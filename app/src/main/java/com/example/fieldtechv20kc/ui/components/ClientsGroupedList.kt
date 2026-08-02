package com.example.fieldtechv20kc.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fieldtechv20kc.data.model.Client

/**
 * Shared composable for rendering clients in a grouped or flat list
 * Supports sticky locality headers and search/filter
 */
@Composable
fun ClientsGroupedList(
    clients: List<Client>,
    groupByLocality: Boolean,
    onClientClick: (Client) -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (Client, () -> Unit) -> Unit // pass your row renderer
) {
    data class Group(val title: String, val items: List<Client>)

    fun toGroups(list: List<Client>): List<Group> {
        val groups = list.groupBy { c ->
            val loc = c.locality?.trim().orEmpty()
            if (loc.isEmpty()) "(No locality)" else loc
        }
        return groups.toList()
            .sortedWith(compareBy<Pair<String, List<Client>>> { (k, _) ->
                if (k == "(No locality)") "\uFFFF" else k.lowercase()
            })
            .map { (k, v) -> Group(k, v.sortedBy { it.name.lowercase() }) }
    }

    if (!groupByLocality) {
        // Flat list view
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(items = clients, key = { it.id }) { c ->
                row(c) { onClientClick(c) }
                HorizontalDivider()
            }
        }
    } else {
        // Grouped view with sticky headers
        val groups = remember(clients) { toGroups(clients) }
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            groups.forEach { g ->
                stickyHeader {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                text = g.title,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IslandBadge(if (g.title == "(No locality)") null else g.title)
                        }
                    }
                }
                items(items = g.items, key = { it.id }) { c ->
                    row(c) { onClientClick(c) }
                    HorizontalDivider()
                }
            }
        }
    }
}




