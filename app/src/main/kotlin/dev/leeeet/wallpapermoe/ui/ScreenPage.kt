package dev.leeeet.wallpapermoe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.leeeet.wallpapermoe.R
import dev.leeeet.wallpapermoe.model.AppData
import dev.leeeet.wallpapermoe.model.Appearance
import dev.leeeet.wallpapermoe.model.Frequency
import dev.leeeet.wallpapermoe.model.Screen
import dev.leeeet.wallpapermoe.service.renderEffect

private const val MULTIPLIER_STEP = 0.1f
private const val BLUR_STEP = 0.05f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenPage(screen: Screen, model: MoeViewModel, data: AppData, back: () -> Unit) {
    val feed = data.activeFeed(screen)
    var choosingFrequency by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(screen.label)) },
                navigationIcon = {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier
                    .contentWidth()
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (screen == Screen.LOCK) {
                    SwitchGroup(
                        title = stringResource(R.string.lock_follow_home),
                        detail = stringResource(R.string.lock_follow_home_detail),
                        checked = data.lock == null,
                        onChange = { follow -> model.setLockSeparate(!follow) },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(stringResource(R.string.settings_how_often), stringResource(R.string.settings_interval_detail))
                    PickerField(stringResource(feed.frequency.label), onClick = { choosingFrequency = true })
                }
                AppearanceSection(model, data, screen)
            }
        }
    }

    if (choosingFrequency) {
        OptionSheet(
            title = stringResource(R.string.settings_how_often),
            options = Frequency.entries,
            selected = feed.frequency,
            onSelect = { model.setFrequency(screen, it) },
            onDismiss = { choosingFrequency = false },
        ) { stringResource(it.label) }
    }
}

@Composable
private fun AppearanceSection(model: MoeViewModel, data: AppData, screen: Screen) {
    val saved = data.appearance(screen)
    var draft by remember(screen, saved) { mutableStateOf(saved) }
    val commit = { model.setAppearance(screen, draft) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                SectionHeader(stringResource(R.string.settings_appearance), stringResource(R.string.settings_appearance_detail))
            }
            if (draft != Appearance()) {
                TextButton(onClick = {
                    draft = Appearance()
                    model.setAppearance(screen, Appearance())
                }) { Text(stringResource(R.string.action_reset)) }
            }
        }
        data.shown(screen)?.image?.let { image ->
            AsyncImage(
                model = model.file(image),
                contentDescription = stringResource(R.string.settings_preview_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(MaterialTheme.shapes.large)
                    .graphicsLayer { renderEffect = draft.renderEffect(size.width.toInt())?.asComposeRenderEffect() },
            )
        }
        LabeledSlider(
            label = stringResource(R.string.settings_brightness),
            valueLabel = multiplier(draft.brightness),
            value = draft.brightness,
            range = Appearance.BRIGHTNESS,
            increment = MULTIPLIER_STEP,
            onChange = { draft = draft.copy(brightness = it) },
            onChangeFinished = commit,
        )
        LabeledSlider(
            label = stringResource(R.string.settings_contrast),
            valueLabel = multiplier(draft.contrast),
            value = draft.contrast,
            range = Appearance.CONTRAST,
            increment = MULTIPLIER_STEP,
            onChange = { draft = draft.copy(contrast = it) },
            onChangeFinished = commit,
        )
        LabeledSlider(
            label = stringResource(R.string.settings_blur),
            valueLabel = percent(draft.blur),
            value = draft.blur,
            range = Appearance.BLUR,
            increment = BLUR_STEP,
            onChange = { draft = draft.copy(blur = it) },
            onChangeFinished = commit,
        )
    }
}

@Composable
private fun multiplier(value: Float): String =
    stringResource(R.string.settings_multiplier_value, String.format(LocalConfiguration.current.locales[0], "%.1f", value))
