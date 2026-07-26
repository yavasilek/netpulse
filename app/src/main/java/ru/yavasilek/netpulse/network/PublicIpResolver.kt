package ru.yavasilek.netpulse.network

import android.os.Build
import org.json.JSONObject
import ru.yavasilek.netpulse.model.IpAddressInfo
import ru.yavasilek.netpulse.model.PublicIpInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class PublicIpResolver {
    suspend fun resolve(): PublicIpInfo = supervisorScope {
        val ipv4Deferred = async { resolveFamily(IPV4_ENDPOINT, AddressFamily.IPV4) }
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

    private suspend fun resolveFamily(
        endpoint: String,
        expectedFamily: AddressFamily,
    ): IpAddressInfo {
        val address = request(endpoint).trim()
        require(address.length <= 64 && expectedFamily.matches(address)) {
            "Сервис вернул некорректный IP"
        }

        val encodedAddress = URLEncoder.encode(
            address,
            StandardCharsets.UTF_8.name(),
        )
        val geoJson = request(
            "$COUNTRY_ENDPOINT$encodedAddress?fields=asn",
        )
        val json = JSONObject(geoJson)
        val countryCode = json.optString("country").takeIf(String::isNotBlank)
        val organization = json
            .optJSONObject("asn")
            ?.optString("organization")
            ?.takeIf(String::isNotBlank)

        return IpAddressInfo(
            address = address,
            countryCode = countryCode,
            countryName = countryCode?.let(::countryName),
            asnOrganization = organization,
        )
    }

    private suspend fun request(url: String): String = withContext(Dispatchers.IO) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.instanceFollowRedirects = true
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

    private companion object {
        const val IPV4_ENDPOINT = "https://api4.ipify.org"
        const val IPV6_ENDPOINT = "https://api6.ipify.org"
        const val COUNTRY_ENDPOINT = "https://api.country.is/"
        const val TIMEOUT_MILLIS = 6_000
        const val MAX_RESPONSE_LENGTH = 8_192
    }
}
