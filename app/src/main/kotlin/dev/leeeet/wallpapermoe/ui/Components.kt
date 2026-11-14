package dev.leeeet.wallpapermoe.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.leeeet.wallpapermoe.R
import dev.leeeet.wallpapermoe.model.Kind
import kotlin.math.roundToInt

val ContentMaxWidth = 720.dp
val RailBreakpoint = 600.dp

fun Modifier.contentWidth(): Modifier = widthIn(max = ContentMaxWidth).fillMaxWidth()

@Composable
fun SectionHeader(title: String, detail: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun PickerField(value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Filled.KeyboardArrowDown, null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoeSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
fun SheetList(arrangement: Arrangement.Vertical = Arrangement.spacedBy(4.dp), content: LazyListScope.() -> Unit) {
    LazyColumn(Modifier.heightIn(max = windowHeight() * 0.55f), verticalArrangement = arrangement, content = content)
}

@Composable
fun windowHeight(): Dp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }

@Composable
fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, detail: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 32.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        RowText(label, detail)
    }
}

@Composable
fun CheckRow(label: String, checked: Boolean, onClick: () -> Unit, detail: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onClick() })
            .heightIn(min = 32.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        RowText(label, detail)
    }
}

@Composable
private fun RowScope.RowText(label: String, detail: String?) {
    Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

val Kind.icon: ImageVector
    get() = when (this) {
        Kind.FAVORITES -> Icons.Outlined.Favorite
        Kind.PANDORA -> Icons.Outlined.Public
        Kind.WALLHAVEN -> Icons.Outlined.Search
        Kind.NEKOS_BEST -> Icons.Outlined.Brush
        Kind.FOLDER -> Icons.Outlined.Folder
        Kind.KEEP_READY -> Icons.Outlined.Inventory2
        Kind.COMBINE -> Icons.AutoMirrored.Outlined.CallMerge
    }

@Composable
fun SourceGlyph(kind: Kind, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(40.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryFixed,
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(kind.icon, null, Modifier.size(22.dp)) }
    }
}

@Composable
fun SourceRow(
    kind: Kind,
    title: String,
    detail: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SourceGlyph(kind)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail != null) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun <T> OptionSheet(
    title: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    label: @Composable (T) -> String,
) {
    MoeSheet(title, onDismiss) {
        SheetList {
            items(options.size) { index ->
                val option = options[index]
                RadioRow(label(option), option == selected, onClick = {
                    onSelect(option)
                    onDismiss()
                })
            }
        }
    }
}

@Composable
fun SheetLoading() {
    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun SheetFailure(message: String, onRetry: () -> Unit) {
    Column {
        Text(
            message,
            Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_try_again)) }
    }
}

@Composable
fun OptionGroup(title: String, error: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
            if (error != null) InlineError(error)
        }
    }
}

@Composable
fun SwitchGroup(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit, content: (@Composable ColumnScope.() -> Unit)? = null) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier
                    .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = checked, onCheckedChange = null)
            }
            if (checked && content != null) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) { content() }
        }
    }
}

@Composable
fun InlineError(message: String) {
    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
fun LabeledSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    increment: Float,
    onChange: (Float) -> Unit,
    onChangeFinished: () -> Unit = {},
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(valueLabel, style = MaterialTheme.typography.titleMedium)
            }
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                steps = ((range.endInclusive - range.start) / increment).roundToInt() - 1,
                onValueChangeFinished = onChangeFinished,
            )
        }
    }
}

@Composable
fun LabeledIntSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit, increment: Int = 1) {
    LabeledSlider(
        label = label,
        valueLabel = value.toString(),
        value = value.coerceIn(range).toFloat(),
        range = range.first.toFloat()..range.last.toFloat(),
        increment = increment.toFloat(),
        onChange = { onChange(it.roundToInt()) },
    )
}

@Composable
fun ScrimIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
        modifier = modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription) }
    }
}

@Composable
fun FavoriteButton(favorite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ScrimIconButton(
        icon = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        contentDescription = stringResource(if (favorite) R.string.action_remove_favorite else R.string.action_add_favorite),
        onClick = onClick,
        modifier = modifier,
    )
}
