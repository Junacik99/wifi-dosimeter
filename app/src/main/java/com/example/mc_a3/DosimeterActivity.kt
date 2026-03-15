package com.example.mc_a3

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mc_a3.ui.theme.MC_A3Theme
import com.example.mc_a3.viewmodel.WifiViewModel
import kotlinx.coroutines.delay

private var mediaPlayer: MediaPlayer? = null

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

        // Init media player for sound effects
        mediaPlayer = MediaPlayer.create(this, R.raw.dosimeter_sound)
        mediaPlayer?.isLooping = true

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

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

fun convertDbmToDosimetry(
    dbm: Int,
    minDbm: Int,
    maxDbm: Int,
    minDosimetry: Int = 0,
    maxDosimetry: Int = 192)
: Int {
    // Scales value from one range to another
    return ((dbm - minDbm) * (maxDosimetry - minDosimetry) / (maxDbm - minDbm) + minDosimetry)
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

    // Bool if mediaplayer is playing
    var isPlaying by remember { mutableStateOf(false) }


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
        // TODO:
        // Change back to this, potentionally combinde with ssid
        // scanResults.maxByOrNull { it.level }
        val targetSsid = "MartinNET"

        scanResults
            .filter { it.wifiSsid.toString() == targetSsid || it.wifiSsid.toString() == "\"$targetSsid\"" }.maxByOrNull { it.level }

    }

    val minIntensity = -80
    val maxIntensity = -10
    val minSpeed = 0.1f
    val maxSpeed = 1.5f
    // Play sound effect when strongest AP, auto-scanning state or scanning state changes
    LaunchedEffect(strongestAp, isAutoScanning, isScanning) {
        val level = strongestAp?.level
        // Lowered threshold to -90 to match your speed requirements
        val shouldPlay = level != null && level >= minIntensity && (isAutoScanning || isScanning)
        
        if (shouldPlay) {
            // Calculate playback speed based on signal intensity
            // Range: -90 dBm (0.7 speed) to -30 dBm (2.0 speed)
            val clampedLevel = level.coerceIn(minIntensity, maxIntensity)
            // Normalizing -90..-30 to 0..1 range: (level - (-90)) / (-30 - (-90)) = (level + 90) / 60
            val normalizedLevel = (clampedLevel - minIntensity.toFloat()) / (maxIntensity - minIntensity).toFloat()
            // Map 0..1 range to 0.7..2.0 range: 0.7 + (normalized * (2.0 - 0.7))
            val speed = minSpeed + (normalizedLevel * (maxSpeed - minSpeed))
            
            try {
                mediaPlayer?.let {
                    // Update playback speed if supported (API 23+)
                    it.playbackParams = it.playbackParams.setSpeed(speed)
                }
            } catch (e: Exception) {
                // Some devices/states might not support dynamic speed changes
            }

            if (!isPlaying) {
                mediaPlayer?.start()
                isPlaying = true
            }
        } else {
            if (isPlaying) {
                mediaPlayer?.pause()
                isPlaying = false
            }
        }
    }

    val digitalFont = FontFamily(
        Font(R.font.digital)
    )

    Box(modifier = Modifier.fillMaxSize()){
        Image(
            painter = painterResource(id = R.drawable.dosimeter),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().padding(top = 50.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            Text(
                text = strongestAp?.let { "${convertDbmToDosimetry(
                    dbm = it.level,
                    minDbm = minIntensity,
                    maxDbm = maxIntensity,
                    )}" } ?: "----",
                fontSize = 80.sp,
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier
                    .padding(top = 75.dp, end = 40.dp)
                    .align(Alignment.End),
                textAlign = TextAlign.Right,
                fontFamily = digitalFont,
                color = Color.Black
            )



            Spacer(modifier = Modifier.height(450.dp))

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
            
        }
    }


}
