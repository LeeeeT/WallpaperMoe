package dev.leeeet.wallpapermoe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.leeeet.wallpapermoe.ui.MoeRoot
import dev.leeeet.wallpapermoe.ui.MoeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MoeTheme { MoeRoot() } }
    }
}
