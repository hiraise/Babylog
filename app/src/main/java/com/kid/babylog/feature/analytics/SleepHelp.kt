package com.kid.babylog.feature.analytics

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.kid.babylog.R
import com.kid.babylog.core.ui.components.AppSheet

/**
 * Откуда часы ночи (ADR-0036): опросник BISQ, где ночь — «между 7 вечера и 7 утра», и исследование
 * сна детей 17 стран по нему. Ссылки — DOI: адрес статьи не меняется, даже если журнал переедет.
 */
internal val SLEEP_SOURCES = listOf(
    "Sadeh A., Pediatrics, 2004 — опросник BISQ" to "https://doi.org/10.1542/peds.113.6.e570",
    "Mindell J. A. и др., Sleep Medicine, 2010 — сон малышей в 17 странах" to "https://doi.org/10.1016/j.sleep.2009.04.012",
)

/** Справка «?» на карточке «Сон» (ADR-0036): как сон делится на ночной и дневной. */
@Composable
internal fun SleepHelpSheet(onDismiss: () -> Unit) {
    AppSheet(onDismiss = onDismiss, title = "Сон ночью и днём", icon = R.drawable.ic_help) {
        SleepHelp()
    }
}

/**
 * Текст справки без листа — его снимает скриншот-тест. Источник открывается в браузере только
 * по нажатию: само приложение в сеть не ходит (ADR-0002); браузера нет — нажатие ничего не делает.
 */
@Composable
internal fun SleepHelp() {
    val style = MaterialTheme.typography.bodyMedium
    Text(
        "Ночью — сон с $NIGHT_START_HOUR:00 до $NIGHT_END_HOUR:00, днём — с $NIGHT_END_HOUR:00 до $NIGHT_START_HOUR:00. " +
            "Так ночь и день заданы в опроснике детского сна BISQ — по нему сравнивали сон малышей в 17 странах:",
        style = style,
    )
    val uriHandler = LocalUriHandler.current
    val linkStyles = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    SLEEP_SOURCES.forEach { (title, url) ->
        Text(
            buildAnnotatedString {
                withLink(LinkAnnotation.Url(url, linkStyles) { runCatching { uriHandler.openUri(url) } }) { append(title) }
            },
            style = style,
        )
    }
    Text(
        "Сон, который переходит через $NIGHT_END_HOUR:00 или $NIGHT_START_HOUR:00, делится: " +
            "сон с 18:30 до 20:00 — это 30 мин днём и 1 ч ночью.",
        style = style,
    )
    Text(
        "Столбик — сон за сутки с полуночи до полуночи, как в сводке дня: ночная часть в нём — утро " +
            "до $NIGHT_END_HOUR:00 и вечер после $NIGHT_START_HOUR:00. Средние — по прошедшим суткам, в которые вели дневник.",
        style = style,
    )
}
