package ru.yavasilek.netpulse.settings

enum class SpeedUnit {
    BITS_PER_SECOND,
    BYTES_PER_SECOND,
}

enum class StatusIconMode {
    DOWNLOAD,
    UPLOAD,
    DOMINANT,
    STATIC,
}

data class AppSettings(
    val monitoringEnabled: Boolean = true,
    val startOnBoot: Boolean = true,
    val speedUnit: SpeedUnit = SpeedUnit.BITS_PER_SECOND,
    val statusIconMode: StatusIconMode = StatusIconMode.DOWNLOAD,
    val warnWhenVpnDisconnects: Boolean = true,
    val warnWhenIpChanges: Boolean = false,
    val automaticUpdateChecks: Boolean = true,
    val dynamicColor: Boolean = true,
)
