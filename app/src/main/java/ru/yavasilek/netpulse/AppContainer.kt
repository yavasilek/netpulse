package ru.yavasilek.netpulse

import android.content.Context
import ru.yavasilek.netpulse.data.NetworkEventStore
import ru.yavasilek.netpulse.monitoring.MonitorRepository
import ru.yavasilek.netpulse.network.ConnectivityObserver
import ru.yavasilek.netpulse.network.PublicIpResolver
import ru.yavasilek.netpulse.network.TrafficSampler
import ru.yavasilek.netpulse.settings.SettingsRepository
import ru.yavasilek.netpulse.update.GitHubReleaseClient
import ru.yavasilek.netpulse.update.UpdateManager
import kotlinx.coroutines.CoroutineScope

class AppContainer(
    context: Context,
    applicationScope: CoroutineScope,
) {
    val settingsRepository = SettingsRepository(context)
    val eventStore = NetworkEventStore(context)
    val monitorRepository = MonitorRepository(
        context = context,
        applicationScope = applicationScope,
        connectivityObserver = ConnectivityObserver(context),
        trafficSampler = TrafficSampler(),
        publicIpResolver = PublicIpResolver(),
        eventStore = eventStore,
    )
    val updateManager = UpdateManager(
        context = context,
        releaseClient = GitHubReleaseClient(),
    )
}
