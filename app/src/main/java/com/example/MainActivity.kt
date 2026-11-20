package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.ThemeMode
import com.example.navigation.PdfCompressorNavGraph
import com.example.presentation.MainViewModel
import com.example.presentation.theme.PdfCompressorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as PdfCompressorApplication).container

        setContent {
            val mainViewModel: MainViewModel = viewModel(
                factory = MainViewModel.Factory(appContainer.settingsRepository)
            )
            val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            PdfCompressorTheme(themeMode = themeMode) {
                PdfCompressorNavGraph(
                    container = appContainer,
                    isDarkTheme = isDark,
                    onToggleTheme = { mainViewModel.toggleQuickTheme(isDark) }
                )
            }
        }
    }
}
