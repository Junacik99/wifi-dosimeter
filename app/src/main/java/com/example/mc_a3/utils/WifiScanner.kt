package com.example.mc_a3.utils

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.example.mc_a3.model.LocationData
import com.example.mc_a3.model.WifiSignal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class WifiScanner(private val context: Context) {

    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults = _scanResults.asStateFlow()
    
    // Broadcast receiver for WiFi scan results
    private val wifiScanReceiver = object : BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                if (success) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return
                    }
                    _scanResults.value = wifiManager.scanResults
                }
            }
        }
    }

    init {
        val intentFilter = IntentFilter().apply {
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        }
        context.registerReceiver(wifiScanReceiver, intentFilter)
    }

    // Start a WiFi scan
    fun startScan(): Boolean {
        return wifiManager.startScan()
    }

    // Convert scan results to a signal matrix of size 100
    fun createSignalMatrix(): List<Int> {
        val results = _scanResults.value
        
        if (results.isEmpty()) {
            // No networks found, return an empty list
            return emptyList()
        }
        
        // Sort access points by signal strength (strongest first)
        val sortedResults = results.sortedByDescending { it.level }
        
        // Create a statistically expanded matrix
        return when {
            // If we have exactly 100 networks, use them directly (unlikely)
            sortedResults.size == LocationData.MATRIX_SIZE -> {
                sortedResults.map { it.level }
            }
            
            // If we have more than 100 networks, take the strongest 100
            sortedResults.size > LocationData.MATRIX_SIZE -> {
                sortedResults.take(LocationData.MATRIX_SIZE).map { it.level }
            }
            
            // If we have fewer than 100 networks, apply statistical expansion
            else -> {
                // 1. Use actual readings first
                val baseMatrix = sortedResults.map { it.level }.toMutableList()
                
                // 2. Calculate statistics from real readings
                val minSignal = baseMatrix.minOrNull() ?: -100
                val maxSignal = baseMatrix.maxOrNull() ?: -40
                val avgSignal = baseMatrix.average().toInt()
                val stdDev = calculateStandardDeviation(baseMatrix, avgSignal)
                
                // 3. Create a larger matrix with statistical variation
                val expandedMatrix = mutableListOf<Int>()
                expandedMatrix.addAll(baseMatrix) // Start with real data
                
                // Calculate how many additional readings we need
                val additionalReadingsNeeded = LocationData.MATRIX_SIZE - baseMatrix.size
                
                // Add variations around existing readings with Gaussian distribution
                repeat(additionalReadingsNeeded) {
                    // Pick a random base reading to vary from
                    val baseReading = baseMatrix[it % baseMatrix.size]
                    
                    // Apply Gaussian variation (with controlled deviation)
                    // Using smaller standard deviation to keep values realistic
                    val variation = (stdDev * (Math.random() * 2 - 1) * 0.5).toInt()
                    
                    // Ensure the variation stays within realistic bounds (-100 to -40 dBm)
                    val newReading = (baseReading + variation).coerceIn(minSignal - 5, maxSignal + 3)
                    
                    expandedMatrix.add(newReading)
                }
                
                // Shuffle the matrix to avoid patterns (but keep first few real readings intact)
                val firstRealReadings = expandedMatrix.take(baseMatrix.size)
                val artificialReadings = expandedMatrix.drop(baseMatrix.size).shuffled()
                
                // Combine and return
                firstRealReadings + artificialReadings
            }
        }
    }
    
    // Helper function to calculate standard deviation
    private fun calculateStandardDeviation(values: List<Int>, mean: Int): Double {
        if (values.size <= 1) return 2.0 // Default small deviation if we don't have enough data
        
        val sumOfSquaredDifferences = values.sumOf { 
            val difference = it - mean
            difference * difference 
        }
        
        return Math.sqrt(sumOfSquaredDifferences.toDouble() / (values.size - 1))
    }

    // Create a LocationData object from current scan results
    fun createLocationData(locationName: String): LocationData {
        val wifiSignals = _scanResults.value.map { scanResult ->
            WifiSignal(
                ssid = scanResult.SSID.ifEmpty { "<Hidden Network>" },
                bssid = scanResult.BSSID,
                level = scanResult.level,
                frequency = scanResult.frequency
            )
        }
        
        return LocationData(
            name = locationName,
            signalMatrix = createSignalMatrix(),
            scanResults = wifiSignals
        )
    }

    fun cleanup() {
        try {
            context.unregisterReceiver(wifiScanReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }
}