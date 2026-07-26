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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val refreshMutex = Mutex()
    private var samplingJob: Job? = null
    private var periodicIpJob: Job? = null
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
        _state.value = _state.value.copy(isMonitoring = true)
        trafficSampler.reset()

        samplingJob = applicationScope.launch {
            while (true) {
                val current = _state.value
                val sample = trafficSampler.sample(current.connection)
                _state.value = current.copy(
                    speed = sample,
                    speedHistory = (current.speedHistory + sample).takeLast(HISTORY_SIZE),
                    isMonitoring = true,
                )
                delay(if (powerManager.isInteractive) ACTIVE_SAMPLE_INTERVAL else IDLE_SAMPLE_INTERVAL)
            }
        }

        periodicIpJob = applicationScope.launch {
            while (true) {
                delay(IP_REFRESH_INTERVAL)
                if (_state.value.connection.status == ConnectionStatus.ONLINE) {
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
        trafficSampler.reset()
        _state.value = _state.value.copy(
            isMonitoring = false,
            speed = _state.value.speed.copy(
                receivedBytesPerSecond = 0,
                transmittedBytesPerSecond = 0,
            ),
        )
    }

    fun refreshPublicIp() {
        applicationScope.launch {
            refreshMutex.withLock {
                val before = _state.value.publicIp
                if (_state.value.connection.status != ConnectionStatus.ONLINE) {
                    _state.value = _state.value.copy(
                        publicIp = before.copy(
                            isRefreshing = false,
                            errorMessage = "Нет подтверждённого доступа в интернет",
                        ),
                    )
                    return@withLock
                }

                _state.value = _state.value.copy(
                    publicIp = before.copy(isRefreshing = true, errorMessage = null),
                )
                val resolved = runCatching { publicIpResolver.resolve() }
                resolved.onSuccess { publicIp ->
                    val oldAddress = before.primary?.address
                    val newAddress = publicIp.primary?.address
                    _state.value = _state.value.copy(publicIp = publicIp)
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
                }.onFailure { error ->
                    _state.value = _state.value.copy(
                        publicIp = before.copy(
                            isRefreshing = false,
                            errorMessage = "Сервис публичного IP временно недоступен",
                        ),
                    )
                }
            }
        }
    }

    private fun handleConnection(connection: ConnectionInfo) {
        val previous = previousConnection
        previousConnection = connection
        val current = _state.value
        _state.value = current.copy(connection = connection)

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
        if (networkChanged) {
            trafficSampler.reset()
            if (connection.status == ConnectionStatus.ONLINE) {
                refreshPublicIp()
            }
        }
    }

    private fun ConnectionStatus.eventTitle(): String = when (this) {
        ConnectionStatus.CHECKING -> "Проверка соединения"
        ConnectionStatus.ONLINE -> "Интернет работает"
        ConnectionStatus.CAPTIVE_PORTAL -> "Требуется вход в сеть"
        ConnectionStatus.LIMITED -> "Интернет не подтверждён"
        ConnectionStatus.OFFLINE -> "Соединение потеряно"
    }

    private companion object {
        const val HISTORY_SIZE = 60
        const val ACTIVE_SAMPLE_INTERVAL = 1_000L
        const val IDLE_SAMPLE_INTERVAL = 5_000L
        const val IP_REFRESH_INTERVAL = 10 * 60 * 1_000L
    }
}
