package com.example.projectneptune

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.projectneptune.data.CatchEntry
import com.example.projectneptune.data.MapRepository
import java.util.Locale
import android.content.res.Configuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatchLogDestination(
    repository: MapRepository,
    onAddClick: () -> Unit,
    onEditClick: (CatchEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries by repository.catchEntries.collectAsState()
    val scope = rememberCoroutineScope()

    // Group entries by a human-readable date if possible, or just use the raw string
    // For simplicity, we'll extract the date part from the "time" field (e.g., "MM/dd/yyyy")
    val groupedEntries = entries.groupBy { entry ->
        entry.time.split(" ").firstOrNull() ?: "Unknown Date"
    }

    Scaffold(
        floatingActionButton = {
            FabButtons(onAddClick = onAddClick)
        },
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.catchLog),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
            )

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                groupedEntries.forEach { (date, logs) ->
                    item {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            Text(
                                text = date,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    items(logs) { log ->
                        CatchCard(
                            log = log,
                            onEdit = { onEditClick(log) },
                            onDelete = {
                                scope.launch {
                                    repository.deleteCatchEntry(log.id)
                                }
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
}

@Composable
fun FabButtons(
    onAddClick: () -> Unit,
    onFilterClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 16.dp, end = 16.dp)
    ) {
        // Add entries FAB
        ExtendedFloatingActionButton(
            onClick = onAddClick,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(24.dp),
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.addEntries)) }
        )
        // Filter/search FAB
        ExtendedFloatingActionButton(
            onClick = onFilterClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(24.dp),
            icon = { Icon(Icons.Default.Search, contentDescription = null) },
            text = { Text(stringResource(R.string.filter)) }
        )
    }
}

@Composable
fun getTranslatedSpecies(speciesName: String): String {
    val context = LocalContext.current
    val currentConfig = LocalConfiguration.current
    val speciesIds = remember {
        listOf(
            R.string.bc_name, R.string.g_name, R.string.hc_name, R.string.lc_name,
            R.string.mc_name, R.string.rc_name, R.string.sc_name, R.string.vc_name,
            R.string.nc_name, R.string.bm_name, R.string.cm_name, R.string.po_name,
            R.string.oo_name, R.string.ps_name, R.string.ss_name, R.string.rs_name,
            R.string.ws_name, R.string.na_name
        )
    }

    val translatedName = remember(speciesName, Locale.getDefault()) {
        // Create localized resources for lookup
        val configEn = Configuration(currentConfig).apply { setLocale(Locale.ENGLISH) }
        val configFr = Configuration(currentConfig).apply { setLocale(Locale.FRENCH) }
        
        val resEn = context.createConfigurationContext(configEn).resources
        val resFr = context.createConfigurationContext(configFr).resources

        val matchedId: Int? = speciesIds.find { id: Int ->
            resEn.getString(id).equals(speciesName, ignoreCase = true) ||
            resFr.getString(id).equals(speciesName, ignoreCase = true)
        }
        
        matchedId
    }

    return if (translatedName != null) stringResource(translatedName) else speciesName
}

@Composable
fun CatchCard(
    log: CatchEntry,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val displaySpecies = getTranslatedSpecies(log.species)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "$displaySpecies x${log.quantity}",
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
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                TextButton(
                    onClick = onEdit,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.edit),
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
