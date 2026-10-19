package com.kid.babylog.feature.diary

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.data.DarkPalette
import com.kid.babylog.core.data.ThemeChoice
import com.kid.babylog.core.data.ThemeMode
import com.kid.babylog.core.data.isDark
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.core.ui.theme.darkColors

/** Круг образца палитры. */
private val SwatchSize = 44.dp

/** Точка акцента в круге образца; у выбранного она больше и с галочкой. */
private val AccentDotSize = 16.dp
private val SelectedDotSize = 24.dp

/**
 * Выбор темы в нижнем листе: три строки режима в одну колонку (ADR-0017), под ними — палитры
 * тёмной темы (ADR-0032). Выбор применяется сразу, лист остаётся открытым.
 */
@Composable
internal fun ThemeSheet(theme: ThemeChoice, onSelect: (ThemeChoice) -> Unit, onDismiss: () -> Unit) {
    AppSheet(onDismiss = onDismiss, title = "Тема", icon = R.drawable.ic_theme, edgeToEdge = true) {
        ThemeOptions(theme, onSelect)
    }
}

/**
 * Содержимое листа «Тема» без самого листа — его снимают скриншот-тесты. Режим — строки с
 * радиокнопкой, нажимается вся строка. Палитры — ряд образцов с подписями: палитр пять, строками
 * лист вышел бы вдвое длиннее. Пока тема на экране светлая, под заголовком палитр — что они видны
 * только в тёмной: иначе выбор ничего не меняет на глазах.
 */
@Composable
internal fun ThemeOptions(theme: ThemeChoice, onSelect: (ThemeChoice) -> Unit) {
    val spacing = BabyLogTheme.spacing
    Column {
        Column(Modifier.selectableGroup()) {
            ThemeMode.entries.forEach { option ->
                ThemeRow(
                    icon = themeIcon(option),
                    title = themeLabel(option),
                    subtitle = if (option == ThemeMode.SYSTEM) "Светлая или тёмная — как в телефоне" else null,
                    selected = option == theme.mode,
                    onClick = { onSelect(theme.copy(mode = option)) },
                )
            }
        }
        Column(Modifier.padding(start = spacing.l, end = spacing.l, top = spacing.l, bottom = spacing.s)) {
            Text("Цвета тёмной темы", style = MaterialTheme.typography.titleSmall)
            if (!theme.mode.isDark(systemDark = isSystemInDarkTheme())) {
                Text(
                    "Видны, когда тема тёмная",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.s)
                .selectableGroup(),
        ) {
            DarkPalette.entries.forEach { palette ->
                PaletteSwatch(
                    palette = palette,
                    selected = palette == theme.darkPalette,
                    onClick = { onSelect(theme.copy(darkPalette = palette)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Строка листа во всю ширину: иконка, подпись с пояснением и радиокнопка справа. */
@Composable
private fun ThemeRow(@DrawableRes icon: Int, title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    val spacing = BabyLogTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = spacing.l, vertical = spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        AppIcon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Нажатие обрабатывает вся строка, у самой кнопки своего onClick нет
        RadioButton(selected = selected, onClick = null)
    }
}

/**
 * Образец палитры: круг цвета её фона с точкой акцента, под ним название. Выбранный обведён
 * акцентом текущей темы, а точка становится галочкой — выбор виден и без различения цветов.
 * Цвета образца — из палитры, которую он показывает, а не из текущей темы.
 */
@Composable
private fun PaletteSwatch(palette: DarkPalette, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val spacing = BabyLogTheme.spacing
    val shown = darkColors(palette)
    val current = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .border(2.dp, if (selected) current.primary else Color.Transparent, CircleShape)
                .padding(spacing.xs)
                .size(SwatchSize)
                .background(shown.background, CircleShape)
                .border(1.dp, current.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                AppIcon(
                    R.drawable.ic_check,
                    contentDescription = null,
                    tint = shown.onPrimary,
                    modifier = Modifier
                        .size(SelectedDotSize)
                        .background(shown.primary, CircleShape),
                )
            } else {
                Box(Modifier.size(AccentDotSize).background(shown.primary, CircleShape))
            }
        }
        Text(
            paletteLabel(palette),
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) current.onSurface else current.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@DrawableRes
private fun themeIcon(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.drawable.ic_theme
    ThemeMode.LIGHT -> R.drawable.ic_theme_light
    ThemeMode.DARK -> R.drawable.ic_theme_dark
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Как в системе"
    ThemeMode.LIGHT -> "Светлая"
    ThemeMode.DARK -> "Тёмная"
}

private fun paletteLabel(palette: DarkPalette): String = when (palette) {
    DarkPalette.DUSK -> "Сумерки"
    DarkPalette.NIGHTLIGHT -> "Ночник"
    DarkPalette.SAGE -> "Шалфей"
    DarkPalette.POWDER -> "Пудра"
    DarkPalette.CLASSIC -> "Классика"
}
