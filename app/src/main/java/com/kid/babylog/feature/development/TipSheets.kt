package com.kid.babylog.feature.development

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import com.kid.babylog.R
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.components.ChoiceChip
import com.kid.babylog.core.ui.components.ItemRow
import com.kid.babylog.core.ui.theme.BabyLogTheme
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor

/** Иконка темы совета; у «когда показать педиатру» — стетоскоп, какой бы ни была тема. */
@DrawableRes
fun Tip.icon(): Int = if (kind == TipKind.DOCTOR) R.drawable.ic_stethoscope else category.icon()

@DrawableRes
fun TipCategory.icon(): Int = when (this) {
    TipCategory.SLEEP -> R.drawable.ic_sleep
    TipCategory.FEEDING -> R.drawable.ic_feeding
    TipCategory.DEVELOPMENT -> R.drawable.ic_development
    TipCategory.SAFETY -> R.drawable.ic_shield
    TipCategory.CRYING -> R.drawable.ic_crying
    TipCategory.HEALTH -> R.drawable.ic_health
}

/** Возраст совета целыми месяцами: «для 0–2 мес» — с месяца начала до месяца конца включительно. */
internal fun tipAgeText(tip: Tip): String {
    val from = floor(tip.ages.first / DAYS_PER_MONTH).toInt()
    val to = ceil((tip.ages.last + 1) / DAYS_PER_MONTH).toInt()
    return "для $from–$to мес"
}

/** Строка совета в списке: иконка темы, заголовок, краткий текст; нажимается вся строка. */
@Composable
internal fun TipRow(tip: Tip, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    ItemRow(
        icon = tip.icon(),
        title = tip.title,
        subtitle = tip.summary,
        trailing = { AppIcon(R.drawable.ic_chevron_right, contentDescription = null) },
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "открыть совет", role = Role.Button, onClick = onOpen)
            .padding(vertical = BabyLogTheme.spacing.s),
    )
}

/**
 * Строка «когда показать педиатру» в жёлтом блоке: без иконки — стетоскоп уже в заголовке блока,
 * а зелёная иконка строки спорила бы с его цветом. Текст — цвета содержимого блока.
 */
@Composable
internal fun DoctorTipRow(tip: Tip, onOpen: () -> Unit) {
    val spacing = BabyLogTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "открыть совет", role = Role.Button, onClick = onOpen)
            .padding(vertical = spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(tip.title, style = MaterialTheme.typography.bodyLarge)
            Text(tip.summary, style = MaterialTheme.typography.bodySmall)
        }
        AppIcon(R.drawable.ic_chevron_right, contentDescription = null)
    }
}

/** Совет целиком — нижний лист (ADR-0018). */
@Composable
fun TipSheet(tip: Tip, onDismiss: () -> Unit) {
    AppSheet(onDismiss = onDismiss, title = tip.title, icon = tip.icon()) {
        TipDetails(tip)
    }
}

/**
 * Текст совета (ADR-0037): тема и возраст, краткий текст, подробности, пункты, сразу после них —
 * «Источники расходятся», если позиции разные (тексты ссылаются на этот блок «см. ниже»), затем
 * источники ссылками и дата проверки. Ссылку открывает браузер только по нажатию, само приложение
 * в сеть не ходит (ADR-0002); браузера нет — нажатие ничего не делает.
 */
@Composable
internal fun TipDetails(tip: Tip) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val spacing = BabyLogTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        val kind = if (tip.kind == TipKind.DOCTOR) "Когда показать педиатру" else tip.category.title
        Text("$kind · ${tipAgeText(tip)}", style = typography.bodySmall, color = colors.onSurfaceVariant)
        Text(tip.summary, style = typography.titleSmall)
        Text(tip.body, style = typography.bodyMedium)
        if (tip.points.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                tip.points.forEach { point ->
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                        Text("•", style = typography.bodyMedium, color = colors.primary)
                        Text(point, style = typography.bodyMedium)
                    }
                }
            }
        }
        if (tip.disagreement.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceContainerHighest, MaterialTheme.shapes.medium)
                    .padding(spacing.m),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text("Источники расходятся", style = typography.titleSmall)
                tip.disagreement.forEach { position ->
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(position.source) }
                            append(" — ${position.position}")
                        },
                        style = typography.bodyMedium,
                    )
                }
            }
        }
        TipSources(tip)
        Text(
            "Проверено ${tip.reviewedAt.format(REVIEWED)}. Справочная информация, не заменяет консультацию врача.",
            style = typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun TipSources(tip: Tip) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val uriHandler = LocalUriHandler.current
    val linkStyles = TextLinkStyles(SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline))
    Column(verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s)) {
        Text("Источники", style = typography.titleSmall)
        tip.sources.forEach { source ->
            Column {
                Text(
                    buildAnnotatedString {
                        withLink(LinkAnnotation.Url(source.url, linkStyles) { runCatching { uriHandler.openUri(source.url) } }) {
                            append(source.name)
                        }
                    },
                    style = typography.bodyMedium,
                )
                Text(source.section, style = typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

/**
 * Все советы недели — нижний лист с чипами тем: «Все» и темы, которые есть на этой неделе.
 * Касание совета закрывает лист и открывает совет ([onOpen]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllTipsSheet(week: DevelopmentWeek, onOpen: (Tip) -> Unit, onDismiss: () -> Unit) {
    var category by rememberSaveable { mutableStateOf<TipCategory?>(null) }
    val categories = week.allTips.map { it.category }.distinct().sortedBy { it.ordinal }
    AppSheet(onDismiss = onDismiss, title = "Советы на ${weekAccusative(week.week)}", icon = R.drawable.ic_lightbulb) { closer ->
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s),
        ) {
            ChoiceChip(selected = category == null, onClick = { category = null }, label = "Все")
            categories.forEach { option ->
                ChoiceChip(selected = category == option, onClick = { category = option }, label = option.title)
            }
        }
        Column {
            week.allTips.filter { category == null || it.category == category }.forEach { tip ->
                TipRow(tip, onOpen = { closer.close { onOpen(tip) } })
            }
        }
    }
}

private val REVIEWED = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** Ссылка на источник таблицы или совета; открывает браузер только по нажатию (ADR-0002). */
@Composable
internal fun SourceLink(source: TipSource) {
    val colors = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val linkStyles = TextLinkStyles(SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline))
    Column {
        Text(
            buildAnnotatedString {
                append("Источник: ")
                withLink(LinkAnnotation.Url(source.url, linkStyles) { runCatching { uriHandler.openUri(source.url) } }) {
                    append(source.name)
                }
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Text(source.section, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}
