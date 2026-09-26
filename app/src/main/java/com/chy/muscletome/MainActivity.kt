package com.chy.muscletome

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chy.muscletome.ui.theme.MuscleTomeTheme
import com.chy.muscletome.ui.navigation.MuscleTomeNav
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // MuscleTome is dark-only: light (cream) system bar icons regardless of
        // system theme, with ink behind them.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                scrim = Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.dark(
                scrim = Color.TRANSPARENT,
            ),
        )
        setContent {
            MuscleTomeTheme {
                MuscleTomeNav()
            }
        }
    }
}
