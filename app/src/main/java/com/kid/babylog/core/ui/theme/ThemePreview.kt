package com.kid.babylog.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kid.babylog.core.data.DarkPalette
import java.time.Clock

// Витрина темы для Android Studio: в каждом превью светлая и тёмная тема рядом.
// Цвета, шрифты и формы берутся только из темы — литералов цвета здесь нет.

@Preview(name = "Цвета", widthDp = 720)
@Composable
private fun ColorsPreview() = LightAndDark { ColorRoles() }

@Preview(name = "Шрифты", widthDp = 720)
@Composable
private fun TypographyPreview() = LightAndDark { TypeScale() }

@Preview(name = "Формы, отступы, компоненты", widthDp = 720)
@Composable
private fun ShapesPreview() = LightAndDark {
    ShapeScale()
    SpacingScale()
    Components()
}

/** Роли цвета во всех палитрах тёмной темы рядом (ADR-0032). */
@Preview(name = "Палитры тёмной темы", widthDp = 1800)
@Composable
private fun DarkPalettesPreview() {
    Row {
        DarkPalette.entries.forEach { palette ->
            BabyLogTheme(clock = Clock.systemDefaultZone(), darkTheme = true, darkPalette = palette) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(BabyLogTheme.spacing.l),
                    verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.m),
                ) {
                    Text(palette.name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                    ColorRoles()
                }
            }
        }
    }
}

/** Две колонки с одним содержимым: слева светлая тема, справа тёмная. */
@Composable
private fun LightAndDark(content: @Composable () -> Unit) {
    Row {
        for (dark in listOf(false, true)) {
            BabyLogTheme(clock = Clock.systemDefaultZone(), darkTheme = dark) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(BabyLogTheme.spacing.l),
                    verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.m),
                ) {
                    Text(
                        text = if (dark) "Тёмная тема" else "Светлая тема",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    content()
                }
            }
        }
    }
}

/** Фон и текст на нём с подписью ролей. */
private class Swatch(val roles: String, val container: Color, val content: Color)

@Composable
private fun ColorRoles() {
    val colors = MaterialTheme.colorScheme
    listOf(
        Swatch("primary / onPrimary — кнопки", colors.primary, colors.onPrimary),
        Swatch("primaryContainer / onPrimaryContainer", colors.primaryContainer, colors.onPrimaryContainer),
        Swatch("secondaryContainer / onSecondaryContainer", colors.secondaryContainer, colors.onSecondaryContainer),
        Swatch("tertiaryContainer / onTertiaryContainer", colors.tertiaryContainer, colors.onTertiaryContainer),
        Swatch("error / onError", colors.error, colors.onError),
        Swatch("errorContainer / onErrorContainer", colors.errorContainer, colors.onErrorContainer),
        Swatch("surfaceContainer — меню", colors.surfaceContainer, colors.onSurface),
        Swatch("surfaceContainerHigh — диалоги, пикеры", colors.surfaceContainerHigh, colors.onSurface),
        Swatch("surfaceContainerHighest — без акцента", colors.surfaceContainerHighest, colors.onSurfaceVariant),
        Swatch("inverseSurface — snackbar", colors.inverseSurface, colors.inverseOnSurface),
    ).forEach { swatch ->
        Text(
            text = swatch.roles,
            style = MaterialTheme.typography.labelMedium,
            color = swatch.content,
            modifier = Modifier
                .fillMaxWidth()
                .background(swatch.container, MaterialTheme.shapes.extraSmall)
                .padding(BabyLogTheme.spacing.m),
        )
    }
    SampleCard {
        Text("surfaceContainerLow — карточка", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        Text("onSurfaceVariant — второстепенный текст", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Text("primary — акцентный текст", style = MaterialTheme.typography.bodyMedium, color = colors.primary)
        Text("error — ошибка", style = MaterialTheme.typography.bodyMedium, color = colors.error)
        HorizontalDivider()
        Text(
            text = "outline — рамка",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            modifier = Modifier
                .border(1.dp, colors.outline, MaterialTheme.shapes.extraSmall)
                .padding(BabyLogTheme.spacing.s),
        )
    }
}

/** Карточка, как её рисуют экраны: `surfaceContainerLow`, форма `medium` и тень темы. */
@Composable
private fun SampleCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .themeShadow(shape, BabyLogTheme.shadows)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .padding(BabyLogTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s),
        content = content,
    )
}

private fun Modifier.themeShadow(shape: Shape, shadows: Shadows): Modifier =
    shadows.card?.let { dropShadow(shape, it) } ?: this

/** Образец стиля текста с подписью «имя · размер/высота строки». */
private class Sample(val name: String, val style: TextStyle, val text: String)

@Composable
private fun TypeScale() {
    val type = MaterialTheme.typography
    val timer = "1:25:40"
    val title = "Дневник малыша"
    val text = "Покормили левой грудью, уснул в 14:30"
    listOf(
        Sample("displayLarge", type.displayLarge, timer),
        Sample("displayMedium — таймер", type.displayMedium, timer),
        Sample("displaySmall", type.displaySmall, timer),
        Sample("headlineLarge", type.headlineLarge, title),
        Sample("headlineMedium", type.headlineMedium, title),
        Sample("headlineSmall — диалоги, шапка", type.headlineSmall, title),
        Sample("titleLarge", type.titleLarge, "Кормление · 14:30"),
        Sample("titleMedium", type.titleMedium, "Сон · 1 ч 25 мин"),
        Sample("titleSmall", type.titleSmall, "Подгузник · пописал"),
        Sample("bodyLarge", type.bodyLarge, text),
        Sample("bodyMedium", type.bodyMedium, text),
        Sample("bodySmall", type.bodySmall, text),
        Sample("labelLarge — кнопки, чипы", type.labelLarge, "Уснул · Сохранить"),
        Sample("labelMedium", type.labelMedium, "5 мин назад"),
        Sample("labelSmall", type.labelSmall, "5 мин назад"),
    ).forEach { sample ->
        Column {
            Text(
                text = "${sample.name} · ${sample.style.fontSize.value.toInt()}/${sample.style.lineHeight.value.toInt()}",
                style = type.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(sample.text, style = sample.style, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun ShapeScale() {
    val shapes = MaterialTheme.shapes
    listOf(
        "extraSmall — поля, меню" to shapes.extraSmall,
        "small — чипы" to shapes.small,
        "medium — карточки" to shapes.medium,
        "large — FAB" to shapes.large,
        "extraLarge — диалоги, пикеры" to shapes.extraLarge,
    ).forEach { (name, shape) ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.m),
        ) {
            Box(
                Modifier
                    .size(width = 96.dp, height = 64.dp)
                    .themeShadow(shape, BabyLogTheme.shadows)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, shape),
            )
            Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun SpacingScale() {
    val spacing = BabyLogTheme.spacing
    listOf(
        "xs" to spacing.xs,
        "s" to spacing.s,
        "m" to spacing.m,
        "l" to spacing.l,
        "xl" to spacing.xl,
        "xxl" to spacing.xxl,
    ).forEach { (name, step) ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            Text(
                text = "$name · ${step.value.toInt()} dp",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.width(72.dp),
            )
            Box(Modifier.size(width = step, height = spacing.s).background(MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
private fun Components() {
    val spacing = BabyLogTheme.spacing
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
        Button(onClick = {}) { Text("Уснул") }
        OutlinedButton(onClick = {}) { Text("Отмена") }
        TextButton(onClick = {}) { Text("Готово") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
        AssistChip(onClick = {}, label = { Text("5 мин назад") })
        FilterChip(selected = true, onClick = {}, label = { Text("Маша") })
        FilterChip(selected = false, onClick = {}, label = { Text("Петя") })
    }
    OutlinedTextField(
        value = "Витамин D",
        onValueChange = {},
        label = { Text("Лекарство") },
        modifier = Modifier.fillMaxWidth(),
    )
}
