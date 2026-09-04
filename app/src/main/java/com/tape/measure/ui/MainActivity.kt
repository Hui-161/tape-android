package com.tape.measure.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.tape.measure.data.prefs.ThemeMode
import com.tape.measure.ui.navigation.TapeNavGraph
import com.tape.measure.ui.theme.TapeTheme
import dagger.hilt.android.AndroidEntryPoint

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
    val viewModel: MainViewModel = hiltViewModel()
    val theme by viewModel.themeFlow.collectAsState(initial = ThemeMode.SYSTEM)

    val darkTheme = when (theme) {
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
        ThemeMode.SYSTEM -> null  // null = follow system
    }

    TapeTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()
            TapeNavGraph(navController = navController)
        }
    }
}
