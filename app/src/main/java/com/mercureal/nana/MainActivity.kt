package com.mercureal.nana

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Start button checks again */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NanaScreen(
                        onStart = { if (ensurePermissions()) NanaService.start(this) },
                        onStop = { NanaService.stop(this) },
                        onOpenAccessibility = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onOpenAppSettings = {
                            startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:$packageName"))
                            )
                        }
                    )
                }
            }
        }
    }

    /** Requests runtime permissions NaNa needs. Returns true only if the mic is granted. */
    private fun ensurePermissions(): Boolean {
        val needed = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.POST_NOTIFICATIONS
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) requestPermissions.launch(missing.toTypedArray())
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun NanaScreen(
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val listening by NanaState.listening.collectAsState()
    val status by NanaState.status.collectAsState()
    val log by NanaState.log.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("NaNa", style = MaterialTheme.typography.headlineLarge)
        Text("Offline voice control. Say \"nana\", then a command.", style = MaterialTheme.typography.bodyMedium)
        Text("Status: $status", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onStart, enabled = !listening) { Text("Start listening") }
            OutlinedButton(onClick = onStop, enabled = listening) { Text("Stop") }
        }

        Text(
            "Reading and tapping the screen need the accessibility service. Turn NaNa on there once.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onOpenAccessibility) { Text("Accessibility") }
            OutlinedButton(onClick = onOpenAppSettings) { Text("App permissions") }
        }

        Text(
            "Try: \"nana open camera\", \"nana press back\", \"nana read screen\", \"nana call 0801...\"",
            style = MaterialTheme.typography.bodySmall
        )

        Text("Activity", style = MaterialTheme.typography.titleSmall)
        LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(log.reversed()) { line -> Text(line, fontSize = 14.sp) }
        }
    }
}
