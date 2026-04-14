package com.example.projectneptune

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.projectneptune.data.CatchEntry
import com.example.projectneptune.data.MapRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatchLogDestination(
    repository: MapRepository,
    onAddClick: () -> Unit,
    onEditClick: (CatchEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries by repository.catchEntries.collectAsState()

    // Group entries by a human-readable date if possible, or just use the raw string
    // For simplicity, we'll extract the date part from the "time" field (e.g., "MM/dd/yyyy")
    val groupedEntries = entries.groupBy { entry ->
        entry.time.split(" ").firstOrNull() ?: "Unknown Date"
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        text = "Catch Log", 
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    ) 
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
            ) {
                // Add entries FAB
                ExtendedFloatingActionButton(
                    onClick = onAddClick,
                    containerColor = Color(0xFF333333),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(24.dp),
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add entries") }
                )
                // Filter/search FAB
                ExtendedFloatingActionButton(
                    onClick = { /* Search logic */ },
                    containerColor = Color(0xFF333333),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(24.dp),
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    text = { Text("Filter/search") }
                )
            }
        },
        modifier = modifier.fillMaxSize(),
        containerColor = Color.White
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            groupedEntries.forEach { (date, logs) ->
                item {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text(
                            text = date,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.DarkGray
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            thickness = 1.dp,
                            color = Color.LightGray.copy(alpha = 0.5f)
                        )
                    }
                }
                items(logs) { log ->
                    CatchCard(
                        log = log,
                        onEdit = { onEditClick(log) },
                        onDelete = {
                            // Optional: delete logic
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            // Spacer to avoid content being hidden by FABs
            item { Spacer(modifier = Modifier.height(140.dp)) }
        }
    }
}

@Composable
fun CatchCard(
    log: CatchEntry,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF2F2F2)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "${log.species} x${log.quantity}",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            )
            Text(
                text = log.location,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black
            )
            Text(
                text = log.time,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black
            )
            
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomEnd
            ) {
                TextButton(
                    onClick = onEdit,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Edit",
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
