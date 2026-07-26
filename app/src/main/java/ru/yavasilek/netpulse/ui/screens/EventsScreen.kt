package ru.yavasilek.netpulse.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.yavasilek.netpulse.model.NetworkEvent
import ru.yavasilek.netpulse.model.NetworkEventType
import java.util.Date

private enum class EventFilter(
    val label: String,
    val type: NetworkEventType?,
) {
    ALL("Все", null),
    CONNECTION("Сеть", NetworkEventType.CONNECTION),
    VPN("VPN", NetworkEventType.VPN),
    IP("IP", NetworkEventType.IP),
    QUALITY("Качество", NetworkEventType.QUALITY),
    WARNING("Риски", NetworkEventType.WARNING),
}

@Composable
fun EventsScreen(
    events: List<NetworkEvent>,
    onClear: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var selectedFilterName by rememberSaveable { mutableStateOf(EventFilter.ALL.name) }
    val selectedFilter = EventFilter.entries
        .firstOrNull { it.name == selectedFilterName }
        ?: EventFilter.ALL
    val filteredEvents = events.filter { event ->
        (selectedFilter.type == null || event.type == selectedFilter.type) &&
            (
                search.isBlank() ||
                    event.title.contains(search, ignoreCase = true) ||
                    event.detail.contains(search, ignoreCase = true)
                )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Последние изменения",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row {
                IconButton(onClick = onShare, enabled = events.isNotEmpty()) {
                    Icon(Icons.Outlined.Share, contentDescription = "Поделиться журналом")
                }
                IconButton(onClick = onClear, enabled = events.isNotEmpty()) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = "Очистить события")
                }
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Outlined.Search, contentDescription = null)
            },
            label = { Text("Поиск по событиям") },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EventFilter.entries.forEach { filter ->
                FilterChip(
                    selected = filter == selectedFilter,
                    onClick = { selectedFilterName = filter.name },
                    label = { Text(filter.label) },
                )
            }
        }

        if (filteredEvents.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.History, contentDescription = null)
                    Text(
                        if (events.isEmpty()) {
                            "Изменений сети пока нет"
                        } else {
                            "По этому фильтру событий нет"
                        },
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filteredEvents, key = NetworkEvent::id) { event ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(event.title, fontWeight = FontWeight.Medium)
                                Text(
                                    DateFormat.getTimeFormat(
                                        androidx.compose.ui.platform.LocalContext.current,
                                    ).format(Date(event.occurredAtMillis)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                event.detail,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
