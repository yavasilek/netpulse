package ru.yavasilek.netpulse.network

import android.os.Build
import org.json.JSONObject
import ru.yavasilek.netpulse.model.IpAddressInfo
import ru.yavasilek.netpulse.model.PublicIpInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class PublicIpResolver internal constructor(
    private val requester: suspend (url: String, timeoutMillis: Int) -> String =
        ::requestText,
    private val geoParser: (json: String) -> GeoDetails = ::parseGeoDetails,
    private val currentIpParser: (json: String) -> CurrentIpDetails =
        ::parseCurrentIpDetails,
) {
    suspend fun resolve(): PublicIpInfo = supervisorScope {
        val ipv4Deferred = async { resolveIpv4() }
        val ipv6Deferred = async { resolveFamily(IPV6_ENDPOINT, AddressFamily.IPV6) }

        val ipv4 = runCatching { ipv4Deferred.await() }.getOrNull()
        val ipv6 = runCatching { ipv6Deferred.await() }.getOrNull()
        if (ipv4 == null && ipv6 == null) {
            error("Не удалось определить публичный IP")
        }

        PublicIpInfo(
            ipv4 = ipv4,
            ipv6 = ipv6,
            checkedAtMillis = System.currentTimeMillis(),
        )
    }

    private suspend fun resolveIpv4(): IpAddressInfo = runCatching {
        val details = currentIpParser(
            requester(IPV4_DETAILS_ENDPOINT, PRIMARY_REQUEST_TIMEOUT_MILLIS),
        )
        require(
            details.address.length <= 64 &&
                AddressFamily.IPV4.matches(details.address),
        ) {
            "Сервис вернул некорректный IPv4"
        }
        IpAddressInfo(
            address = details.address,
            countryCode = details.countryCode,
            countryName = details.countryCode?.let(::countryName),
            asnOrganization = details.asnOrganization,
        )
    }.getOrElse {
        resolveFamily(IPV4_FALLBACK_ENDPOINT, AddressFamily.IPV4)
    }

    private suspend fun resolveFamily(
        endpoint: String,
        expectedFamily: AddressFamily,
    ): IpAddressInfo {
        val timeoutMillis = when (expectedFamily) {
            AddressFamily.IPV4 -> PRIMARY_REQUEST_TIMEOUT_MILLIS
            AddressFamily.IPV6 -> IPV6_REQUEST_TIMEOUT_MILLIS
        }
        val address = requester(endpoint, timeoutMillis).trim()
        require(address.length <= 64 && expectedFamily.matches(address)) {
            "Сервис вернул некорректный IP"
        }

        val encodedAddress = URLEncoder.encode(
            address,
            StandardCharsets.UTF_8.name(),
        )
        val geoDetails = try {
            val geoJson = requester(
                "$COUNTRY_ENDPOINT$encodedAddress?fields=asn",
                timeoutMillis,
            )
            geoParser(geoJson)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            GeoDetails(
                countryCode = null,
                asnOrganization = null,
            )
        }
        val countryCode = geoDetails.countryCode

        return IpAddressInfo(
            address = address,
            countryCode = countryCode,
            countryName = countryCode?.let(::countryName),
            asnOrganization = geoDetails.asnOrganization,
        )
    }

    private fun countryName(countryCode: String): String {
        val locale = Locale.Builder()
            .setLanguage("ru")
            .setRegion(countryCode.uppercase(Locale.ROOT))
            .build()
        return locale.getDisplayCountry(Locale.forLanguageTag("ru"))
            .ifBlank { countryCode.uppercase(Locale.ROOT) }
    }

    private enum class AddressFamily {
        IPV4,
        IPV6;

        fun matches(value: String): Boolean = when (this) {
            IPV4 -> value.contains('.') && !value.contains(':')
            IPV6 -> value.contains(':')
        }
    }

}

internal data class GeoDetails(
    val countryCode: String?,
    val asnOrganization: String?,
)

internal data class CurrentIpDetails(
    val address: String,
    val countryCode: String?,
    val asnOrganization: String?,
)

private fun parseCurrentIpDetails(jsonText: String): CurrentIpDetails {
    val json = JSONObject(jsonText)
    return CurrentIpDetails(
        address = json.getString("ip"),
        countryCode = json.optString("country").takeIf(String::isNotBlank),
        asnOrganization = json
            .optString("org")
            .takeIf(String::isNotBlank)
            ?.substringAfter(' ', missingDelimiterValue = json.optString("org")),
    )
}

private fun parseGeoDetails(jsonText: String): GeoDetails {
    val json = JSONObject(jsonText)
    return GeoDetails(
        countryCode = json.optString("country").takeIf(String::isNotBlank),
        asnOrganization = json
            .optJSONObject("asn")
            ?.optString("organization")
            ?.takeIf(String::isNotBlank),
    )
}

private suspend fun requestText(
    url: String,
    timeoutMillis: Int,
): String = withContext(Dispatchers.IO) {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = timeoutMillis
        connection.readTimeout = timeoutMillis
        connection.instanceFollowRedirects = true
        connection.useCaches = false
        connection.setRequestProperty("Cache-Control", "no-cache")
        connection.setRequestProperty("Accept", "application/json, text/plain")
        connection.setRequestProperty(
            "User-Agent",
            "NetPulse Android/${Build.VERSION.SDK_INT}",
        )
        val status = connection.responseCode
        if (status !in 200..299) {
            error("HTTP $status")
        }
        connection.inputStream.bufferedReader().use { reader ->
            reader.readText().take(MAX_RESPONSE_LENGTH)
        }
    } finally {
        connection.disconnect()
    }
}

private const val IPV4_DETAILS_ENDPOINT = "https://ipinfo.io/json"
private const val IPV4_FALLBACK_ENDPOINT = "https://api4.ipify.org"
private const val IPV6_ENDPOINT = "https://api6.ipify.org"
private const val COUNTRY_ENDPOINT = "https://api.country.is/"
private const val PRIMARY_REQUEST_TIMEOUT_MILLIS = 3_500
private const val IPV6_REQUEST_TIMEOUT_MILLIS = 800
private const val MAX_RESPONSE_LENGTH = 8_192
