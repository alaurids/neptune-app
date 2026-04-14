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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
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
        SimpleDateFormat("MM/dd/yyyy hh:mm a z", Locale.getDefault()).format(java.util.Date())
    }
    var time by remember { mutableStateOf(if (initialTime.isEmpty()) defaultTime else initialTime) }
    var location by remember { mutableStateOf(if (initialLocation.isEmpty()) "Fetching location..." else initialLocation) }
    var permissionGranted by remember { 
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) 
    }

    var showWarningDialog by remember { mutableStateOf(false) }

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
            repository.upsertCatchEntry(species, quantity, time, location, entryId)
            onSubmitClick()
        }
    }

    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
            title = { Text("Missing Information") },
            text = { Text("Some fields are empty. Do you want to continue anyway?") },
            confirmButton = {
                TextButton(onClick = {
                    showWarningDialog = false
                    onActualSubmit()
                }) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWarningDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionGranted = isGranted
        if (!isGranted) {
            location = "Permission denied"
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
                    location = "Location unavailable"
                }
            }.addOnFailureListener {
                location = "Error fetching location"
            }
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "Catch Tracking",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                            if (species.isBlank() || quantity.isBlank() || time.isBlank() || location.isBlank() || location == "Fetching location...") {
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
                        Text("Add entry", fontWeight = FontWeight.Medium, fontSize = 16.sp)
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
                                label = { Text("Species") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .border(1.dp, Color.Black, RoundedCornerShape(50))
                                    .background(Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(onClick = onCameraClick) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = "Take Photo", tint = Color.Black)
                                }
                            }
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
                        label = { Text("Quantity") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    // Time
                    Column {
                        OutlinedTextField(
                            value = time,
                            onValueChange = { time = it },
                            label = { Text("Time") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Text(
                            "Defaults to current time. Click to change.",
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
                            label = { Text("Location") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Text(
                            "Defaults to current coordinates.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
