package com.kid.babylog.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.ui.theme.BabyLogTheme

/**
 * Аватар ребёнка: круг `primaryContainer` с первой буквой имени — заглавной, `headlineSmall`
 * цвета `onPrimaryContainer` (буква — [avatarInitial]). В имени без букв — иконка `ic_child`.
 *
 * Используй рядом с именем ребёнка: в карточке, переключателе, диалоге. Аватар декоративный —
 * TalkBack его пропускает, поэтому имя всегда показывай рядом текстом.
 *
 * @param size диаметр; буква всегда `headlineSmall`, она рассчитана на 40–56 dp.
 */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val colors = MaterialTheme.colorScheme
    val initial = avatarInitial(name)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.primaryContainer)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (initial != null) {
            Text(
                text = initial,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onPrimaryContainer,
                maxLines = 1,
                // При крупном шрифте строка выше круга: пусть выходит за него поровну сверху
                // и снизу, тогда буква остаётся в центре, а лишнее срежет круг
                modifier = Modifier.wrapContentSize(unbounded = true),
            )
        } else {
            AppIcon(
                id = R.drawable.ic_child,
                contentDescription = null,
                modifier = Modifier.size(size * 0.55f), // 24 dp в круге 44 dp
                tint = colors.onPrimaryContainer,
            )
        }
    }
}

/**
 * Буква для [Avatar]: первая буква имени заглавной («маша» → «М») вместе с диакритическими
 * знаками за ней («й», набранная как «и» + кратка). Пробелы, кавычки и эмодзи перед буквой
 * пропускаются; в имени без букв — `null`.
 */
internal fun avatarInitial(name: String): String? {
    var index = 0
    while (index < name.length) {
        val codePoint = name.codePointAt(index)
        val next = index + Character.charCount(codePoint)
        if (Character.isLetter(codePoint)) {
            var end = next
            while (end < name.length && name.codePointAt(end).isCombiningMark()) {
                end += Character.charCount(name.codePointAt(end))
            }
            return StringBuilder()
                .appendCodePoint(Character.toTitleCase(codePoint))
                .append(name, next, end)
                .toString()
        }
        index = next
    }
    return null
}

private fun Int.isCombiningMark(): Boolean = when (Character.getType(this)) {
    Character.NON_SPACING_MARK.toInt(),
    Character.COMBINING_SPACING_MARK.toInt(),
    Character.ENCLOSING_MARK.toInt(),
    -> true
    else -> false
}

@Preview(name = "Avatar", widthDp = 720)
@Composable
private fun AvatarPreview() = LightDarkPreview {
    Row(
        horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar("маша")
        Avatar("Ёлка")
        Avatar("") // без букв — иконка
        Avatar("Петя", size = 56.dp)
        Avatar("", size = 56.dp)
    }
}
