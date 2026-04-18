package com.example.projectneptune

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable

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
    var showFilter by rememberSaveable { mutableStateOf(false) }
    var selectedSpeciesFilter by rememberSaveable { mutableStateOf(setOf<String>()) }

    val fullSpeciesList = remember {
        listOf(
            "BUTTER_CLAM", "GEODUCK", "HORSE_CLAM", "LITTLENECK_CLAM",
            "MANILA_CLAM", "NUTTALL'S_COCKLE", "RAZOR_CLAM", "SOFTSHELL_CLAM",
            "VARNISH_CLAM", "BLUE_MUSSEL", "CALIFORNIA_MUSSEL", "OLYMPIA_OYSTER",
            "PACIFIC_OYSTER", "PINK_SCALLOP", "PURPLE_SCALLOP",
            "SPINY_SCALLOP", "WEATHERVANE_SCALLOP"
        )
    }

    if (showFilter) {
        CatchLogFilterPage(
            selectedSpecies = selectedSpeciesFilter,
            fullSpeciesList = fullSpeciesList,
            onSpeciesToggle = { species: String ->
                selectedSpeciesFilter = if (selectedSpeciesFilter.contains(species)) {
                    selectedSpeciesFilter - species
                } else {
                    selectedSpeciesFilter + species
                }
            },
            onBack = { showFilter = false }
        )
        return
    }

    // Apply filtering
    val filteredEntries = if (selectedSpeciesFilter.isEmpty()) {
        entries
    } else {
        entries.filter { entry ->
            selectedSpeciesFilter.any { filter ->
                filter.replace("_", " ").equals(entry.species, ignoreCase = true)
            }
        }
    }

    // Group entries by a human-readable date
    val groupedEntries = filteredEntries.groupBy { entry ->
        entry.time.split(" ").firstOrNull() ?: "Unknown Date"
    }

    Scaffold(
        floatingActionButton = {
            FabButtons(
                onAddClick = onAddClick,
                onFilterClick = { showFilter = true }
            )
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.catchLog),
                    style = MaterialTheme.typography.headlineMedium
                )
                
                if (selectedSpeciesFilter.isNotEmpty()) {
                    TextButton(
                        onClick = { selectedSpeciesFilter = emptySet() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.clearFilter))
                    }
                }
            }

            HorizontalDivider()

            if (filteredEntries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.noResults),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
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
}

@Composable
fun CatchLogFilterPage(
    selectedSpecies: Set<String>,
    fullSpeciesList: List<String>,
    onSpeciesToggle: (String) -> Unit,
    onBack: () -> Unit
) {
    val displayNames = remember {
        // This mapping uses the internal keys to look up localized names later
        // For the Checklist UI, we'll use getTranslatedSpecies directly for each item
        fullSpeciesList
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { 
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) 
            }
            Text(
                text = stringResource(R.string.filter), 
                style = MaterialTheme.typography.headlineMedium, 
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            fullSpeciesList.forEach { speciesKey ->
                // Use the existing translation logic
                val displayName = getTranslatedSpecies(speciesKey.replace("_", " "))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSpeciesToggle(speciesKey) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selectedSpecies.contains(speciesKey),
                        onCheckedChange = { onSpeciesToggle(speciesKey) }
                    )
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text(stringResource(R.string.apply))
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
