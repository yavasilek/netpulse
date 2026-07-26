package ru.yavasilek.netpulse.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.yavasilek.netpulse.model.ConnectionInfo
import ru.yavasilek.netpulse.model.ConnectionStatus
import ru.yavasilek.netpulse.model.IpAddressInfo
import ru.yavasilek.netpulse.model.MonitorSnapshot
import ru.yavasilek.netpulse.model.PublicIpInfo
import ru.yavasilek.netpulse.model.TransportType
import ru.yavasilek.netpulse.settings.AppSettings
import ru.yavasilek.netpulse.settings.TrustedExitProfile

class ProtectionEvaluatorTest {
    @Test
    fun protectsWhenVpnAndPublicIpAreHealthy() {
        val result = ProtectionEvaluator.evaluate(healthySnapshot(), AppSettings())

        assertEquals(ProtectionStatus.PROTECTED, result.status)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun reportsDangerWhenRequiredVpnIsMissing() {
        val snapshot = healthySnapshot().copy(
            connection = onlineConnection(setOf(TransportType.WIFI)),
        )

        val result = ProtectionEvaluator.evaluate(snapshot, AppSettings())

        assertEquals(ProtectionStatus.DANGER, result.status)
        assertTrue(ProtectionIssue.VPN_REQUIRED in result.issues)
    }

    @Test
    fun allowsConnectionWithoutVpnWhenProfileDoesNotRequireIt() {
        val snapshot = healthySnapshot().copy(
            connection = onlineConnection(setOf(TransportType.WIFI)),
        )

        val result = ProtectionEvaluator.evaluate(
            snapshot,
            AppSettings(requireVpnForProtection = false),
        )

        assertEquals(ProtectionStatus.PROTECTED, result.status)
    }

    @Test
    fun waitsForFreshIpBeforeComparingTrustedExit() {
        val result = ProtectionEvaluator.evaluate(
            healthySnapshot().copy(
                publicIp = healthyPublicIp().copy(isRefreshing = true),
            ),
            settingsWithTrustedExit(),
        )

        assertEquals(ProtectionStatus.ATTENTION, result.status)
        assertEquals(setOf(ProtectionIssue.IP_REFRESHING), result.issues)
    }

    @Test
    fun warnsWhenTrustedCountryChanges() {
        val result = ProtectionEvaluator.evaluate(
            healthySnapshot(),
            settingsWithTrustedExit(countryCode = "FI"),
        )

        assertEquals(ProtectionStatus.ATTENTION, result.status)
        assertTrue(ProtectionIssue.TRUSTED_COUNTRY_MISMATCH in result.issues)
    }

    @Test
    fun warnsWhenTrustedProviderChanges() {
        val result = ProtectionEvaluator.evaluate(
            healthySnapshot(),
            settingsWithTrustedExit(provider = "Another VPN"),
        )

        assertEquals(ProtectionStatus.ATTENTION, result.status)
        assertTrue(ProtectionIssue.TRUSTED_PROVIDER_MISMATCH in result.issues)
    }

    @Test
    fun ignoresProviderCaseAndExtraWhitespace() {
        val result = ProtectionEvaluator.evaluate(
            healthySnapshot(),
            settingsWithTrustedExit(provider = "  EXAMPLE   vpn  "),
        )

        assertEquals(ProtectionStatus.PROTECTED, result.status)
    }

    @Test
    fun reportsDangerForCaptivePortal() {
        val result = ProtectionEvaluator.evaluate(
            healthySnapshot().copy(
                connection = ConnectionInfo(
                    status = ConnectionStatus.CAPTIVE_PORTAL,
                    transports = setOf(TransportType.WIFI),
                ),
            ),
            AppSettings(requireVpnForProtection = false),
        )

        assertEquals(ProtectionStatus.DANGER, result.status)
        assertTrue(ProtectionIssue.CAPTIVE_PORTAL in result.issues)
    }

    @Test
    fun reportsDangerForDifferentIpv4AndIpv6Countries() {
        val result = ProtectionEvaluator.evaluate(
            healthySnapshot().copy(
                publicIp = healthyPublicIp().copy(
                    ipv6 = IpAddressInfo(
                        address = "2001:db8::42",
                        countryCode = "DE",
                        countryName = "Германия",
                        asnOrganization = "Example VPN",
                    ),
                ),
            ),
            AppSettings(),
        )

        assertEquals(ProtectionStatus.DANGER, result.status)
        assertTrue(ProtectionIssue.IPV6_COUNTRY_MISMATCH in result.issues)
    }

    private fun healthySnapshot(): MonitorSnapshot = MonitorSnapshot(
        connection = onlineConnection(
            setOf(TransportType.VPN, TransportType.WIFI),
        ),
        publicIp = healthyPublicIp(),
    )

    private fun onlineConnection(transports: Set<TransportType>) = ConnectionInfo(
        status = ConnectionStatus.ONLINE,
        transports = transports,
    )

    private fun healthyPublicIp() = PublicIpInfo(
        ipv4 = IpAddressInfo(
            address = "203.0.113.42",
            countryCode = "EE",
            countryName = "Эстония",
            asnOrganization = "Example VPN",
        ),
        checkedAtMillis = 1L,
    )

    private fun settingsWithTrustedExit(
        countryCode: String = "EE",
        provider: String = "Example VPN",
    ) = AppSettings(
        trustedExitProfile = TrustedExitProfile(
            countryCode = countryCode,
            countryName = "Эстония",
            asnOrganization = provider,
        ),
    )
}
