package ru.yavasilek.netpulse.diagnostics

import ru.yavasilek.netpulse.model.ConnectionStatus
import ru.yavasilek.netpulse.model.MonitorSnapshot
import ru.yavasilek.netpulse.model.NetworkEvent
import ru.yavasilek.netpulse.model.NetworkQualityStatus
import ru.yavasilek.netpulse.protection.ProtectionEvaluator
import ru.yavasilek.netpulse.settings.AppSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticReportBuilder {
    fun build(
        snapshot: MonitorSnapshot,
        settings: AppSettings,
        events: List<NetworkEvent>,
        versionName: String,
        notificationPermissionGranted: Boolean,
        batteryOptimizationIgnored: Boolean,
        generatedAtMillis: Long = System.currentTimeMillis(),
    ): String {
        val protection = ProtectionEvaluator.evaluate(snapshot, settings)
        return buildString {
            appendLine("Диагностика NetPulse $versionName")
            appendLine("Создано: ${formatDate(generatedAtMillis)}")
            appendLine()
            appendLine("Соединение")
            appendLine("• Статус: ${snapshot.connection.status.label()}")
            appendLine("• Транспорт: ${snapshot.connection.transportLabel}")
            appendLine("• VPN: ${if (snapshot.connection.isVpn) "активен" else "не обнаружен"}")
            appendLine("• Лимитный трафик: ${yesNo(snapshot.connection.isMetered)}")
            appendLine()
            appendLine("Качество")
            appendLine("• Оценка: ${snapshot.quality.status.label()}")
            appendLine("• Задержка: ${snapshot.quality.latencyMillis.value("мс")}")
            appendLine("• Джиттер: ${snapshot.quality.jitterMillis.value("мс")}")
            appendLine("• Потери: ${snapshot.quality.packetLossPercent.value("%")}")
            appendLine()
            appendLine("Выход в интернет")
            appendLine("• IPv4: ${snapshot.publicIp.ipv4?.address ?: "не определён"}")
            appendLine("• IPv6: ${snapshot.publicIp.ipv6?.address ?: "не определён"}")
            appendLine("• Страна: ${snapshot.publicIp.countryName ?: "не определена"}")
            appendLine(
                "• Оператор: ${snapshot.publicIp.primary?.asnOrganization ?: "не определён"}",
            )
            appendLine()
            appendLine("Защита")
            appendLine("• Состояние: ${protection.status.name}")
            appendLine(
                "• Причины: ${
                    protection.issues.joinToString().ifBlank { "отклонений нет" }
                }",
            )
            appendLine()
            appendLine("Фоновая работа")
            appendLine("• Мониторинг: ${if (snapshot.isMonitoring) "работает" else "остановлен"}")
            appendLine("• Уведомления: ${if (notificationPermissionGranted) "разрешены" else "запрещены"}")
            appendLine("• Автозапуск: ${if (settings.startOnBoot) "включён" else "выключен"}")
            appendLine(
                "• Батарея: ${
                    if (batteryOptimizationIgnored) "ограничения сняты" else "управляет Android"
                }",
            )
            appendLine("• Автообновление: ${if (settings.automaticUpdateChecks) "включено" else "выключено"}")
            if (events.isNotEmpty()) {
                appendLine()
                appendLine("Последние события")
                events.take(MAX_REPORT_EVENTS).forEach { event ->
                    appendLine(
                        "• ${formatDate(event.occurredAtMillis)} · ${event.title}: ${event.detail}",
                    )
                }
            }
            appendLine()
            append("Отчёт создан локально. Он содержит публичные IP-адреса.")
        }
    }

    fun buildEventLog(
        events: List<NetworkEvent>,
        versionName: String,
        generatedAtMillis: Long = System.currentTimeMillis(),
    ): String = buildString {
        appendLine("Журнал NetPulse $versionName")
        appendLine("Экспорт: ${formatDate(generatedAtMillis)}")
        appendLine("Событий: ${events.size}")
        appendLine()
        events.forEach { event ->
            appendLine(
                "${formatDate(event.occurredAtMillis)}\t${event.type}\t" +
                    "${event.title}\t${event.detail}",
            )
        }
    }

    private fun ConnectionStatus.label(): String = when (this) {
        ConnectionStatus.CHECKING -> "проверяется"
        ConnectionStatus.ONLINE -> "интернет работает"
        ConnectionStatus.CAPTIVE_PORTAL -> "требуется вход в сеть"
        ConnectionStatus.LIMITED -> "интернет не подтверждён"
        ConnectionStatus.OFFLINE -> "нет подключения"
    }

    private fun NetworkQualityStatus.label(): String = when (this) {
        NetworkQualityStatus.CHECKING -> "проверяется"
        NetworkQualityStatus.EXCELLENT -> "отлично"
        NetworkQualityStatus.GOOD -> "хорошо"
        NetworkQualityStatus.UNSTABLE -> "нестабильно"
        NetworkQualityStatus.UNAVAILABLE -> "недоступно"
    }

    private fun Int?.value(suffix: String): String =
        this?.let { "$it $suffix" } ?: "нет данных"

    private fun yesNo(value: Boolean): String = if (value) "да" else "нет"

    private fun formatDate(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault()).format(Date(millis))

    private const val MAX_REPORT_EVENTS = 20
}
