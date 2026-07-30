package ru.yavasilek.netpulse.protection

import kotlinx.coroutines.runBlocking
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

class TrustCurrentExitUseCaseTest {
    @Test
    fun rejectsWhenVpnDisconnectsBeforeCallback() = runBlocking {
        val fixture = Fixture()
        assertTrue(
            TrustCurrentExitPolicy.evaluate(fixture.snapshot, fixture.settings) is
                TrustCurrentExitDecision.Ready,
        )
        fixture.snapshot = healthySnapshot().copy(
            connection = onlineConnection(setOf(TransportType.WIFI)),
        )

        val result = fixture.useCase.save()

        assertEquals(
            TrustCurrentExitRejection.VPN_REQUIRED,
            (result as TrustCurrentExitResult.Rejected).reason,
        )
        assertTrue(fixture.savedProfiles.isEmpty())
    }

    @Test
    fun rejectsWhenRefreshStartsBeforeCallback() = runBlocking {
        val fixture = Fixture()
        assertTrue(
            TrustCurrentExitPolicy.evaluate(fixture.snapshot, fixture.settings) is
                TrustCurrentExitDecision.Ready,
        )
        fixture.snapshot = healthySnapshot().copy(
            publicIp = healthyPublicIp().copy(isRefreshing = true),
        )

        val result = fixture.useCase.save()

        assertEquals(
            TrustCurrentExitRejection.IP_REFRESHING,
            (result as TrustCurrentExitResult.Rejected).reason,
        )
        assertTrue(fixture.savedProfiles.isEmpty())
    }

    @Test
    fun rejectsWhenLookupFailsBeforeCallback() = runBlocking {
        val fixture = Fixture()
        assertTrue(
            TrustCurrentExitPolicy.evaluate(fixture.snapshot, fixture.settings) is
                TrustCurrentExitDecision.Ready,
        )
        fixture.snapshot = healthySnapshot().copy(
            publicIp = healthyPublicIp().copy(errorMessage = "lookup failed"),
        )

        val result = fixture.useCase.save()

        assertEquals(
            TrustCurrentExitRejection.IP_LOOKUP_FAILED,
            (result as TrustCurrentExitResult.Rejected).reason,
        )
        assertTrue(fixture.savedProfiles.isEmpty())
    }

    @Test
    fun savesCurrentExitWhenLatestStateIsValid() = runBlocking {
        val fixture = Fixture()

        val result = fixture.useCase.save()

        assertTrue(result is TrustCurrentExitResult.Saved)
        assertEquals(
            listOf(
                TrustedExitProfile(
                    countryCode = "EE",
                    countryName = "Эстония",
                    asnOrganization = "Example VPN",
                ),
            ),
            fixture.savedProfiles,
        )
    }

    private class Fixture {
        var snapshot = healthySnapshot()
        var settings = AppSettings()
        val savedProfiles = mutableListOf<TrustedExitProfile>()
        val useCase = TrustCurrentExitUseCase(
            currentSnapshot = { snapshot },
            currentSettings = { settings },
            saveProfile = savedProfiles::add,
        )
    }

    companion object {
        private fun healthySnapshot() = MonitorSnapshot(
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
    }
}
