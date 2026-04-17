package com.example.projectneptune

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcgismaps.Color
import com.arcgismaps.geometry.Envelope
import com.arcgismaps.geometry.Geometry
import com.arcgismaps.geometry.GeometryEngine
import com.arcgismaps.geometry.Polygon
import com.arcgismaps.geometry.SpatialReference
import com.arcgismaps.location.LocationDisplayAutoPanMode
import com.arcgismaps.mapping.ArcGISMap
import com.arcgismaps.mapping.Basemap
import com.arcgismaps.mapping.BasemapStyle
import com.arcgismaps.mapping.layers.ArcGISVectorTiledLayer
import com.arcgismaps.mapping.symbology.FontWeight as ArcGISFontWeight
import com.arcgismaps.mapping.symbology.HorizontalAlignment
import com.arcgismaps.mapping.symbology.SimpleFillSymbol
import com.arcgismaps.mapping.symbology.SimpleFillSymbolStyle
import com.arcgismaps.mapping.symbology.SimpleLineSymbol
import com.arcgismaps.mapping.symbology.SimpleLineSymbolStyle
import com.arcgismaps.mapping.symbology.SimpleMarkerSymbol
import com.arcgismaps.mapping.symbology.SimpleMarkerSymbolStyle
import com.arcgismaps.mapping.symbology.TextSymbol
import com.arcgismaps.mapping.symbology.VerticalAlignment
import com.arcgismaps.mapping.view.Graphic
import com.arcgismaps.mapping.view.GraphicsOverlay
import com.arcgismaps.mapping.view.MapView
import com.example.projectneptune.data.Layer20Feature
import com.example.projectneptune.data.MapRepository
import com.example.projectneptune.data.Station
import com.example.projectneptune.data.TideData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

// Optimized data structure to avoid expensive re-parsing of JSON during map movements
private data class PreparedFeature(
    val feature: Layer20Feature,
    val geometry: Geometry,
    val polygon: Polygon?
)

@OptIn(FlowPreview::class)
@Composable
fun MapDestination(
    repository: MapRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val isOnline by observeInternetConnectivity(context).collectAsStateWithLifecycle(initialValue = isInternetAvailable(context))

    var offlineExtent by remember { mutableStateOf<Envelope?>(null) }

    val arcGISMap = remember(isOnline) {
        if (isOnline) {
            ArcGISMap(BasemapStyle.ArcGISNavigation)
        } else if (repository.hasOfflineBasemap()) {
            val vtpkFile = java.io.File(context.filesDir, "basemap.vtpk")
            ArcGISMap(Basemap(ArcGISVectorTiledLayer(vtpkFile.absolutePath)))
        } else {
            ArcGISMap(BasemapStyle.ArcGISNavigation)
        }
    }

    val mapView = remember { MapView(context) }

    LaunchedEffect(arcGISMap, isOnline, mapView) {
        if (isOnline) {
            offlineExtent = null
            // Restrict online map to Coastal BC (approximate bounds)
            arcGISMap.maxExtent = Envelope(
                xMin = -134.0, yMin = 48.2, xMax = -122.5, yMax = 56.0,
                spatialReference = SpatialReference.wgs84()
            )
        } else if (repository.hasOfflineBasemap()) {
            val layer = arcGISMap.basemap.value?.baseLayers?.filterIsInstance<ArcGISVectorTiledLayer>()?.firstOrNull()
            layer?.load()?.onSuccess {
                val extent = layer.fullExtent
                offlineExtent = extent
                arcGISMap.maxExtent = extent
            }
        } else {
            offlineExtent = null
            arcGISMap.maxExtent = null
        }
    }
    val graphicsOverlay = remember { GraphicsOverlay() }

    // Symbols
    val redSymbol = remember { SimpleFillSymbol(SimpleFillSymbolStyle.Solid, Color.fromRgba(255, 179, 186, 255), SimpleLineSymbol(SimpleLineSymbolStyle.Solid, Color.fromRgba(0, 0, 0, 255), 1f)) }
    val yellowSymbol = remember { SimpleFillSymbol(SimpleFillSymbolStyle.Solid, Color.fromRgba(255, 255, 186, 255), SimpleLineSymbol(SimpleLineSymbolStyle.Solid, Color.fromRgba(0, 0, 0, 255), 1f)) }
    val greenSymbol = remember { SimpleFillSymbol(SimpleFillSymbolStyle.Solid, Color.fromRgba(186, 255, 201, 255), SimpleLineSymbol(SimpleLineSymbolStyle.Solid, Color.fromRgba(0, 0, 0, 255), 1f)) }
    
    val fullSpeciesList = remember {
        listOf(
            "ALL_BIVALVES", "BUTTER_CLAM", "GEODUCK_CLAM", "HORSE_CLAM",
            "LITTLENECK_CLAM", "MANILA_CLAM", "NUTTALLS_COCKLE", "PACIFIC_RAZOR_CLAM",
            "SOFTSHELL_CLAM", "VARNISH_CLAM", "BLUE_MUSSEL", "CALIFORNIA_MUSSEL",
            "OLYMPIA_OYSTER", "PACIFIC_OYSTER", "PINK_SCALLOP",
            "PURPLE_HINGE_ROCK_SCALLOP", "SPINY_SCALLOP", "WEATHERVANE_SCALLOP"
        )
    }

    var selectedFeature by remember { mutableStateOf<Layer20Feature?>(null) }
    var clickedFeatureId by remember { mutableStateOf<Int?>(null) }
    var showFilterPage by remember { mutableStateOf(false) }
    var visibleArea by remember { mutableStateOf<Polygon?>(null) }
    var showOfflineAlert by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val features by repository.features.collectAsStateWithLifecycle()
    val isSyncing by repository.isSyncing.collectAsStateWithLifecycle()
    val selectedSpecies by repository.selectedSpecies.collectAsStateWithLifecycle()
    val userLocation by mapView.locationDisplay.location.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(Unit) {
        delay(1200)
        if (repository.features.value.isEmpty() && !isSyncing && !isInternetAvailable(context)) {
            showOfflineAlert = true
        }
    }

    LaunchedEffect(clickedFeatureId) {
        if (clickedFeatureId != null) {
            delay(1000)
            clickedFeatureId = null
        }
    }

    var preparedFeatures by remember { mutableStateOf<List<PreparedFeature>>(emptyList()) }
    LaunchedEffect(features) {
        withContext(Dispatchers.Default) {
            preparedFeatures = features.mapNotNull { feature ->
                try {
                    val geometry = Geometry.fromJsonOrNull(feature.geometryJson) ?: return@mapNotNull null
                    if (geometry.spatialReference == null) return@mapNotNull null
                    PreparedFeature(feature, geometry, geometry as? Polygon)
                } catch (e: Exception) { null }
            }
        }
    }

    LaunchedEffect(mapView) {
        visibleArea = mapView.visibleArea
        mapView.viewpointChanged.debounce(300).collect { visibleArea = mapView.visibleArea }
    }

    LaunchedEffect(preparedFeatures, selectedSpecies, visibleArea, userLocation, clickedFeatureId, offlineExtent, isOnline) {
        if (preparedFeatures.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                val speciesToCheck = if (selectedSpecies.isEmpty()) fullSpeciesList else selectedSpecies.toList()
                val currentVisibleArea = visibleArea
                
                var displayFeatures = if (currentVisibleArea != null) {
                    preparedFeatures.filter { 
                        try {
                            val featureSR = it.geometry.spatialReference!!
                            val projectedArea = if (currentVisibleArea.spatialReference != featureSR) {
                                GeometryEngine.projectOrNull(currentVisibleArea, featureSR)
                            } else currentVisibleArea
                            projectedArea?.let { pa -> GeometryEngine.intersects(it.geometry, pa) } ?: true
                        } catch (e: Exception) { false }
                    }
                } else preparedFeatures

                // Clip features to the offline extent if in offline mode
                val currentOfflineExtent = offlineExtent
                if (!isOnline && currentOfflineExtent != null) {
                    displayFeatures = displayFeatures.mapNotNull { wrapper ->
                        try {
                            val featureSR = wrapper.geometry.spatialReference!!
                            val projectedExtent = if (currentOfflineExtent.spatialReference != featureSR) {
                                GeometryEngine.projectOrNull(currentOfflineExtent, featureSR)
                            } else currentOfflineExtent
                            
                            if (projectedExtent != null) {
                                val clipped = GeometryEngine.intersectionOrNull(wrapper.geometry, projectedExtent)
                                if (clipped != null && !clipped.isEmpty) {
                                    wrapper.copy(
                                        geometry = clipped,
                                        polygon = clipped as? Polygon
                                    )
                                } else null
                            } else wrapper
                        } catch (e: Exception) { null }
                    }
                }

                val userPos = userLocation?.position
                if (userPos != null && displayFeatures.isNotEmpty()) {
                    try {
                        val targetSR = displayFeatures[0].geometry.spatialReference!!
                        val projectedUserPos = if (userPos.spatialReference != targetSR) {
                            GeometryEngine.projectOrNull(userPos, targetSR)
                        } else userPos
                        
                        if (projectedUserPos != null) {
                            displayFeatures = displayFeatures.sortedBy { featureWrapper ->
                                try {
                                    GeometryEngine.distanceOrNull(featureWrapper.geometry, projectedUserPos) ?: Double.MAX_VALUE
                                } catch (e: Exception) { Double.MAX_VALUE }
                            }
                        }
                    } catch (e: Exception) {}
                }

                val graphics = mutableListOf<Graphic>()
                for (featureWrapper in displayFeatures.take(1000)) {
                    val feature = featureWrapper.feature
                    val isHighlighted = feature.objectId == clickedFeatureId
                    val openCount = speciesToCheck.count { getSpeciesStatus(feature, it) == 0 }
                    
                    var (areaSymbol, areaZIndex) = when {
                        openCount == speciesToCheck.size -> Pair(greenSymbol, 0)
                        openCount > 0 -> Pair(yellowSymbol, 1)
                        else -> Pair(redSymbol, 2)
                    }

                    if (isHighlighted) {
                        val baseColor = areaSymbol.color
                        areaSymbol = SimpleFillSymbol(SimpleFillSymbolStyle.Solid, Color.fromRgba(baseColor.red, baseColor.green, baseColor.blue, 160), SimpleLineSymbol(SimpleLineSymbolStyle.Solid, Color.fromRgba(0, 122, 255, 255), 4f))
                        areaZIndex = 100
                    }

                    graphics.add(Graphic(featureWrapper.geometry, mapOf("type" to "area"), areaSymbol).apply { zIndex = areaZIndex })
                    
                    if (graphics.size <= 50) {
                        featureWrapper.polygon?.let { polygon ->
                            try {
                                GeometryEngine.labelPointOrNull(polygon)?.let { labelPoint ->
                                    val labelZIndex = if (isHighlighted) 105 else 5
                                    graphics.add(Graphic(labelPoint, mapOf("type" to "label_visual"), TextSymbol(feature.poNum, Color.fromRgba(0, 0, 0, 255), 14f, HorizontalAlignment.Center, VerticalAlignment.Middle).apply {
                                        haloColor = if (isHighlighted) Color.fromRgba(0, 122, 255, 255) else Color.fromRgba(255, 255, 255, 255)
                                        haloWidth = if (isHighlighted) 4f else 2f
                                        fontWeight = ArcGISFontWeight.Bold
                                    }).apply { zIndex = labelZIndex })

                                    graphics.add(Graphic(labelPoint, mapOf("type" to "label_hit_proxy", "OBJECTID" to feature.objectId), SimpleMarkerSymbol(SimpleMarkerSymbolStyle.Circle, Color.fromRgba(0, 0, 0, 0), 80f)).apply { zIndex = if (isHighlighted) 110 else 10 })
                                }
                            } catch (e: Exception) {}
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    graphicsOverlay.graphics.clear()
                    graphicsOverlay.graphics.addAll(graphics)
                }
            }
        }
    }

    val currentFeatures by rememberUpdatedState(features)

    LaunchedEffect(mapView, arcGISMap) {
        mapView.map = arcGISMap
        if (!mapView.graphicsOverlays.contains(graphicsOverlay)) mapView.graphicsOverlays.add(graphicsOverlay)
        
        mapView.onSingleTapConfirmed.collect { event ->
            mapView.identifyGraphicsOverlay(graphicsOverlay, event.screenCoordinate, 40.0, false).onSuccess { identifyResult ->
                identifyResult.graphics.find { it.attributes["type"] == "label_hit_proxy" }?.let { target ->
                    (target.attributes["OBJECTID"] as? Int)?.let { objectId ->
                        clickedFeatureId = objectId
                        scope.launch { delay(500); selectedFeature = currentFeatures.find { it.objectId == objectId } }
                    }
                }
            }
        }
    }

    var isLocationPermissionGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isLocationPermissionGranted = it }
    LaunchedEffect(Unit) { if (!isLocationPermissionGranted) locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
    
    DisposableEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(mapView)
        onDispose { lifecycleOwner.lifecycle.removeObserver(mapView) }
    }

    LaunchedEffect(isLocationPermissionGranted) {
        if (isLocationPermissionGranted) {
            mapView.locationDisplay.dataSource.start().onSuccess { mapView.locationDisplay.setAutoPanMode(LocationDisplayAutoPanMode.Recenter) }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            update = { it.map = arcGISMap },
            modifier = Modifier.fillMaxSize()
        )
        
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))

        if (isSyncing) CircularProgressIndicator(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp).size(24.dp), strokeWidth = 3.dp)

        Column(modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 50.dp, end = 16.dp)) {
            FloatingActionButton(onClick = { scope.launch { if (isLocationPermissionGranted) mapView.locationDisplay.setAutoPanMode(LocationDisplayAutoPanMode.Recenter) else locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) } }, modifier = Modifier.padding(bottom = 16.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) { Icon(Icons.Default.MyLocation, contentDescription = "My Location") }
            FloatingActionButton(onClick = { showFilterPage = true }, modifier = Modifier.padding(bottom = 16.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) { Icon(Icons.Default.FilterList, contentDescription = "Filter Species") }
            FloatingActionButton(onClick = { scope.launch { mapView.setViewpointScale(scale = mapView.mapScale.value * 0.5) } }, modifier = Modifier.padding(bottom = 8.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) { Icon(Icons.Default.Add, contentDescription = "Zoom In") }
            FloatingActionButton(onClick = { scope.launch { mapView.setViewpointScale(scale = mapView.mapScale.value * 2.0) } }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) { Icon(Icons.Default.Remove, contentDescription = "Zoom Out") }
        }

        if (selectedFeature != null) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                FeatureDetailView(feature = selectedFeature!!, repository = repository, onBack = { selectedFeature = null })
            }
        }

        if (showFilterPage) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                SpeciesFilterPage(
                    selectedSpecies = selectedSpecies,
                    // Filter out "ALL_BIVALVES" from the checklist
                    fullSpeciesList = fullSpeciesList.filter { it != "ALL_BIVALVES" },
                    onSpeciesToggle = { species ->
                        val newSelection = if (selectedSpecies.contains(species)) selectedSpecies - species else selectedSpecies + species
                        repository.updateSpeciesFilter(newSelection)
                    },
                    onBack = { showFilterPage = false }
                )
            }
        }
        
        if (showOfflineAlert) {
            AlertDialog(
                onDismissRequest = { showOfflineAlert = false },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(stringResource(R.string.offlineNoMapData1)) },
                text = { Text(stringResource(R.string.offlineNoMapData2), textAlign = TextAlign.Center) },
                confirmButton = { Button(onClick = { showOfflineAlert = false }) { Text("OK") } }
            )
        }
    }
}

private fun getSpeciesStatus(feature: Layer20Feature, speciesName: String): Int {
    return when (speciesName) {
        "ALL_BIVALVES" -> feature.allBivalves
        "BUTTER_CLAM" -> feature.butterClam
        "GEODUCK_CLAM" -> feature.geoduckClam
        "HORSE_CLAM" -> feature.horseClam
        "LITTLENECK_CLAM" -> feature.littleneckClam
        "MANILA_CLAM" -> feature.manilaClam
        "NUTTALLS_COCKLE" -> feature.nuttallsCockle
        "PACIFIC_RAZOR_CLAM" -> feature.pacificRazorClam
        "SOFTSHELL_CLAM" -> feature.softshellClam
        "VARNISH_CLAM" -> feature.varnishClam
        "BLUE_MUSSEL" -> feature.blueMussel
        "CALIFORNIA_MUSSEL" -> feature.californiaMussel
        "OLYMPIA_OYSTER" -> feature.olympiaOyster
        "PACIFIC_OYSTER" -> feature.pacificOyster
        "PINK_SCALLOP" -> feature.pinkScallop
        "PURPLE_HINGE_ROCK_SCALLOP" -> feature.purpleHingeRockScallop
        "SPINY_SCALLOP" -> feature.spinyScallop
        "WEATHERVANE_SCALLOP" -> feature.weathervaneScallop
        else -> -1
    }
}

@Composable
fun SpeciesFilterPage(selectedSpecies: Set<String>, fullSpeciesList: List<String>, onSpeciesToggle: (String) -> Unit, onBack: () -> Unit) {
    val butterClamStr = stringResource(R.string.bc_name)
    val geoduckStr = stringResource(R.string.g_name)
    val horseClamStr = stringResource(R.string.hc_name)
    val littleneckClamStr = stringResource(R.string.lc_name)
    val manilaClamStr = stringResource(R.string.mc_name)
    val nuttallsCockleStr = stringResource(R.string.nc_name)
    val razorClamStr = stringResource(R.string.rc_name)
    val softshellClamStr = stringResource(R.string.sc_name)
    val varnishClamStr = stringResource(R.string.vc_name)
    val blueMusselStr = stringResource(R.string.bm_name)
    val californiaMusselStr = stringResource(R.string.cm_name)
    val olympiaOysterStr = stringResource(R.string.oo_name)
    val pacificOysterStr = stringResource(R.string.po_name)
    val pinkScallopStr = stringResource(R.string.ps_name)
    val purpleScallopStr = stringResource(R.string.rs_name)
    val spinyScallopStr = stringResource(R.string.ss_name)
    val weathervaneScallopStr = stringResource(R.string.ws_name)

    val displayNames = remember(
        butterClamStr, geoduckStr, horseClamStr, littleneckClamStr, manilaClamStr,
        nuttallsCockleStr, razorClamStr, softshellClamStr, varnishClamStr,
        blueMusselStr, californiaMusselStr, olympiaOysterStr, pacificOysterStr,
        pinkScallopStr, purpleScallopStr, spinyScallopStr, weathervaneScallopStr
    ) {
        mapOf(
            "BUTTER_CLAM" to butterClamStr,
            "GEODUCK_CLAM" to geoduckStr,
            "HORSE_CLAM" to horseClamStr,
            "LITTLENECK_CLAM" to littleneckClamStr,
            "MANILA_CLAM" to manilaClamStr,
            "NUTTALLS_COCKLE" to nuttallsCockleStr,
            "PACIFIC_RAZOR_CLAM" to razorClamStr,
            "SOFTSHELL_CLAM" to softshellClamStr,
            "VARNISH_CLAM" to varnishClamStr,
            "BLUE_MUSSEL" to blueMusselStr,
            "CALIFORNIA_MUSSEL" to californiaMusselStr,
            "OLYMPIA_OYSTER" to olympiaOysterStr,
            "PACIFIC_OYSTER" to pacificOysterStr,
            "PINK_SCALLOP" to pinkScallopStr,
            "PURPLE_HINGE_ROCK_SCALLOP" to purpleScallopStr,
            "SPINY_SCALLOP" to spinyScallopStr,
            "WEATHERVANE_SCALLOP" to weathervaneScallopStr
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
            Text(text = stringResource(R.string.filter), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            fullSpeciesList.forEach { species ->
                val displayName = displayNames[species] ?: species.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = selectedSpecies.contains(species), onCheckedChange = { onSpeciesToggle(species) })
                    Text(text = displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) { Text("OK") }
    }
}

@Composable
fun FeatureDetailView(feature: Layer20Feature, repository: MapRepository, onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(stringResource(R.string.regulations), stringResource(R.string.tides))
    val staticBoundaries by repository.staticBoundaries.collectAsStateWithLifecycle()
    
    val overlapsStatic by produceState(initialValue = false, feature, staticBoundaries) {
        value = withContext(Dispatchers.Default) {
            val featureGeom = Geometry.fromJsonOrNull(feature.geometryJson) ?: return@withContext false
            staticBoundaries.any { boundary ->
                try {
                    val boundaryGeom = Geometry.fromJsonOrNull(boundary.geometryJson) ?: return@any false
                    val projectedBoundary = if (boundaryGeom.spatialReference != featureGeom.spatialReference) {
                        GeometryEngine.projectOrNull(boundaryGeom, featureGeom.spatialReference!!)
                    } else boundaryGeom
                    projectedBoundary?.let { GeometryEngine.intersects(featureGeom, it) } ?: false
                } catch (e: Exception) { false }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
            Text(
                text = feature.poNum,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        TabRow(selectedTabIndex = selectedTab, modifier = Modifier.padding(vertical = 8.dp)) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (selectedTab == 0) {
                RegulationsContent(feature, repository, overlapsStatic)
            } else {
                TidesContent(feature, repository)
            }
        }
    }
}

@Composable
fun RegulationsContent(feature: Layer20Feature, repository: MapRepository, overlapsStatic: Boolean = false) {
    val uriHandler = LocalUriHandler.current
    val zoneId = if (overlapsStatic) "PACIFIC-RIM" else "DEFAULT"
    val catchLimit by produceState<com.example.projectneptune.data.CatchLimit?>(initialValue = null, zoneId) {
        value = repository.getCatchLimit(zoneId)
    }

    val reasonText = when (feature.reason) {
        1 -> stringResource(R.string.reason1)
        2 -> stringResource(R.string.reason2)
        3 -> stringResource(R.string.reason3)
        4 -> stringResource(R.string.reason4)
        5 -> stringResource(R.string.reason5)
        6 -> stringResource(R.string.reason6)
        7 -> stringResource(R.string.reason7)
        8 -> stringResource(R.string.reason8)
        9 -> stringResource(R.string.reason9)
        10 -> stringResource(R.string.reason10)
        else -> feature.reason.toString()
    }
    
    // Define a structure for ordered rows with indentation and group status calculation
    data class RegulationRow(val keys: List<String>, val label: String, val limitKey: String, val indented: Boolean = false)


    val allClamStr = stringResource(R.string.allClams)
    val butterClamStr = stringResource(R.string.bc_name)
    val geoduckStr = stringResource(R.string.g_name)
    val horseClamStr = stringResource(R.string.hc_name)
    val littleneckClamStr = stringResource(R.string.lc_name)
    val manilaClamStr = stringResource(R.string.mc_name)
    val nuttallsCockleStr = stringResource(R.string.nc_name)
    val razorClamStr = stringResource(R.string.rc_name)
    val softshellClamStr = stringResource(R.string.sc_name)
    val varnishClamStr = stringResource(R.string.vc_name)
    val allMusselStr = stringResource(R.string.allMussels)
    val blueMusselStr = stringResource(R.string.bm_name)
    val californiaMusselStr = stringResource(R.string.cm_name)
    val olympiaOysterStr = stringResource(R.string.oo_name)
    val pacificOysterStr = stringResource(R.string.po_name)
    val pinkAndSpinyStr = stringResource(R.string.pinkAndSpiny)
    val pinkScallopStr = stringResource(R.string.ps_name)
    val spinyScallopStr = stringResource(R.string.ss_name)
    val purpleAndWeathervaneStr = stringResource(R.string.purpleAndWeathervane)
    val purpleScallopStr = stringResource(R.string.rs_name)
    val weathervaneScallopStr = stringResource(R.string.ws_name)

    val tableRows = remember {
        listOf(
            RegulationRow(listOf("BUTTER_CLAM", "HORSE_CLAM", "LITTLENECK_CLAM", "MANILA_CLAM", "PACIFIC_RAZOR_CLAM", "SOFTSHELL_CLAM", "VARNISH_CLAM"), allClamStr, "allClams"),
            RegulationRow(listOf("BUTTER_CLAM"), butterClamStr, "butterClam", true),
            RegulationRow(listOf("HORSE_CLAM"), horseClamStr, "horseClam", true),
            RegulationRow(listOf("LITTLENECK_CLAM"), littleneckClamStr, "littleneckClam", true),
            RegulationRow(listOf("MANILA_CLAM"), manilaClamStr, "manilaClam", true),
            RegulationRow(listOf("PACIFIC_RAZOR_CLAM"), razorClamStr, "pacificRazorClam", true),
            RegulationRow(listOf("SOFTSHELL_CLAM"), softshellClamStr, "softshellClam", true),
            RegulationRow(listOf("VARNISH_CLAM"), varnishClamStr, "varnishClam", true),
            RegulationRow(listOf("GEODUCK_CLAM"), geoduckStr, "geoduck"),
            RegulationRow(listOf("NUTTALLS_COCKLE"), nuttallsCockleStr, "nuttallsCockle"),
            RegulationRow(listOf("BLUE_MUSSEL", "CALIFORNIA_MUSSEL"), allMusselStr, "allMussels"),
            RegulationRow(listOf("BLUE_MUSSEL"), blueMusselStr, "blueMussel", true),
            RegulationRow(listOf("CALIFORNIA_MUSSEL"), californiaMusselStr, "californiaMussel", true),
            RegulationRow(listOf("OLYMPIA_OYSTER"), olympiaOysterStr, "olympiaOyster"),
            RegulationRow(listOf("PACIFIC_OYSTER"), pacificOysterStr, "pacificOyster"),
            RegulationRow(listOf("PINK_SCALLOP", "SPINY_SCALLOP"), pinkAndSpinyStr, "pinkAndSpiny"),
            RegulationRow(listOf("PINK_SCALLOP"), pinkScallopStr, "pinkScallop", true),
            RegulationRow(listOf("SPINY_SCALLOP"), spinyScallopStr, "spinyScallop", true),
            RegulationRow(listOf("PURPLE_HINGE_ROCK_SCALLOP", "WEATHERVANE_SCALLOP"), purpleAndWeathervaneStr, "purpleAndWeathervane"),
            RegulationRow(listOf("PURPLE_HINGE_ROCK_SCALLOP"), purpleScallopStr, "purpleScallop", true),
            RegulationRow(listOf("WEATHERVANE_SCALLOP"), weathervaneScallopStr, "weathervaneScallop", true)
        )
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (overlapsStatic) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.prOverlap),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        DetailRow(label = stringResource(R.string.placeName), value = feature.placeNameEn)
        Text(text = stringResource(R.string.url), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (feature.publicNoticeUrl != "N/A" && feature.publicNoticeUrl.startsWith("http")) {
            Text(text = feature.publicNoticeUrl, style = MaterialTheme.typography.bodyLarge.copy(textDecoration = TextDecoration.Underline), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp).clickable { try { uriHandler.openUri(feature.publicNoticeUrl) } catch (e: Exception) { Log.e("MapDestination", "Failed to open notice URL", e) } })
        } else {
            SelectionContainer { Text(text = feature.publicNoticeUrl, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary) }
        }
        Spacer(modifier = Modifier.height(12.dp))
        DetailRow(label = stringResource(R.string.reason), value = reasonText)
        DetailRow(label = stringResource(R.string.enforceDate), value = feature.enforceDateEn)
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = stringResource(R.string.speciesDailyHarvestLimit), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
        Column(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline)) {
            Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(8.dp)) {
                Text(text = stringResource(R.string.species), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(text = stringResource(R.string.limit), modifier = Modifier.width(80.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            tableRows.forEach { row ->
                // Calculate status based on all keys in the row (for groups)
                val statuses = row.keys.map { getSpeciesStatus(feature, it) }
                val statusValue = when {
                    statuses.any { it == 0 } -> 0 // If any sub-species is open, the group is open
                    statuses.all { it == 1 } -> 1 // Only closed if ALL listed species are closed
                    else -> -1 // Mix of closed/N/A or all N/A
                }
                val statusText = when (statusValue) { 0 -> stringResource(R.string.open); 1 -> "0 (" + stringResource(R.string.closed) + ")"; else -> "N/A" }
                
                // Get the limit value from the CatchLimit entity
                val limitValue = catchLimit?.let { cl ->
                    when (row.limitKey) {
                        "allClams" -> cl.allClams
                        "butterClam" -> cl.butterClam
                        "geoduck" -> cl.geoduck
                        "horseClam" -> cl.horseClam
                        "littleneckClam" -> cl.littleneckClam
                        "manilaClam" -> cl.manilaClam
                        "pacificRazorClam" -> cl.pacificRazorClam
                        "softshellClam" -> cl.softshellClam
                        "varnishClam" -> cl.varnishClam
                        "nuttallsCockle" -> cl.nuttallsCockle
                        "allMussels" -> cl.allMussels
                        "blueMussel" -> cl.blueMussel
                        "californiaMussel" -> cl.californiaMussel
                        "olympiaOyster" -> cl.olympiaOyster
                        "pacificOyster" -> cl.pacificOyster
                        "pinkAndSpiny" -> cl.pinkAndSpiny
                        "pinkScallop" -> cl.pinkScallop
                        "spinyScallop" -> cl.spinyScallop
                        "purpleAndWeathervane" -> cl.purpleAndWeathervane
                        "purpleScallop" -> cl.purpleScallop
                        "weathervaneScallop" -> cl.weathervaneScallop
                        else -> 0
                    }
                }?.toString() ?: "..."

                val displayLimit = if (statusValue == 1) "0 (" + stringResource(R.string.closed) + ")" else limitValue

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.label, 
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = if (row.indented) 24.dp else 0.dp), 
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (!row.indented) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = displayLimit,
                        modifier = Modifier.width(80.dp), 
                        style = MaterialTheme.typography.bodyMedium, 
                        color = if (statusText == "0 (" + stringResource(R.string.closed) + ")") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = if (statusText == "0 (" + stringResource(R.string.closed) + ")") FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        Text(
            text = "* " + stringResource(R.string.possessionLimit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun TidesContent(feature: Layer20Feature, repository: MapRepository) {
    val stations by repository.stations.collectAsStateWithLifecycle()
    var tideResult by remember { mutableStateOf<Pair<Station, List<TideData>>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val currentLocale = LocalConfiguration.current.locales[0]

    val closestStation = remember(feature, stations) {
        stations.find { it.id == feature.closestStationId }
    }

    LaunchedEffect(feature, stations) {
        isLoading = true
        tideResult = repository.getTideDataWithFallback(feature)
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(vertical = 16.dp)) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (tideResult != null) {
            val (usedStation, tideData) = tideResult!!

            Text(
                text = stringResource(R.string.closestStation),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = usedStation.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            if (tideData.isEmpty()) {
                Text(stringResource(R.string.noTideData))
            } else {
                Text(
                    text = stringResource(R.string.highLowTide),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Tide Table
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(8.dp)
                    ) {
                        Text(stringResource(R.string.date), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.time), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.height), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    }
                    
                    // Group data by date
                    val groupedData = tideData.groupBy { it.getDateLabel(currentLocale) }

                    groupedData.forEach { (date, events) ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Date Column (merged look)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .align(Alignment.CenterVertically)
                                    .padding(8.dp)
                            ) {
                                Text(text = date, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            
                            // Times and Values
                            Column(modifier = Modifier.weight(2f)) {
                                events.forEachIndexed { index, event ->
                                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                        Text(text = event.getTimeLabel(currentLocale), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        Text(text = String.format(currentLocale, "%.2f", event.value), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Text(stringResource(R.string.noTideData))
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
