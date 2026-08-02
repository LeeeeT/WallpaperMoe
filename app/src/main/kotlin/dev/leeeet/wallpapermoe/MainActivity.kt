package dev.leeeet.wallpapermoe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.leeeet.wallpapermoe.service.WallpaperRuntime
import dev.leeeet.wallpapermoe.ui.MoeRoot
import dev.leeeet.wallpapermoe.ui.MoeTheme
import dev.leeeet.wallpapermoe.ui.MoeViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MoeTheme {
                val vm: MoeViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                MoeRoot(state = state, vm = vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        WallpaperRuntime.refresh(this)
    }
}
