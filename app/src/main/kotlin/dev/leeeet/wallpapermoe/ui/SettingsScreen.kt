package dev.leeeet.wallpapermoe.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.leeeet.wallpapermoe.R
import dev.leeeet.wallpapermoe.model.AppData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(model: MoeViewModel, data: AppData) {
    val storage by model.storage.collectAsState()
    var importing by rememberSaveable { mutableStateOf<String?>(null) }
    val fileName = stringResource(R.string.backup_file_name)
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { target ->
        if (target != null) model.export(target)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source ->
        if (source != null) importing = source.toString()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Column(
                Modifier
                    .contentWidth()
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(stringResource(R.string.settings_network))
                    SwitchGroup(
                        title = stringResource(R.string.settings_unmetered),
                        detail = stringResource(R.string.settings_unmetered_detail),
                        checked = data.unmeteredOnly,
                        onChange = model::setUnmeteredOnly,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(stringResource(R.string.settings_wallhaven))
                    WallhavenKey(data.wallhavenKey, model::setWallhavenKey)
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(stringResource(R.string.settings_storage))
                    storage?.let { usage ->
                        Panel {
                            SizeRow(stringResource(R.string.storage_shown), usage.shown)
                            SizeRow(stringResource(R.string.storage_favorites), usage.favorites)
                            SizeRow(stringResource(R.string.storage_ready), usage.ready)
                            SizeRow(stringResource(R.string.storage_total), usage.shown + usage.favorites + usage.ready, total = true)
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(stringResource(R.string.settings_backup))
                    ActionRow(stringResource(R.string.backup_export), stringResource(R.string.backup_export_detail)) {
                        exporter.launch(fileName)
                    }
                    ActionRow(stringResource(R.string.backup_import), stringResource(R.string.backup_import_detail)) {
                        importer.launch(arrayOf("application/zip", "application/octet-stream"))
                    }
                }
            }
        }
    }

    importing?.let { source ->
        AlertDialog(
            onDismissRequest = { importing = null },
            title = { Text(stringResource(R.string.backup_import_title)) },
            text = { Text(stringResource(R.string.backup_import_body)) },
            confirmButton = {
                TextButton(onClick = {
                    model.import(source.toUri())
                    importing = null
                }) { Text(stringResource(R.string.backup_import)) }
            },
            dismissButton = { TextButton({ importing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun SizeRow(label: String, size: Long, total: Boolean = false) {
    val style = if (total) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = style)
        Text(bytes(size), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionRow(title: String, detail: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WallhavenKey(saved: String, onChange: (String) -> Unit) {
    var key by rememberSaveable { mutableStateOf(saved) }
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = key,
        onValueChange = {
            key = it
            onChange(it)
        },
        label = { Text(stringResource(R.string.wallhaven_api_key)) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    stringResource(if (visible) R.string.action_hide_key else R.string.action_show_key),
                )
            }
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
}
