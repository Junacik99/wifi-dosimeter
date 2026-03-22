package com.example.mc_a3

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import kotlin.random.Random
import kotlin.text.format
import java.util.Locale

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

        val useSsid = intent.getBooleanExtra("USE_SSID", false)
        val targetSsid = intent.getStringExtra("TARGET_SSID") ?: ""

        setContent {
            MC_A3Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DosimeterApp(
                        viewModel,
                        useSsid,
                        targetSsid
                    )
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
    viewModel: WifiViewModel = viewModel(),
    useSsid: Boolean,
    targetSsid: String
) {
    val scanResults by viewModel.scanResults.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    
    // State to control periodic scanning
    var isAutoScanning by remember { mutableStateOf(true) }

    // ADJUSTABLE DELAY: Internal variable for scan frequency
    val scanIntervalMs = 5000L

    // Bool if media player is playing
    var isPlaying by remember { mutableStateOf(false) }

    // State for random signal fluctuation
    var levelOffset by remember { mutableFloatStateOf(0f) }
    val randomAddInterval = 500L
    val randomAddMin = -2f
    val randomAddMax = 2f




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

    // High-frequency fluctuation timer (every 0.5s)
    LaunchedEffect(isAutoScanning) {
        if (isAutoScanning){
            while(true){
                levelOffset = randomAddMin + (Random.nextFloat() * (randomAddMax - randomAddMin))
                delay(randomAddInterval)
            }
        }
        else{
            levelOffset = 0f
        }
    }

    // Memoize the strongest AP for performance
    val strongestAp = remember(scanResults) {
        if (useSsid)
            scanResults
                .filter { it.wifiSsid.toString() == targetSsid || it.wifiSsid.toString() == "\"$targetSsid\"" }.maxByOrNull { it.level }
        else
            scanResults.maxByOrNull { it.level }
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

    val rawDosimetry = remember(strongestAp) {
        strongestAp?.let {
            convertDbmToDosimetry(
                dbm = it.level,
                minDbm = minIntensity,
                maxDbm = maxIntensity,
            )
        }
    }
    val displayDosimetry = rawDosimetry?.let { it + levelOffset }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
        ){
        Box(modifier = Modifier
            .fillMaxWidth()
            .padding(top = 50.dp)) {
            Image(
                painter = painterResource(id = R.drawable.dosimeter),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = displayDosimetry?.let { String.format(Locale.US, "%.2f", it) } ?: "----",
                fontSize = 80.sp,
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 50.dp, end = 45.dp),
                textAlign = TextAlign.Right,
                fontFamily = digitalFont,
                color = Color.Black
            )
        }


        // Column(
        //     modifier = Modifier
        //         .fillMaxSize()
        //         .padding(24.dp),
        //     horizontalAlignment = Alignment.CenterHorizontally,
        //     verticalArrangement = Arrangement.Center
        //
        // ) {
        //
        //
        //     Text(
        //         text = displayDosimetry?.let { String.format(Locale.US, "%.2f", it) } ?: "----",
        //         fontSize = 80.sp,
        //         style = MaterialTheme.typography.displayLarge,
        //         modifier = Modifier
        //             .padding(top = 75.dp, end = 40.dp)
        //             .align(Alignment.End),
        //         textAlign = TextAlign.Right,
        //         fontFamily = digitalFont,
        //         color = Color.Black
        //     )
        //
        // }
    }


}
