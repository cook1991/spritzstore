package net.spritzlive.spritzstore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import net.spritzlive.spritzstore.ui.AppListScreen
import net.spritzlive.spritzstore.ui.SettingsScreen
import net.spritzlive.spritzstore.ui.SpritzStoreTheme

private enum class Screen { List, Settings }

class MainActivity : ComponentActivity() {
    private val viewModel: SpritzStoreViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpritzStoreTheme {
                var screen by remember { mutableStateOf(Screen.List) }
                when (screen) {
                    Screen.List -> AppListScreen(viewModel, onOpenSettings = { screen = Screen.Settings })
                    Screen.Settings -> SettingsScreen(viewModel, onBack = { screen = Screen.List })
                }
            }
        }
    }
}
