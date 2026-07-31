package dev.leeeet.wallpapermoe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.leeeet.wallpapermoe.service.Engine
import dev.leeeet.wallpapermoe.ui.MoeRoot
import dev.leeeet.wallpapermoe.ui.MoeTheme
import dev.leeeet.wallpapermoe.ui.MoeViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Graph.init(applicationContext)

        setContent {
            MoeTheme {
                val vm: MoeViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                MoeRoot(state = state, vm = vm)
            }
        }
    }

    /** A foreground activity may always start a foreground service, so this repairs a refused start. */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            if (Graph.prefs.current().enabled) Engine.ensure(this@MainActivity)
        }
    }
}
