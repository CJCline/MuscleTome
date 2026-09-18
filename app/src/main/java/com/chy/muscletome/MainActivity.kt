package com.chy.muscletome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chy.muscletome.ui.theme.MuscleTomeTheme
import com.chy.muscletome.ui.navigation.MuscleTomeNav
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MuscleTomeTheme {
                MuscleTomeNav()
            }
        }
    }
}
