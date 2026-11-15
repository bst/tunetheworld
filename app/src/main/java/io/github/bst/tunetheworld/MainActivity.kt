package io.github.bst.tunetheworld

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import io.github.bst.tunetheworld.ui.PermissionScreen
import io.github.bst.tunetheworld.ui.TunerScreen

// AppCompatActivity (not ComponentActivity) so per-app language switching works below Android 13
class MainActivity : AppCompatActivity() {
    private var micGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        micGranted = hasMicPermission()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface {
                    if (micGranted) TunerScreen() else PermissionScreen()
                }
            }
        }
    }

    // Also covers returning from the system settings screen
    override fun onResume() {
        super.onResume()
        micGranted = hasMicPermission()
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}
