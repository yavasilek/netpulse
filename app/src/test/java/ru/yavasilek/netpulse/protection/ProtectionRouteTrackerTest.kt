package ru.yavasilek.netpulse.protection

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.yavasilek.netpulse.model.ConnectionInfo
import ru.yavasilek.netpulse.model.ConnectionStatus
import ru.yavasilek.netpulse.model.IpAddressInfo
import ru.yavasilek.netpulse.model.MonitorSnapshot
import ru.yavasilek.netpulse.model.NetworkEventType
import ru.yavasilek.netpulse.model.PublicIpInfo
import ru.yavasilek.netpulse.model.TransportType
import ru.yavasilek.netpulse.monitoring.PublicIpRefreshEventFactory
import ru.yavasilek.netpulse.settings.AppSettings
import ru.yavasilek.netpulse.settings.TrustedExitProfile

class ProtectionRouteTrackerTest {
    @Test
    fun failedIpRefreshDoesNotRearmMismatchWarning() {
        val tracker = ProtectionRouteTracker()
        val settings = trustedSettings()
        val safe = snapshot(countryCode = "EE")
        val mismatch = snapshot(countryCode = "DE")
        val failed = mismatch.copy(
            publicIp = mismatch.publicIp.copy(errorMessage = "lookup failed"),
        )

        val warnings = listOfNotNull(
            tracker.onSnapshot(safe, settings),
            tracker.onSnapshot(mismatch, settings),
            tracker.onSnapshot(failed, settings),
            tracker.onSnapshot(mismatch, settings),
        )

        assertEquals(1, warnings.count { it.type == NetworkEventType.WARNING })
    }

    @Test
    fun safeToIpv6MismatchCreatesOneWarningAcrossAllEventPaths() {
        val tracker = ProtectionRouteTracker()
        val settings = AppSettings()
        val safe = snapshot(countryCode = "EE")
        val mismatch = safe.copy(
            publicIp = safe.publicIp.copy(
                ipv6 = ip(address = "2001:db8::42", countryCode = "DE"),
            ),
        )
        tracker.onSnapshot(safe, settings)

        val events = PublicIpRefreshEventFactory.create(safe.publicIp, mismatch.publicIp) +
            listOfNotNull(tracker.onSnapshot(mismatch, settings))

        assertEquals(1, events.count { it.type == NetworkEventType.WARNING })
    }

    private fun snapshot(countryCode: String) = MonitorSnapshot(
        connection = ConnectionInfo(
            status = ConnectionStatus.ONLINE,
            transports = setOf(TransportType.VPN, TransportType.WIFI),
        ),
        publicIp = PublicIpInfo(
            ipv4 = ip(address = "203.0.113.42", countryCode = countryCode),
            checkedAtMillis = 1L,
        ),
    )

    private fun ip(
        address: String,
        countryCode: String,
    ) = IpAddressInfo(
        address = address,
        countryCode = countryCode,
        countryName = countryCode,
        asnOrganization = "Example VPN",
    )

    private fun trustedSettings() = AppSettings(
        trustedExitProfile = TrustedExitProfile(
            countryCode = "EE",
            countryName = "Эстония",
            asnOrganization = "Example VPN",
        ),
    )
}
