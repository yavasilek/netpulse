package ru.yavasilek.netpulse.network

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicIpResolverTest {
    @Test
    fun keepsIpv4WhenIpv6IsUnavailable() = runBlocking {
        val timeouts = ConcurrentHashMap<String, Int>()
        val resolver = PublicIpResolver(
            requester = { url, timeoutMillis ->
                timeouts[url] = timeoutMillis
                when {
                    "ipinfo.io" in url -> "{}"
                    "api4.ipify.org" in url -> "203.0.113.42"
                    "api6.ipify.org" in url -> throw IOException("IPv6 unavailable")
                    "api.country.is" in url -> "{}"
                    else -> error("Unexpected URL: $url")
                }
            },
            geoParser = { GeoDetails("NL", "Example VPN") },
            currentIpParser = {
                CurrentIpDetails(
                    address = "203.0.113.42",
                    countryCode = "NL",
                    asnOrganization = "Example VPN",
                )
            },
        )

        val result = resolver.resolve()

        assertEquals("203.0.113.42", result.ipv4?.address)
        assertEquals("NL", result.ipv4?.countryCode)
        assertEquals("Example VPN", result.ipv4?.asnOrganization)
        assertNull(result.ipv6)
        assertTrue(timeouts.keys.none { "api4.ipify.org" in it })
        assertTrue(timeouts.keys.none { "api.country.is" in it })
        assertTrue(
            requireNotNull(timeouts.entries.first { "api6.ipify.org" in it.key }.value) <= 1_000,
        )
        assertTrue(
            requireNotNull(timeouts.entries.first { "ipinfo.io" in it.key }.value) >
                requireNotNull(timeouts.entries.first { "api6.ipify.org" in it.key }.value),
        )
    }

    @Test
    fun fallsBackToIpv6WhenIpv4IsUnavailable() = runBlocking {
        val resolver = PublicIpResolver(
            requester = { url, _ ->
                when {
                    "ipinfo.io" in url -> throw IOException("Primary IPv4 unavailable")
                    "api4.ipify.org" in url -> throw IOException("IPv4 unavailable")
                    "api6.ipify.org" in url -> "2001:db8::42"
                    "api.country.is" in url -> "{}"
                    else -> error("Unexpected URL: $url")
                }
            },
            geoParser = { GeoDetails("DE", "Example IPv6") },
        )

        val result = resolver.resolve()

        assertNull(result.ipv4)
        assertEquals("2001:db8::42", result.ipv6?.address)
        assertEquals("DE", result.countryCode)
    }
}
