package com.potheride.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potheride.app.ui.PotheRideViewModel
import com.potheride.app.ui.navigation.PotheNavHost
import com.potheride.app.ui.theme.PotheRideTheme

/**
 * Single-activity host. Everything above this is Compose.
 *
 * Permissions are requested from here rather than from a composable because the
 * result contract needs an Activity, and asking on launch (before the user has any
 * reason to say yes) is the fastest way to get a permanent denial. The driver screen
 * triggers the request at the moment location actually becomes useful.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: PotheRideViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            viewModel.startRealTracking()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            PotheRideTheme(language = state.language) {
                Surface(Modifier.fillMaxSize()) {
                    PotheNavHost(
                        vm = viewModel,
                        onRequestLocationPermission = { requestLocationPermissions() }
                    )
                }
            }
        }
    }

    private fun requestLocationPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
}
