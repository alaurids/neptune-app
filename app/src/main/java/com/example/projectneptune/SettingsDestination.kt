package com.example.projectneptune

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcgismaps.Color
import com.arcgismaps.mapping.ArcGISMap
import com.arcgismaps.mapping.BasemapStyle
import com.arcgismaps.mapping.symbology.SimpleFillSymbol
import com.arcgismaps.mapping.symbology.SimpleFillSymbolStyle
import com.arcgismaps.mapping.symbology.SimpleLineSymbol
import com.arcgismaps.mapping.symbology.SimpleLineSymbolStyle
import com.arcgismaps.mapping.view.Graphic
import com.arcgismaps.mapping.view.GraphicsOverlay
import com.arcgismaps.mapping.view.MapView
import com.example.projectneptune.data.AppDatabase
import com.example.projectneptune.data.MapRepository
import com.example.projectneptune.isInternetAvailable
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsDestination(
    repository: MapRepository,
    onForceSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Use the shared repository's syncing state
    val isSyncing by repository.isSyncing.collectAsStateWithLifecycle()
    var lastUpdated by remember { mutableStateOf("Loading...") }
    var offlineSize by remember { mutableStateOf("0 MB") }
    
    var tideDays by remember { mutableFloatStateOf(7f) }
    
    var isSelectingArea by remember { mutableStateOf(false) }
    
    // Observe background download progress from repository
    val basemapProgress by repository.basemapDownloadProgress.collectAsStateWithLifecycle()
    val isDownloading = basemapProgress in 0..100
    val downloadProgress = if (basemapProgress >= 0) basemapProgress / 100f else -1f

    // Update offline size when download completes (progress resets to -1)
    LaunchedEffect(basemapProgress) {
        if (basemapProgress == -1) {
            offlineSize = repository.getOfflineBasemapSize()
        }
    }

    // Initial load of the timestamp and tide days
    LaunchedEffect(isSyncing) {
        if (!isSyncing) {
            val db = AppDatabase.getDatabase(context)
            val timestamp = db.mapDao().getMetadata("last_updated") ?: "Never"
            lastUpdated = timestamp
            tideDays = repository.getTideDownloadDays().toFloat()
            offlineSize = repository.getOfflineBasemapSize()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        if (isSelectingArea || isDownloading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (isSelectingArea && !isDownloading) {
                    SelectionMapView(
                        repository = repository,
                        onCancel = { isSelectingArea = false },
                        onDownload = { envelope ->
                            if (isInternetAvailable(context)) {
                                repository.startBasemapDownload(envelope)
                                isSelectingArea = false
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("You are offline. Please connect to the internet to download the map.")
                                }
                            }
                        }
                    )
                }
                
                if (isDownloading) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                "Downloading Basemap...",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "You can leave this screen; the download will continue.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            
                            if (downloadProgress >= 0) {
                                Spacer(modifier = Modifier.height(32.dp))
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.width(240.dp)
                                )
                                Text(
                                    "${(downloadProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(48.dp))
                            OutlinedButton(
                                onClick = { repository.cancelBasemapDownload() }
                            ) {
                                Text("Cancel Download")
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                HorizontalDivider()

                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Text(
                        text = "Map Data",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Last synchronized: $lastUpdated",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    Text(
                        text = "Data is automatically updated every hour when the app is open with an internet connection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Tide Forecast Duration",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "Download tides for the next ${tideDays.roundToInt()} days.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    Slider(
                        value = tideDays,
                        onValueChange = { tideDays = it },
                        onValueChangeFinished = {
                            scope.launch {
                                repository.updateTideDownloadDays(tideDays.roundToInt())
                            }
                        },
                        valueRange = 1f..14f,
                        steps = 12,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (isInternetAvailable(context)) {
                                onForceSync()
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("You are offline. Please connect to the internet to sync map data.")
                                }
                            }
                        },
                        enabled = !isSyncing,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Syncing...")
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Map Data Now")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Offline Basemap",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Downloaded: $offlineSize",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (isInternetAvailable(context)) {
                                    isSelectingArea = true
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("You are offline. Please connect to the internet to download a basemap.")
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Download Area")
                        }
                        OutlinedButton(
                            onClick = {
                                repository.clearOfflineBasemap()
                                offlineSize = "0 MB"
                            },
                            modifier = Modifier.weight(1f),
                            enabled = repository.hasOfflineBasemap()
                        ) {
                            Text("Clear Offline")
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun SelectionMapView(
    repository: MapRepository,
    onCancel: () -> Unit,
    onDownload: (com.arcgismaps.geometry.Envelope) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val arcGISMap = remember { ArcGISMap(BasemapStyle.ArcGISNavigation) }
    val mapView = remember { MapView(context) }
    val selectionOverlay = remember { GraphicsOverlay() }
    
    val selectionSymbol = remember { SimpleLineSymbol(SimpleLineSymbolStyle.Dash, Color.fromRgba(0, 122, 255, 255), 3f) }
    val selectionFill = remember { SimpleFillSymbol(SimpleFillSymbolStyle.Solid, Color.fromRgba(0, 122, 255, 40), selectionSymbol) }

    DisposableEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(mapView)
        onDispose { lifecycleOwner.lifecycle.removeObserver(mapView) }
    }

    LaunchedEffect(mapView) {
        mapView.map = arcGISMap
        mapView.graphicsOverlays.add(selectionOverlay)
        
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        mapView.viewpointChanged.debounce(100).collect {
            selectionOverlay.graphics.clear()
            mapView.visibleArea?.extent?.let { extent ->
                selectionOverlay.graphics.add(Graphic(extent, selectionFill))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // Top bar with back button
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(8.dp).statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Select Download Area", style = MaterialTheme.typography.titleLarge)
            }
        }

        // Map area (non-obstructed)
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        }
        
        // Bottom action section (Solid footer)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            tonalElevation = 8.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Adjust Map Area", style = MaterialTheme.typography.titleMedium)
                Text(
                    "The area visible above will be downloaded for offline use.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            mapView.visibleArea?.extent?.let { onDownload(it) }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Download")
                    }
                }
            }
        }
    }

}
