package com.example.mc_a3

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mc_a3.model.LocationData
import com.example.mc_a3.ui.components.SignalMatrixVisualization
import com.example.mc_a3.ui.theme.MC_A3Theme
import com.example.mc_a3.viewmodel.WifiViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

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
            // Initialize scanning if permissions are granted
            val viewModel: WifiViewModel by viewModels()
            viewModel.startScan()
        } else {
            // Handle permission denial
            // In a real app, you'd show a dialog explaining why permissions are needed
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Check and request permissions
        checkAndRequestPermissions()
        
        setContent {
            MC_A3Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WifiSignalApp()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiSignalApp(
    viewModel: WifiViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentLocationName by viewModel.currentLocationName.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val locationData by viewModel.locationData.collectAsState()
    
    val predefinedLocations = viewModel.getPredefinedLocations()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("WiFi Signal Strength Scanner") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            // Location selection
            Text(
                text = "Select Location",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                predefinedLocations.forEach { location ->
                    Button(
                        onClick = { viewModel.setCurrentLocation(location) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentLocationName == location) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(location.split(" ").last())
                    }
                }
            }
            
            // Single button for scanning and capturing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.scanAndCaptureData()
                    },
                    enabled = !isScanning && currentLocationName != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isScanning) "Processing..." else "Record WiFi Fingerprint")
                }
            }
            
            // Clear button with Compose dialog
            var showClearConfirmDialog by remember { mutableStateOf(false) }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showClearConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    enabled = locationData.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear All Data")
                }
            }
            
            // Confirmation dialog
            if (showClearConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showClearConfirmDialog = false },
                    title = { Text("Clear All Data") },
                    text = { 
                        Text("Are you sure you want to clear all the location data?") 
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.clearAllLocationData()
                                scope.launch {
                                    snackbarHostState.showSnackbar("All the location data has been cleared")
                                }
                                showClearConfirmDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Clear")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showClearConfirmDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            
            // Location Data Visualizations
            if (locationData.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    Text(
                        text = "No data captured yet. Select a location, scan WiFi networks, and capture data.",
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    text = "Comparison of WiFi Signal Strengths Across Locations",
                    style = MaterialTheme.typography.headlineMedium,  // Upgraded from headlineSmall to headlineMedium
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                
                // Display each location in a separate card
                // Keep track of which card is expanded
                var expandedCardIndex by remember { mutableStateOf<Int?>(null) }
                
                locationData.values.forEachIndexed { index, data ->
                    // Use a lighter surface color for better visibility in dark mode
                    val cardBackgroundColor = if (isSystemInDarkTheme()) {
                        // Use a lighter gray in dark mode for better contrast
                        Color(0xFF2C2C2C)
                    } else {
                        // In light mode, use the default surface color (white)
                        MaterialTheme.colorScheme.surface
                    }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .clickable {
                                // Toggle expanded state when card is clicked
                                if (expandedCardIndex == index) {
                                    expandedCardIndex = null // collapse if already expanded
                                } else {
                                    expandedCardIndex = index // expand this card
                                    
                                    // Log the matrix to Logcat
                                    val logTag = "WifiMatrix"
                                    Log.d(logTag, "Matrix for ${data.name}:")
                                    val matrixString = formatMatrixForLog(data.signalMatrix)
                                    Log.d(logTag, matrixString)
                                }
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        ) {
                            SignalMatrixVisualization(
                                locationData = data,
                                barColor = getColorForLocation(data),
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            // Always display WiFi access points detected at this location
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Detected WiFi Access Points:",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            if (data.scanResults.isEmpty()) {
                                Text(
                                    text = "No WiFi networks detected",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    data.scanResults.forEachIndexed { apIndex, wifiSignal ->
                                        Text(
                                            text = "${apIndex + 1}. ${wifiSignal.ssid} (${wifiSignal.bssid})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                        Text(
                                            text = "    Signal: ${wifiSignal.level} dBm, Frequency: ${wifiSignal.frequency} MHz",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                    }
                                }
                            }
                            
                            // Show matrix data only when expanded
                            if (expandedCardIndex == index) {
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Text(
                                    text = "Signal Matrix (100 elements):",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                // Display the matrix data in a scrollable column
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                ) {
                                    Text(
                                        text = formatMatrixForDisplay(data.signalMatrix),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (locationData.size >= 2) {
                    // Show statistics across locations
                    val stats = calculateStatsBetweenLocations(locationData.values.toList())
                    
                    Text(
                        text = "Cross-Location Statistics",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    // Display cross-location stats directly without a card
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth()
                    ) {
                        // General statistics section
                        Text(
                            text = "General Signal Differences",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Text(
                            text = "Average difference between locations: ${stats.averageDifference} dBm",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        
                        Text(
                            text = "Max difference between locations: ${stats.maxDifference} dBm",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Divider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        
                        // Per-access point differences
                        Text(
                            text = "Per-Access Point Analysis",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                        )
                        
                        if (stats.accessPointStats.isEmpty()) {
                            Text(
                                text = "No common access points found between locations",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            // Create a card for each access point
                            stats.accessPointStats.forEach { apStat ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    ) {
                                        // Show AP name and presence information
                                        Text(
                                            text = "${apStat.ssid} (${apStat.bssid})",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        
                                        if (apStat.presentInLocations.size < locationData.size) {
                                            // Show which locations this AP appears in
                                            Text(
                                                text = "Present in: ${apStat.presentInLocations.joinToString(", ")}",
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(top = 4.dp),
                                                color = if (apStat.presentInLocations.size == 1) 
                                                    MaterialTheme.colorScheme.error 
                                                else 
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            Text(
                                                text = "Present in all locations",
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(top = 4.dp),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        
                                        // Show signal difference if present in multiple locations
                                        if (apStat.presentInLocations.size >= 2) {
                                            Text(
                                                text = "Maximum signal difference: ${apStat.signalDifference} dBm",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(top = 8.dp)
                                            )
                                            
                                            Text(
                                                text = "Average signal difference: ${apStat.averageSignalDifference} dBm",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                            )
                                            
                                            // Show signal strength at each location
                                            Column(modifier = Modifier.padding(top = 4.dp)) {
                                                apStat.signalLevels.forEach { (location, level) ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = location,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = if (location == apStat.bestSignalLocation) 
                                                                    Color(0xFF4CAF50) // Green for best signal
                                                                else 
                                                                    MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = "$level dBm",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = if (location == apStat.bestSignalLocation) 
                                                                    Color(0xFF4CAF50) // Green for best signal
                                                                else 
                                                                    MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            Text(
                                                text = "Signal strength: ${apStat.signalLevels.values.first()} dBm",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(top = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getColorForLocation(locationData: LocationData): Color {
    // Extract the location number from the location name
    val locationName = locationData.name
    val locationNumber = locationName.split(" ").lastOrNull()?.toIntOrNull()
    
    return when (locationNumber) {
        1 -> Color(0xFF1976D2) // Blue for Location 1
        2 -> Color(0xFFF44336) // Red for Location 2
        3 -> Color(0xFF4CAF50) // Green for Location 3
        else -> Color(0xFF9C27B0) // Purple for any other location
    }
}

private data class LocationStats(
    val averageDifference: Int,
    val maxDifference: Int,
    val accessPointStats: List<AccessPointStat>
)

private data class AccessPointStat(
    val bssid: String,
    val ssid: String,
    val presentInLocations: List<String>,
    val signalDifference: Int,
    val averageSignalDifference: Int,
    val signalLevels: Map<String, Int>,
    val bestSignalLocation: String?
)

private fun calculateStatsBetweenLocations(locations: List<LocationData>): LocationStats {
    if (locations.size < 2) {
        return LocationStats(0, 0, emptyList())
    }
    
    // Calculate average signal strength for each location
    val locationAverages = locations.map { locationData ->
        val avgSignal = if (locationData.signalMatrix.isNotEmpty()) {
            locationData.signalMatrix.average().toInt()
        } else {
            0
        }
        Pair(locationData.name, avgSignal)
    }
    
    val diffs = mutableListOf<Int>()
    
    // Compare average signal strengths between locations
    for (i in 0 until locationAverages.size - 1) {
        for (j in i + 1 until locationAverages.size) {
            val avgDiff = Math.abs(locationAverages[i].second - locationAverages[j].second)
            diffs.add(avgDiff)
        }
    }
    
    val avgDifference = if (diffs.isNotEmpty()) diffs.average().toInt() else 0
    val maxDifference = diffs.maxOrNull() ?: 0
    
    // Calculate per-access point differences
    val accessPointsByBssid = mutableMapOf<String, MutableMap<String, Int>>()
    val accessPointSsids = mutableMapOf<String, String>()
    
    // Group access points by BSSID across all locations
    locations.forEach { locationData ->
        val locationName = locationData.name
        locationData.scanResults.forEach { signal ->
            // Store the signal level for this location
            accessPointsByBssid
                .getOrPut(signal.bssid) { mutableMapOf() }
                .put(locationName, signal.level)
            
            // Store the SSID (we'll use the first non-empty one we find)
            if (signal.ssid != "<Hidden Network>") {
                accessPointSsids[signal.bssid] = signal.ssid
            } else if (!accessPointSsids.containsKey(signal.bssid)) {
                accessPointSsids[signal.bssid] = signal.ssid
            }
        }
    }
    
    // Calculate stats for each access point
    val accessPointStats = accessPointsByBssid.map { (bssid, levelsByLocation) ->
        // Calculate the maximum difference in signal strength across locations
        val levels = levelsByLocation.values.toList()
        val signalDifference = if (levels.size >= 2) {
            levels.maxOrNull()!! - levels.minOrNull()!!
        } else {
            0 // If only present in one location, difference is 0
        }
        
        // Calculate the average signal difference
        val pairwiseDiffs = mutableListOf<Int>()
        if (levels.size >= 2) {
            for (i in 0 until levels.size - 1) {
                for (j in i + 1 until levels.size) {
                    pairwiseDiffs.add(Math.abs(levels[i] - levels[j]))
                }
            }
        }
        
        val averageSignalDifference = if (pairwiseDiffs.isNotEmpty()) {
            pairwiseDiffs.average().toInt()
        } else {
            0
        }
        
        // Determine the location with the best signal
        val bestSignalLocation = levelsByLocation.maxByOrNull { it.value }?.key
        
        AccessPointStat(
            bssid = bssid,
            ssid = accessPointSsids[bssid] ?: "<Unknown>",
            presentInLocations = levelsByLocation.keys.toList(),
            signalDifference = signalDifference,
            averageSignalDifference = averageSignalDifference,
            signalLevels = levelsByLocation,
            bestSignalLocation = bestSignalLocation
        )
    }.sortedByDescending { it.signalDifference } // Sort by largest difference first
    
    return LocationStats(
        averageDifference = avgDifference,
        maxDifference = maxDifference,
        accessPointStats = accessPointStats
    )
}

/**
 * Formats the signal matrix for display in the UI
 * Creates a multi-line string with 10 values per line
 */
private fun formatMatrixForDisplay(matrix: List<Int>): String {
    if (matrix.isEmpty()) return "No signal data available"
    
    val builder = StringBuilder()
    matrix.forEachIndexed { index, value ->
        builder.append("$value dBm")
        // Add comma except for last element or end of row
        if (index < matrix.size - 1) {
            builder.append(", ")
        }
        // Create a new line after every 10 elements
        if ((index + 1) % 10 == 0) {
            builder.append("\n")
        }
    }
    return builder.toString()
}

/**
 * Formats the signal matrix for logging to Logcat
 * Creates a compact representation for easier reading in logs
 */
private fun formatMatrixForLog(matrix: List<Int>): String {
    if (matrix.isEmpty()) return "Empty matrix"
    
    val builder = StringBuilder()
    builder.append("[\n")
    matrix.forEachIndexed { index, value ->
        // Add a tab for each row
        if (index % 10 == 0) {
            builder.append("\t")
        }
        builder.append("$value")
        // Add separator except for last element
        if (index < matrix.size - 1) {
            builder.append(", ")
        }
        // Create a new line after every 10 elements
        if ((index + 1) % 10 == 0) {
            builder.append("\n")
        }
    }
    builder.append("\n]")
    return builder.toString()
}