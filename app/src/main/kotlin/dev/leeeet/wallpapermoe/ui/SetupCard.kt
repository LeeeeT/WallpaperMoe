package dev.leeeet.wallpapermoe.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.leeeet.wallpapermoe.R

/** Both permissions keep rotation running while the app is in the background. */
data class SetupState(
    val needsNotifications: Boolean,
    val needsBatteryExemption: Boolean,
) {
    val anythingMissing: Boolean get() = needsNotifications || needsBatteryExemption
}

private fun readSetupState(context: Context): SetupState {
    val notifications = context.checkSelfPermission(
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED

    val power = context.getSystemService(PowerManager::class.java)
    val exempt = power?.isIgnoringBatteryOptimizations(context.packageName) ?: false

    return SetupState(
        needsNotifications = !notifications,
        needsBatteryExemption = !exempt,
    )
}

/** Re-reads on resume, so returning from the system settings screen updates the card. */
@Composable
fun rememberSetupState(): SetupState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(readSetupState(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state = readSetupState(context) }
    return state
}

@Composable
fun SetupCard(state: SetupState, modifier: Modifier = Modifier) {
    if (!state.anythingMissing) return
    val context = LocalContext.current

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* card refreshes itself on resume */ }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleMedium)
            }

            if (state.needsNotifications) {
                SetupRow(
                    title = stringResource(R.string.setup_notifications_title),
                    detail = stringResource(R.string.setup_notifications_detail),
                    action = stringResource(R.string.action_allow),
                    onClick = {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                )
            }

            if (state.needsNotifications && state.needsBatteryExemption) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            if (state.needsBatteryExemption) {
                SetupRow(
                    title = stringResource(R.string.setup_battery_title),
                    detail = stringResource(R.string.setup_battery_detail),
                    action = stringResource(R.string.action_allow),
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            "package:${context.packageName}".toUri(),
                        )
                        context.startActivity(intent)
                    },
                )
            }
        }
    }
}

@Composable
private fun SetupRow(title: String, detail: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onClick) { Text(action) }
    }
}
