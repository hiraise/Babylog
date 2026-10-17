package com.kid.babylog.feature.diary

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.data.ThemeMode
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.theme.BabyLogTheme

/**
 * Выбор темы в нижнем листе (ADR-0017): три строки в одну колонку, у выбранной — отметка.
 * Нажимается вся строка. Тема применяется сразу, лист остаётся открытым.
 */
@Composable
internal fun ThemeSheet(mode: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    AppSheet(onDismiss = onDismiss, title = "Тема", icon = R.drawable.ic_theme, edgeToEdge = true) {
        Column(Modifier.selectableGroup()) {
            ThemeMode.entries.forEach { option ->
                ThemeRow(
                    icon = themeIcon(option),
                    title = themeLabel(option),
                    subtitle = if (option == ThemeMode.SYSTEM) "Светлая или тёмная — как в телефоне" else null,
                    selected = option == mode,
                    onClick = { onSelect(option) },
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
