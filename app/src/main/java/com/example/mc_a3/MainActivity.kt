package com.example.mc_a3

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalContext
import com.example.mc_a3.ui.theme.MC_A3Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MC_A3Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainMenu(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MainMenu(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var useSsid by remember { mutableStateOf(false) }
    var ssidText by remember { mutableStateOf("") }

    val chernobylFont = FontFamily(
        Font(R.font.chernobyl)
    )

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "DOSIMETER",
            fontFamily = chernobylFont,
            fontSize = 50.sp,
            modifier = modifier.padding(top = 100.dp, bottom = 70.dp)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ){
                Text(
                    text = "Use SSID",
                    fontFamily = chernobylFont,
                    fontSize = 20.sp,
                )
                Spacer(modifier = Modifier.width(20.dp))
                Switch(
                    checked = useSsid,
                    onCheckedChange = { useSsid = it },
                )
            }

            if (useSsid) {
                OutlinedTextField(
                    value = ssidText,
                    onValueChange = { ssidText = it },
                    label = { Text("Target SSID", fontFamily = chernobylFont) },
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
        Button(
            onClick = {
                val intent = Intent(context, DosimeterActivity::class.java).apply {
                    putExtra("USE_SSID", useSsid)
                    putExtra("TARGET_SSID", ssidText)
                }
                context.startActivity(intent)
            },
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Text("START", fontFamily = chernobylFont, fontSize = 24.sp)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MC_A3Theme {
        MainMenu()
    }
}