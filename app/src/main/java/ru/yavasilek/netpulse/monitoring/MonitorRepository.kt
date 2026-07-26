package ru.yavasilek.netpulse.monitoring

import android.content.Context
import android.os.PowerManager
import ru.yavasilek.netpulse.data.NetworkEventStore
import ru.yavasilek.netpulse.model.ConnectionInfo
import ru.yavasilek.netpulse.model.ConnectionStatus
import ru.yavasilek.netpulse.model.MonitorSnapshot
import ru.yavasilek.netpulse.model.NetworkEventType
import ru.yavasilek.netpulse.model.PublicIpInfo
import ru.yavasilek.netpulse.network.ConnectivityObserver
import ru.yavasilek.netpulse.network.PublicIpResolver
import ru.yavasilek.netpulse.network.TrafficSampler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MonitorRepository(
    context: Context,
    private val applicationScope: CoroutineScope,
    private val connectivityObserver: ConnectivityObserver,
    private val trafficSampler: TrafficSampler,
    private val publicIpResolver: PublicIpResolver,
    private val eventStore: NetworkEventStore,
) {
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val _state = MutableStateFlow(MonitorSnapshot())
    private val ipRefreshLock = Any()
    private var samplingJob: Job? = null
    private var periodicIpJob: Job? = null
    private var ipRefreshJob: Job? = null
    @Volatile
    private var ipRefreshSequence = 0L
    private var previousConnection: ConnectionInfo? = null

    val state: StateFlow<MonitorSnapshot> = _state.asStateFlow()

    init {
        connectivityObserver.start()
        applicationScope.launch {
            connectivityObserver.connection.collectLatest(::handleConnection)
        }
    }

    fun startMonitoring() {
        if (samplingJob?.isActive == true) return
        _state.update { it.copy(isMonitoring = true) }
        trafficSampler.reset()

        samplingJob = applicationScope.launch {
            while (true) {
                val sample = trafficSampler.sample(_state.value.connection)
                _state.update { current ->
                    current.copy(
                        speed = sample,
                        speedHistory = (current.speedHistory + sample).takeLast(HISTORY_SIZE),
                        isMonitoring = true,
                    )
                }
                delay(if (powerManager.isInteractive) ACTIVE_SAMPLE_INTERVAL else IDLE_SAMPLE_INTERVAL)
            }
        }

        periodicIpJob = applicationScope.launch {
            while (true) {
                delay(IP_REFRESH_INTERVAL)
                if (_state.value.connection.status.canResolvePublicIp()) {
                    refreshPublicIp()
                }
            }
        }
    }

    fun stopMonitoring() {
        samplingJob?.cancel()
        samplingJob = null
        periodicIpJob?.cancel()
        periodicIpJob = null
        cancelPublicIpRefresh()
        trafficSampler.reset()
        _state.update { current ->
            current.copy(
                isMonitoring = false,
                speed = current.speed.copy(
                    receivedBytesPerSecond = 0,
                    transmittedBytesPerSecond = 0,
                ),
            )
        }
    }

    fun refreshPublicIp() {
        schedulePublicIpRefresh()
    }

    private fun schedulePublicIpRefresh(settleDelayMillis: Long = 0L) {
        if (!_state.value.connection.status.canResolvePublicIp()) {
            cancelPublicIpRefresh()
            _state.update { current ->
                current.copy(
                    publicIp = current.publicIp.copy(
                        isRefreshing = false,
                        errorMessage = "Нет подтверждённого доступа в интернет",
                    ),
                )
            }
            return
        }

        _state.update { current ->
            current.copy(
                publicIp = current.publicIp.copy(
                    isRefreshing = true,
                    errorMessage = null,
                ),
            )
        }

        synchronized(ipRefreshLock) {
            ipRefreshSequence += 1
            val requestSequence = ipRefreshSequence
            ipRefreshJob?.cancel()
            ipRefreshJob = applicationScope.launch {
                if (settleDelayMillis > 0) delay(settleDelayMillis)
                if (
                    requestSequence != ipRefreshSequence ||
                    !_state.value.connection.status.canResolvePublicIp()
                ) {
                    return@launch
                }

                val before = _state.value.publicIp
                var resolved = runCatching { publicIpResolver.resolve() }
                var retryAttempt = 0
                while (
                    resolved.isFailure &&
                    retryAttempt < IP_REFRESH_RETRY_COUNT &&
                    requestSequence == ipRefreshSequence &&
                    _state.value.connection.status.canResolvePublicIp()
                ) {
                    retryAttempt += 1
                    delay(IP_REFRESH_RETRY_DELAY * retryAttempt)
                    if (requestSequence != ipRefreshSequence) return@launch
                    resolved = runCatching { publicIpResolver.resolve() }
                }
                if (requestSequence != ipRefreshSequence) return@launch

                resolved.onSuccess { publicIp ->
                    val oldAddress = before.primary?.address
                    val newAddress = publicIp.primary?.address
                    _state.update { it.copy(publicIp = publicIp) }
                    if (oldAddress != null && newAddress != null && oldAddress != newAddress) {
                        eventStore.add(
                            type = NetworkEventType.IP,
                            title = "Публичный IP изменился",
                            detail = "$oldAddress → $newAddress",
                        )
                    } else if (oldAddress == null && newAddress != null) {
                        eventStore.add(
                            type = NetworkEventType.IP,
                            title = "Публичный IP определён",
                            detail = newAddress,
                        )
                    }
                    if (publicIp.hasPossibleIpv6Leak) {
                        eventStore.add(
                            type = NetworkEventType.WARNING,
                            title = "Возможна IPv6-утечка",
                            detail = "IPv4 и IPv6 выходят через разные страны",
                        )
                    }
                }.onFailure {
                    _state.update { current ->
                        current.copy(
                            publicIp = current.publicIp.copy(
                                isRefreshing = false,
                                errorMessage = "Сервис публичного IP временно недоступен",
                            ),
                        )
                    }
                }

                synchronized(ipRefreshLock) {
                    if (requestSequence == ipRefreshSequence) {
                        ipRefreshJob = null
                    }
                }
            }
        }
    }

    private fun cancelPublicIpRefresh() {
        synchronized(ipRefreshLock) {
            ipRefreshSequence += 1
            ipRefreshJob?.cancel()
            ipRefreshJob = null
        }
        _state.update { current ->
            current.copy(
                publicIp = current.publicIp.copy(isRefreshing = false),
            )
        }
    }

    private fun handleConnection(connection: ConnectionInfo) {
        val previous = previousConnection
        previousConnection = connection
        _state.update { it.copy(connection = connection) }

        if (previous != null && previous.status != connection.status) {
            eventStore.add(
                type = NetworkEventType.CONNECTION,
                title = connection.status.eventTitle(),
                detail = connection.transportLabel,
            )
        }
        if (previous != null && previous.isVpn != connection.isVpn) {
            eventStore.add(
                type = NetworkEventType.VPN,
                title = if (connection.isVpn) "VPN подключён" else "VPN отключён",
                detail = connection.transportLabel,
            )
        }

        val networkChanged = previous == null ||
            previous.interfaceName != connection.interfaceName ||
            previous.transports != connection.transports
        val becameOnline =
            previous?.status != ConnectionStatus.ONLINE &&
                connection.status == ConnectionStatus.ONLINE
        val becameResolvable =
            previous?.status?.canResolvePublicIp() != true &&
                connection.status.canResolvePublicIp()
        if (networkChanged || becameResolvable || becameOnline) {
            trafficSampler.reset()
            if (connection.status.canResolvePublicIp()) {
                schedulePublicIpRefresh(NETWORK_SETTLE_DELAY)
            } else {
                cancelPublicIpRefresh()
            }
        } else if (!connection.status.canResolvePublicIp()) {
            cancelPublicIpRefresh()
        }
    }

    private fun ConnectionStatus.eventTitle(): String = when (this) {
        ConnectionStatus.CHECKING -> "Проверка соединения"
        ConnectionStatus.ONLINE -> "Интернет работает"
        ConnectionStatus.CAPTIVE_PORTAL -> "Требуется вход в сеть"
        ConnectionStatus.LIMITED -> "Интернет не подтверждён"
        ConnectionStatus.OFFLINE -> "Соединение потеряно"
    }

    private fun ConnectionStatus.canResolvePublicIp(): Boolean =
        this == ConnectionStatus.ONLINE || this == ConnectionStatus.LIMITED

    private companion object {
        const val HISTORY_SIZE = 60
        const val ACTIVE_SAMPLE_INTERVAL = 1_000L
        const val IDLE_SAMPLE_INTERVAL = 5_000L
        const val NETWORK_SETTLE_DELAY = 300L
        const val IP_REFRESH_RETRY_COUNT = 1
        const val IP_REFRESH_RETRY_DELAY = 700L
        const val IP_REFRESH_INTERVAL = 10 * 60 * 1_000L
    }
}
