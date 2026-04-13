package com.example.projectneptune

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Data model for a catch entry as shown in the wireframe.
 */
data class CatchLogEntry(
    val species: String,
    val count: Int,
    val area: String,
    val time: String,
    val date: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatchLogDestination(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Mock data based on the wireframe
    val entries = listOf(
        CatchLogEntry("Pacific Oyster", 3, "Area 17-1", "11:38 AM", "November 11, 2025"),
        CatchLogEntry("Butter Clam", 5, "Area 17-1", "11:36 AM", "November 11, 2025"),
        CatchLogEntry("Pacific Oyster", 7, "Area 16-3", "5:31 PM", "November 6, 2025"),
        CatchLogEntry("Manila Clam", 2, "Area 17-1", "11:15 AM", "October 25, 2025"),
        CatchLogEntry("Butter Clam", 6, "Area 17-1", "11:12 AM", "October 25, 2025"),
        CatchLogEntry("Littleneck Clam", 10, "Area 15-2", "2:45 PM", "October 20, 2025"),
        CatchLogEntry("Pacific Oyster", 4, "Area 17-1", "9:20 AM", "October 20, 2025"),
        CatchLogEntry("Geoduck", 1, "Area 18-4", "4:15 PM", "October 12, 2025"),
        CatchLogEntry("Dungeness Crab", 2, "Area 18-1", "2:00 PM", "October 5, 2025"),
        CatchLogEntry("Pacific Oyster", 8, "Area 17-1", "10:15 AM", "October 5, 2025"),
    )

    // Group entries by date for headers
    val groupedEntries = entries.groupBy { it.date }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        text = "Catch Log", 
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { /* Handle back */ }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
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
                    CatchCard(log)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            // Spacer to avoid content being hidden by FABs
            item { Spacer(modifier = Modifier.height(140.dp)) }
        }
    }
}

@Composable
fun CatchCard(log: CatchLogEntry) {
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
                text = "${log.species} x${log.count}",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            )
            Text(
                text = log.area,
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
                    onClick = { /* Edit logic */ },
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
