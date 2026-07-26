package ru.yavasilek.netpulse.diagnostics

import org.junit.Assert.assertTrue
import org.junit.Test
import ru.yavasilek.netpulse.model.ConnectionInfo
import ru.yavasilek.netpulse.model.ConnectionStatus
import ru.yavasilek.netpulse.model.IpAddressInfo
import ru.yavasilek.netpulse.model.MonitorSnapshot
import ru.yavasilek.netpulse.model.NetworkQuality
import ru.yavasilek.netpulse.model.NetworkQualityStatus
import ru.yavasilek.netpulse.model.PublicIpInfo
import ru.yavasilek.netpulse.model.TransportType
import ru.yavasilek.netpulse.settings.AppSettings

class DiagnosticReportBuilderTest {
    @Test
    fun reportContainsConnectionQualityAndPrivacyWarning() {
        val report = DiagnosticReportBuilder.build(
            snapshot = MonitorSnapshot(
                connection = ConnectionInfo(
                    status = ConnectionStatus.ONLINE,
                    transports = setOf(TransportType.VPN, TransportType.WIFI),
                ),
                quality = NetworkQuality(
                    status = NetworkQualityStatus.EXCELLENT,
                    latencyMillis = 35,
                    jitterMillis = 4,
                    packetLossPercent = 0,
                ),
                publicIp = PublicIpInfo(
                    ipv4 = IpAddressInfo("203.0.113.1", "FI", "Finland", "Example ASN"),
                ),
            ),
            settings = AppSettings(),
            events = emptyList(),
            versionName = "0.3.0",
            notificationPermissionGranted = true,
            batteryOptimizationIgnored = false,
            generatedAtMillis = 1,
        )

        assertTrue(report.contains("35 мс"))
        assertTrue(report.contains("203.0.113.1"))
        assertTrue(report.contains("содержит публичные IP-адреса"))
    }
}
