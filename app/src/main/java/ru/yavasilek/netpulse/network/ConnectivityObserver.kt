package ru.yavasilek.netpulse.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import ru.yavasilek.netpulse.model.ConnectionInfo
import ru.yavasilek.netpulse.model.ConnectionStatus
import ru.yavasilek.netpulse.model.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConnectivityObserver(
    context: Context,
) {
    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)
    private val _connection = MutableStateFlow(ConnectionInfo())
    private var started = false

    val connection: StateFlow<ConnectionInfo> = _connection.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            publish(network)
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            publish(
                network = network,
                capabilities = networkCapabilities,
            )
        }

        override fun onLinkPropertiesChanged(
            network: Network,
            linkProperties: LinkProperties,
        ) {
            publish(
                network = network,
                linkProperties = linkProperties,
            )
        }

        override fun onLost(network: Network) {
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork == null || activeNetwork == network) {
                _connection.value = ConnectionInfo(
                    status = ConnectionStatus.OFFLINE,
                    changedAtMillis = System.currentTimeMillis(),
                )
            } else {
                publish(activeNetwork)
            }
        }
    }

    @Synchronized
    fun start() {
        if (started) return
        started = true
        connectivityManager.registerDefaultNetworkCallback(callback)
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork == null) {
            _connection.value = ConnectionInfo(status = ConnectionStatus.OFFLINE)
        } else {
            publish(activeNetwork)
        }
    }

    @Synchronized
    fun stop() {
        if (!started) return
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        started = false
    }

    private fun publish(
        network: Network,
        capabilities: NetworkCapabilities? =
            connectivityManager.getNetworkCapabilities(network),
        linkProperties: LinkProperties? =
            connectivityManager.getLinkProperties(network),
    ) {
        if (network != connectivityManager.activeNetwork) return

        val status = when {
            capabilities == null -> ConnectionStatus.CHECKING
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) ->
                ConnectionStatus.CAPTIVE_PORTAL
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ->
                ConnectionStatus.ONLINE
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ->
                ConnectionStatus.LIMITED
            else -> ConnectionStatus.OFFLINE
        }

        val transports = buildSet {
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                add(TransportType.VPN)
            }
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                add(TransportType.WIFI)
            }
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) {
                add(TransportType.CELLULAR)
            }
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) {
                add(TransportType.ETHERNET)
            }
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) == true) {
                add(TransportType.BLUETOOTH)
            }
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_USB) == true
            ) {
                add(TransportType.USB)
            }
            if (isEmpty() && capabilities != null) {
                add(TransportType.OTHER)
            }
        }

        val previous = _connection.value
        val next = ConnectionInfo(
            status = status,
            transports = transports,
            interfaceName = linkProperties?.interfaceName,
            networkIdentity = network.networkHandle,
            isMetered = connectivityManager.isActiveNetworkMetered,
            changedAtMillis = if (
                previous.status == status &&
                previous.transports == transports &&
                previous.interfaceName == linkProperties?.interfaceName &&
                previous.networkIdentity == network.networkHandle
            ) {
                previous.changedAtMillis
            } else {
                System.currentTimeMillis()
            },
        )
        _connection.value = next
    }
}
