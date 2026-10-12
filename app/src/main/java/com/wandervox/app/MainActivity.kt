package com.wandervox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import com.wandervox.app.navigation.WanderVoxNavHost
import com.wandervox.ui.theme.WanderVoxTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WanderVoxTheme {
                WanderVoxNavHost()
            }
        }
    }
}
