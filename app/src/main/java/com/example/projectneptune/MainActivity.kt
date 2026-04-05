package com.example.projectneptune

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.arcgismaps.ApiKey
import com.arcgismaps.ArcGISEnvironment
import com.example.projectneptune.data.MapRepository
import com.example.projectneptune.ui.theme.ProjectNeptuneTheme
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        // Setup a global crash logger
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("NeptuneCrash", "FATAL EXCEPTION on thread ${thread.name}", throwable)
            oldHandler?.uncaughtException(thread, throwable)
        }

        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize ArcGIS globally ONCE to prevent screen flickering
        ArcGISEnvironment.apiKey = ApiKey.create("AAPTaOQ2Het8GgGYJ4ajqkioREQ..GEcDIHKHM7r29WIN3X-bCFCm4yQzF6bMoT-8yRa8HXeIOX9dY9_ArNVSaLWDD1dT8lqJdXrIq8ITfWHMlP3phHWWaM_CeJteyA-xY4CD_pcLznnGTzPiVRdl4Q7B0vnyUV4cqRNfR-YQdm3t-2lYfPiILHZdhFfgIbCB5ImH_U3tvLpVmBbTQZXXJBbgZa0WtCXC457qvsiZEePpeFqMSm8qCD6j4Z9ahpu01-fQ-O8IGubcUHbkqjU.AT1_Gyt8djcX")
        ArcGISEnvironment.applicationContext = applicationContext

        cameraExecutor = Executors.newSingleThreadExecutor()
        setContent {
            ProjectNeptuneTheme {
                ProjectNeptuneApp(cameraExecutor)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

@PreviewScreenSizes
@Composable
fun ProjectNeptuneApp(cameraExecutor: ExecutorService? = null) {
    val context = LocalContext.current
    val mapRepository = remember { MapRepository(context) }
    val appScope = rememberCoroutineScope()
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.REFERENCE_GUIDE) }

    LaunchedEffect(Unit) {
        // Initial sync
        mapRepository.fetchAndCacheLayer20Data()
        
        // Auto-sync when internet returns
        observeInternetConnectivity(context).collect { isAvailable ->
            if (isAvailable) {
                Log.d("NeptuneDebug", "Internet restored. Checking for updates...")
                mapRepository.fetchAndCacheLayer20Data()
            }
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        when (val icon = it.icon) {
                            is ImageVector -> Icon(icon, contentDescription = it.label)
                            is Int -> Icon(painterResource(icon), contentDescription = it.label)
                        }
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        when (currentDestination) {
            AppDestinations.CAMERA -> CameraDestination(cameraExecutor!!)
            AppDestinations.CATCH_LOG -> CatchLogDestination()
            AppDestinations.MAP -> MapDestination(
                repository = mapRepository
            )
            AppDestinations.REFERENCE_GUIDE -> ReferenceGuideDestination()
            AppDestinations.SETTINGS -> SettingsDestination(
                repository = mapRepository,
                onForceSync = {
                    appScope.launch {
                        mapRepository.fetchAndCacheLayer20Data(force = true)
                    }
                }
            )
        }
    }
}

enum class AppDestinations(val label: String, val icon: Any) {
    CAMERA("Camera", Icons.Default.CameraAlt),
    CATCH_LOG("Catch Log", Icons.Default.AutoStories),
    MAP("Map", Icons.Default.Map),
    REFERENCE_GUIDE("Reference", R.drawable.reference_icon),
    SETTINGS("Settings", Icons.Default.Settings),
}
