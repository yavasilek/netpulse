package ru.yavasilek.netpulse.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import ru.yavasilek.netpulse.model.NetworkEvent
import ru.yavasilek.netpulse.model.NetworkEventType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

class NetworkEventStore(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val idCounter = AtomicLong(System.currentTimeMillis())
    private val _events = MutableStateFlow(load())

    val events: StateFlow<List<NetworkEvent>> = _events.asStateFlow()

    @Synchronized
    fun add(
        type: NetworkEventType,
        title: String,
        detail: String,
        occurredAtMillis: Long = System.currentTimeMillis(),
    ) {
        val event = NetworkEvent(
            id = idCounter.incrementAndGet(),
            type = type,
            title = title,
            detail = detail,
            occurredAtMillis = occurredAtMillis,
        )
        val updated = (listOf(event) + _events.value).take(MAX_EVENTS)
        _events.value = updated
        save(updated)
    }

    @Synchronized
    fun clear() {
        _events.value = emptyList()
        preferences.edit { remove(KEY_EVENTS) }
    }

    private fun load(): List<NetworkEvent> = runCatching {
        val json = preferences.getString(KEY_EVENTS, null) ?: return emptyList()
        val array = JSONArray(json)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    NetworkEvent(
                        id = item.getLong("id"),
                        type = enumValueOf(item.getString("type")),
                        title = item.getString("title"),
                        detail = item.getString("detail"),
                        occurredAtMillis = item.getLong("occurredAtMillis"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun save(events: List<NetworkEvent>) {
        val array = JSONArray()
        events.forEach { event ->
            array.put(
                JSONObject()
                    .put("id", event.id)
                    .put("type", event.type.name)
                    .put("title", event.title)
                    .put("detail", event.detail)
                    .put("occurredAtMillis", event.occurredAtMillis),
            )
        }
        preferences.edit { putString(KEY_EVENTS, array.toString()) }
    }

    private companion object {
        const val PREFERENCES_NAME = "network_events"
        const val KEY_EVENTS = "events"
        const val MAX_EVENTS = 100
    }
}
