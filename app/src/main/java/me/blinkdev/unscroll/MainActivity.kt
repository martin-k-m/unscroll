package me.blinkdev.unscroll

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import me.blinkdev.unscroll.ui.HomeScreen
import me.blinkdev.unscroll.ui.MainViewModel

private val UnscrollColors = darkColorScheme(
    primary = Color(0xFF7BD88F),
    onPrimary = Color(0xFF0B1410),
    background = Color(0xFF12151C),
    surface = Color(0xFF181C25),
    surfaceVariant = Color(0xFF222836),
)

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = UnscrollColors) {
                val state by viewModel.state.collectAsState()
                Surface(color = MaterialTheme.colorScheme.background) {
                    HomeScreen(state = state, viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}
