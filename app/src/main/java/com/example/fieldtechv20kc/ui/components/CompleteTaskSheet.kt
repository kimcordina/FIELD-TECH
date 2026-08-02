package com.example.fieldtechv20kc.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fieldtechv20kc.data.model.ServiceTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteTaskSheet(
    clientName: String,
    tasks: List<ServiceTask>,
    onSelectTaskAndConfirm: (taskId: String?) -> Unit, // pass job id if YES, null if NO
    onViewTask: (taskId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTaskId by remember { mutableStateOf<String?>(tasks.firstOrNull()?.id) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Pending job detected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Client \"$clientName\" has ${tasks.size} pending job${if (tasks.size > 1) "s" else ""}. Should this report complete ${if (tasks.size > 1) "one of them" else "it"} when finished?",
                style = MaterialTheme.typography.bodyMedium
            )

            if (tasks.size > 1) {
                Text(
                    "Select a job:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 240.dp)
                ) {
                    items(tasks) { t ->
                        ElevatedCard(
                            onClick = { selectedTaskId = t.id },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    t.title.ifBlank { "Service visit" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selectedTaskId == t.id) FontWeight.Bold else FontWeight.Normal
                                )
                                val line = buildString {
                                    if (!t.assignedToName.isNullOrBlank()) append("${t.assignedToName} • ")
                                    if (t.scheduledDate > 0) {
                                        append(android.text.format.DateFormat.format("dd MMM yyyy", t.scheduledDate))
                                    }
                                }
                                if (line.isNotBlank()) {
                                    Text(
                                        line,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (tasks.size == 1) {
                val t = tasks.first()
                ElevatedCard(
                    onClick = { /* no-op */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            t.title.ifBlank { "Service visit" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        val line = buildString {
                            if (!t.assignedToName.isNullOrBlank()) append("${t.assignedToName} • ")
                            if (t.scheduledDate > 0) {
                                append(android.text.format.DateFormat.format("dd MMM yyyy", t.scheduledDate))
                            }
                        }
                        if (line.isNotBlank()) {
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onViewTask(t.id) }) {
                        Text("View job")
                    }
                }
                selectedTaskId = t.id
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onSelectTaskAndConfirm(null) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("No")
                }
                Button(
                    onClick = { onSelectTaskAndConfirm(selectedTaskId) },
                    enabled = tasks.isNotEmpty() && selectedTaskId != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Yes")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}


