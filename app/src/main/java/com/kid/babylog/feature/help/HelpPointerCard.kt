package com.kid.babylog.feature.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kid.babylog.R
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.theme.BabyLogTheme

/**
 * Где справка (ADR-0043): карточка вверху «Сегодня», один раз — и новичку, и обновившемуся.
 * Исчезает по «Понятно» или когда справку открыли.
 */
@Composable
fun HelpPointerCard(onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = BabyLogTheme.spacing
    val colors = MaterialTheme.colorScheme
    AppCard(modifier.fillMaxWidth(), containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
            AppIcon(R.drawable.ic_help, contentDescription = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text("Как пользоваться дневником", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Справка — в меню ⋮ справа вверху. Каждую тему можно пройти на примере дневника, ваши записи не изменятся.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        // Кнопки — цветом текста карточки: primary на primaryContainer в тёмной теме почти сливается
        val buttons = ButtonDefaults.textButtonColors(contentColor = colors.onPrimaryContainer)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.s, Alignment.End)) {
            TextButton(onClick = onDismiss, colors = buttons) { Text("Понятно") }
            TextButton(onClick = onOpen, colors = buttons) { Text("Открыть справку") }
        }
    }
}
