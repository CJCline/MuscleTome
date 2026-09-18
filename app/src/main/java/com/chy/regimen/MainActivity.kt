package com.chy.regimen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chy.regimen.ui.library.AddExerciseScreen
import com.chy.regimen.ui.library.ExerciseLibraryScreen
import com.chy.regimen.ui.theme.RegimenTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RegimenTheme {
                RegimenNav()
            }
        }
    }
}

@Composable
private fun RegimenNav() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "library") {
        composable("library") {
            ExerciseLibraryScreen(
                onAddExercise = { navController.navigate("add_exercise") },
            )
        }
        composable("add_exercise") {
            AddExerciseScreen(onBack = { navController.popBackStack() })
        }
    }
}