package com.tape.measure.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.tape.measure.ui.navigation.TapeNavGraph
import com.tape.measure.ui.theme.TapeTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single Activity — hosts the entire Compose / Navigation graph.
 *
 * All screen transitions happen inside [TapeNavGraph]; this file stays thin.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TapeApp() }
    }
}

@Composable
private fun TapeApp() {
    TapeTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()
            TapeNavGraph(navController = navController)
        }
    }
}
