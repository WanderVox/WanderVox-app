package com.wandervox.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wandervox.app.navigation.WanderVoxNavHost
import com.wandervox.core.ui.theme.WanderVoxTheme
import com.wandervox.features.translator.presentation.TranslatorRoute
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app only ships a light palette, so keep the bars light even in system dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(LightNavigationScrim, DarkNavigationScrim),
        )
        setContent {
            WanderVoxTheme {

                WanderVoxNavHost()
            }
        }
    }
}

// Same scrims androidx.activity uses by default; only visible with 3-button navigation.
private val LightNavigationScrim = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
private val DarkNavigationScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
