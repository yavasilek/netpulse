package ru.yavasilek.netpulse.model

enum class ConnectionStatus {
    CHECKING,
    ONLINE,
    CAPTIVE_PORTAL,
    LIMITED,
    OFFLINE,
}

enum class TransportType {
    VPN,
    WIFI,
    CELLULAR,
    ETHERNET,
    BLUETOOTH,
    USB,
    OTHER,
}

data class ConnectionInfo(
    val status: ConnectionStatus = ConnectionStatus.CHECKING,
    val transports: Set<TransportType> = emptySet(),
    val interfaceName: String? = null,
    val isMetered: Boolean = false,
    val changedAtMillis: Long = System.currentTimeMillis(),
) {
    val isVpn: Boolean
        get() = TransportType.VPN in transports

    val transportLabel: String
        get() {
            val base = when {
                TransportType.WIFI in transports -> "Wi‑Fi"
                TransportType.CELLULAR in transports -> "мобильной сети"
                TransportType.ETHERNET in transports -> "Ethernet"
                TransportType.USB in transports -> "USB"
                TransportType.BLUETOOTH in transports -> "Bluetooth"
                else -> "сети"
            }
            return if (isVpn) "VPN поверх $base" else base.replaceFirstChar(Char::uppercase)
        }
}

data class SpeedSample(
    val receivedBytesPerSecond: Long = 0,
    val transmittedBytesPerSecond: Long = 0,
    val sampledAtMillis: Long = System.currentTimeMillis(),
)

data class IpAddressInfo(
    val address: String,
    val countryCode: String?,
    val countryName: String?,
    val asnOrganization: String?,
)

data class PublicIpInfo(
    val ipv4: IpAddressInfo? = null,
    val ipv6: IpAddressInfo? = null,
    val checkedAtMillis: Long? = null,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
) {
    val primary: IpAddressInfo?
        get() = ipv4 ?: ipv6

    val countryName: String?
        get() = primary?.countryName

    val countryCode: String?
        get() = primary?.countryCode

    val hasPossibleIpv6Leak: Boolean
        get() {
            val v4Country = ipv4?.countryCode
            val v6Country = ipv6?.countryCode
            return v4Country != null && v6Country != null && v4Country != v6Country
        }
}

data class MonitorSnapshot(
    val connection: ConnectionInfo = ConnectionInfo(),
    val speed: SpeedSample = SpeedSample(),
    val speedHistory: List<SpeedSample> = emptyList(),
    val publicIp: PublicIpInfo = PublicIpInfo(),
    val isMonitoring: Boolean = false,
)

enum class NetworkEventType {
    CONNECTION,
    VPN,
    IP,
    WARNING,
}

data class NetworkEvent(
    val id: Long,
    val type: NetworkEventType,
    val title: String,
    val detail: String,
    val occurredAtMillis: Long,
)
