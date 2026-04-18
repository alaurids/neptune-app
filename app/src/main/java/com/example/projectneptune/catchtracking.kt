package com.example.projectneptune

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.projectneptune.data.MapRepository
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.arcgismaps.geometry.GeometryEngine
import com.arcgismaps.geometry.Point
import com.arcgismaps.geometry.SpatialReference
import com.arcgismaps.mapping.ArcGISMap
import com.arcgismaps.mapping.BasemapStyle
import com.arcgismaps.mapping.view.MapView
import com.arcgismaps.mapping.view.ScreenCoordinate
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatchTracking(
    repository: MapRepository,
    onBackClick: () -> Unit,
    onSubmitClick: () -> Unit,
    onCameraClick: () -> Unit,
    initialSpecies: String = "",
    initialQuantity: String = "",
    initialTime: String = "",
    initialLocation: String = "",
    entryId: Int = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    var species by remember { mutableStateOf(initialSpecies) }
    var quantity by remember { mutableStateOf(initialQuantity) }
    
    val defaultTime = remember {
        val sdf = SimpleDateFormat("MM/dd/yyyy hh:mm a z", Locale.getDefault())
        sdf.timeZone = java.util.TimeZone.getTimeZone("PST")
        sdf.format(java.util.Date())
    }
    var time by remember { mutableStateOf(if (initialTime.isEmpty()) defaultTime else initialTime) }
    val fetchingLocationText = stringResource(R.string.fetchingLocation)
    var location by remember { mutableStateOf(if (initialLocation.isEmpty()) fetchingLocationText else initialLocation) }
    var permissionGranted by remember { 
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) 
    }

    var validationWarning by remember { mutableStateOf<String?>(null) }
    var showWarningDialog by remember { mutableStateOf(false) }
    var showMapPicker by remember { mutableStateOf(false) }

    // List of species from ReferenceGuideDestination
    val speciesIds = remember {
        listOf(
            R.string.bc_name, R.string.g_name, R.string.hc_name, R.string.lc_name,
            R.string.mc_name, R.string.rc_name, R.string.sc_name, R.string.vc_name,
            R.string.nc_name, R.string.bm_name, R.string.cm_name, R.string.po_name,
            R.string.oo_name, R.string.ps_name, R.string.ss_name, R.string.rs_name,
            R.string.ws_name, R.string.na_name
        )
    }
    
    val speciesOptions = speciesIds.map { stringResource(it) }.sorted()
    
    var expanded by remember { mutableStateOf(false) }
    val filteredOptions = remember(species, speciesOptions) {
        if (species.isEmpty()) {
            speciesOptions
        } else {
            speciesOptions.filter { it.contains(species, ignoreCase = true) }
        }
    }

    val onActualSubmit = {
        scope.launch {
            val qInt = quantity.toIntOrNull() ?: 0
            val warning = repository.getValidationWarning(species, qInt, location, time)
            if (warning != null && validationWarning == null) {
                validationWarning = warning
                showWarningDialog = true
            } else {
                repository.upsertCatchEntry(species, quantity, time, location, entryId)
                onSubmitClick()
            }
        }
    }

    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
            title = { Text(if (validationWarning != null) stringResource(R.string.regWarning) else stringResource(R.string.missingInfo1)) },
            text = { Text(validationWarning ?: stringResource(R.string.missingInfo2)) },
            confirmButton = {
                TextButton(onClick = {
                    showWarningDialog = false
                    scope.launch {
                        repository.upsertCatchEntry(species, quantity, time, location, entryId)
                        onSubmitClick()
                    }
                }) {
                    Text(if (validationWarning != null) stringResource(R.string.addAnyway) else stringResource(R.string.cont))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showWarningDialog = false 
                    validationWarning = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val locUnavailable = stringResource(R.string.locUnavailable)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionGranted = isGranted
        if (!isGranted) {
            location = locUnavailable
        }
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted && initialLocation.isEmpty()) {
            val cancellationTokenSource = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { loc ->
                if (loc != null) {
                    location = String.format(Locale.US, "%.6f, %.6f", loc.latitude, loc.longitude)
                } else {
                    location = locUnavailable
                }
            }.addOnFailureListener {
                location = locUnavailable
            }
        } else if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.catchTrack),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF2F2F2),
                tonalElevation = 0.dp
            ) {
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            if (species.isBlank() || quantity.isBlank() || time.isBlank() || location.isBlank() || location == locUnavailable) {
                                validationWarning = null
                                showWarningDialog = true
                            } else {
                                onActualSubmit()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(50.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.addEntry), fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    }
                }
            }
        },
        containerColor = Color.White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F2F2))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Species Searchable Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = species,
                                onValueChange = { 
                                    species = it
                                    expanded = true
                                },
                                label = { Text(stringResource(R.string.species)) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                )
                            )
                        }
                        
                        DropdownMenu(
                            expanded = expanded && filteredOptions.isNotEmpty(),
                            onDismissRequest = { expanded = false },
                            properties = PopupProperties(focusable = false),
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .heightIn(max = 240.dp)
                        ) {
                            filteredOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        species = option
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Quantity
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { 
                            if (it.all { char -> char.isDigit() }) {
                                quantity = it 
                            }
                        },
                        label = { Text(stringResource(R.string.quantity)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    // Time
                    Column {
                        OutlinedTextField(
                            value = time,
                            onValueChange = { time = it },
                            label = { Text(stringResource(R.string.time)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Text(
                            stringResource(R.string.timeDefault),
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }

                    // Location
                    Column {
                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text(stringResource(R.string.location)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            trailingIcon = {
                                IconButton(onClick = { showMapPicker = true }) {
                                    Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.select_location))
                                }
                            }
                        )
                        Text(
                            stringResource(R.string.locationDefault),
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                }
            }
        }
    }

    if (showMapPicker) {
        val arcGISMap = remember { ArcGISMap(BasemapStyle.ArcGISTopographic) }
        var currentCenter by remember { mutableStateOf<Point?>(null) }
        val lifecycleOwner = LocalLifecycleOwner.current
        
        // Default viewpoint for BC Coast
        val bcCoastCenter = remember { Point(-125.0, 52.0, SpatialReference.wgs84()) }
        val defaultScale = 4000000.0

        val mapView = remember { 
            MapView(context).apply {
                // The crash "lateinit property lifeCycleOwner has not been initialized"
                // suggests we need to ensure the MapView is lifecycle aware early.
            }
        }

        DisposableEffect(lifecycleOwner, mapView) {
            lifecycleOwner.lifecycle.addObserver(mapView)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(mapView)
            }
        }

        LaunchedEffect(mapView, location) {
            // Try to parse current location to set initial view
            val parts = location.split(",").map { it.trim().toDoubleOrNull() }
            if (parts.size == 2 && parts[0] != null && parts[1] != null) {
                val startPoint = Point(parts[1]!!, parts[0]!!, SpatialReference.wgs84())
                mapView.setViewpointCenter(startPoint, 50000.0)
            } else {
                // Default to BC Coast if no valid location is set
                mapView.setViewpointCenter(bcCoastCenter, defaultScale)
            }
        }

        Dialog(
            onDismissRequest = { showMapPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { 
                            mapView.apply {
                                map = arcGISMap
                                
                                scope.launch {
                                    viewpointChanged.collect {
                                        val center = screenToLocation(ScreenCoordinate(width / 2.0, height / 2.0))
                                        currentCenter = center
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Crosshair
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .align(Alignment.Center)
                            .border(2.dp, Color.Red, RoundedCornerShape(20.dp))
                    ) {
                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.Center)
                                .padding(horizontal = 10.dp),
                            color = Color.Red,
                            thickness = 2.dp
                        )
                        VerticalDivider(
                            modifier = Modifier
                                .fillMaxHeight()
                                .align(Alignment.Center)
                                .width(2.dp)
                                .padding(vertical = 10.dp),
                            color = Color.Red,
                            thickness = 2.dp
                        )
                    }

                    // Controls
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 16.dp, end = 16.dp)
                    ) {
                        FloatingActionButton(
                            onClick = { scope.launch { mapView.setViewpointScale(scale = mapView.mapScale.value * 0.5) } },
                            modifier = Modifier.padding(bottom = 8.dp),
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Zoom In")
                        }
                        FloatingActionButton(
                            onClick = { scope.launch { mapView.setViewpointScale(scale = mapView.mapScale.value * 2.0) } },
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { showMapPicker = false },
                            modifier = Modifier.background(Color.White, RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }

                        Button(
                            onClick = {
                                currentCenter?.let { point ->
                                    val wgs84Point = GeometryEngine.projectOrNull(point, SpatialReference.wgs84()) as? Point
                                    wgs84Point?.let { 
                                        location = String.format(Locale.US, "%.6f, %.6f", it.y, it.x)
                                    }
                                }
                                showMapPicker = false
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.select_location))
                        }
                    }
                }
            }
        }
    }
}
