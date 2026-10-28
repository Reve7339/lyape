package com.lyape

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lyape.ui.LyapeApp
import com.lyape.ui.navigation.NavigationRequests
import com.lyape.ui.theme.LyapeTheme
import com.lyape.voice.VoiceServiceIntents

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        requestSettingsFromIntent(intent)
        setContent {
            LyapeTheme {
                LyapeApp()
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestSettingsFromIntent(intent)
    }

    private fun requestSettingsFromIntent(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(VoiceServiceIntents.EXTRA_OPEN_SETTINGS, false) == true) {
            // Consumir una vez: una recreación de la actividad no debe volver a abrir Ajustes.
            intent.removeExtra(VoiceServiceIntents.EXTRA_OPEN_SETTINGS)
            NavigationRequests.requestOpenSettings()
        }
    }
}
