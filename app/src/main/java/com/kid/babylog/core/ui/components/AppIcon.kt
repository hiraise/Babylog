package com.kid.babylog.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.kid.babylog.R
import com.kid.babylog.core.ui.theme.BabyLogTheme

/**
 * Иконка приложения из `res/drawable/ic_*.xml` — Material Symbols Rounded (ADR-0010).
 *
 * Используй вместо `Icon(Icons.…)`: библиотеки иконок в проекте нет, каждая иконка — векторный
 * файл, имя файла — по смыслу (`ic_feeding`, `ic_delete`). Размер по умолчанию 24 dp, цвет —
 * текущий цвет содержимого (`LocalContentColor`).
 *
 * Новую иконку добавляй таким же файлом. Начертание: Rounded, FILL 0, wght 300, GRAD 0, opsz 24 —
 * оптический размер под 24 dp; у opsz 48 штрихи для такого размера тоньше задуманных. Источник —
 * `fonts.gstatic.com/s/i/short-term/release/materialsymbolsrounded/<имя>/wght300/24px.svg`:
 * `d` из SVG переносится в `pathData`, обёртка (`viewport` 960, `translateY` 960) — как у соседних файлов.
 *
 * @param contentDescription текст для TalkBack; `null` — иконка декоративна: рядом есть подпись
 *   или смысл понятен из текста.
 */
@Composable
fun AppIcon(
    @DrawableRes id: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(id),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

/** Все иконки приложения: витрина для выбора. Подпись — имя файла без `ic_`. */
private val AllIcons = listOf(
    R.drawable.ic_feeding to "feeding",
    R.drawable.ic_diaper to "diaper",
    R.drawable.ic_sleep to "sleep",
    R.drawable.ic_medicine to "medicine",
    R.drawable.ic_weight to "weight",
    R.drawable.ic_child to "child",
    R.drawable.ic_add_child to "add_child",
    R.drawable.ic_edit to "edit",
    R.drawable.ic_delete to "delete",
    R.drawable.ic_more to "more",
    R.drawable.ic_calendar to "calendar",
    R.drawable.ic_schedule to "schedule",
    R.drawable.ic_chevron_left to "chevron_left",
    R.drawable.ic_chevron_right to "chevron_right",
    R.drawable.ic_export to "export",
    R.drawable.ic_import to "import",
)

@Preview(name = "AppIcon — все иконки", widthDp = 720)
@Composable
private fun AppIconPreview() = LightDarkPreview {
    val spacing = BabyLogTheme.spacing
    AllIcons.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            row.forEach { (id, name) ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    AppIcon(id, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Пустые ячейки в неполном последнем ряду держат сетку ровной
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
