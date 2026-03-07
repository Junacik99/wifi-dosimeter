package com.example.mc_a3

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mc_a3.ui.theme.MC_A3Theme
import com.example.mc_a3.viewmodel.WifiViewModel
import kotlinx.coroutines.delay

class DosimeterActivity : ComponentActivity() {
    private val viewModel: WifiViewModel by viewModels()

    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            viewModel.startScan()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestPermissions()

        setContent {
            MC_A3Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DosimeterApp(viewModel)
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DosimeterApp(
    viewModel: WifiViewModel = viewModel()
) {
    val scanResults by viewModel.scanResults.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    
    // State to control periodic scanning
    var isAutoScanning by remember { mutableStateOf(false) }

    // ADJUSTABLE DELAY: Internal variable for scan frequency
    val scanIntervalMs = 5000L

    // Periodic Timer Logic
    LaunchedEffect(isAutoScanning) {
        if (isAutoScanning) {
            while (true) {
                if (!isScanning) {
                    viewModel.startScan()
                }
                delay(scanIntervalMs)
            }
        }
    }

    // Memoize strongest AP for performance
    val strongestAp = remember(scanResults) {
        scanResults.maxByOrNull { it.level }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "WiFi Dosimeter",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // Status indicator
        Text(
            text = when {
                isScanning -> "Refreshing..."
                isAutoScanning -> "Live Monitoring"
                else -> "Stopped"
            },
            style = MaterialTheme.typography.labelMedium,
            color = when {
                isScanning -> MaterialTheme.colorScheme.secondary
                isAutoScanning -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            },
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = strongestAp?.let { "${it.level} dBm" } ?: "---",
            fontSize = 64.sp,
            style = MaterialTheme.typography.displayLarge,
            color = if (isAutoScanning || isScanning) MaterialTheme.colorScheme.onSurface 
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        strongestAp?.let { ap ->
            val ssid = ap.SSID.ifEmpty { "Hidden Network" }.removeSurrounding("\"")

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "SSID: $ssid", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "BSSID: ${ap.BSSID}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Start/Stop Toggle Button
        Button(
            onClick = { isAutoScanning = !isAutoScanning },
            modifier = Modifier.width(200.dp),
            colors = if (isAutoScanning) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.buttonColors()
            }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isAutoScanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isAutoScanning) "Stop Tracking" else "Start Tracking")
            }
        }
        
        // Manual Scan Button
        if (!isAutoScanning) {
            TextButton(
                onClick = { viewModel.startScan() },
                enabled = !isScanning,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Manual Scan")
            }
        }
    }
}
