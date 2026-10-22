package com.kid.babylog.core.ui.guide

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.theme.BabyLogTheme

/**
 * Места экрана, на которые показывает гайд справки (ADR-0043). Цель, куда нажать, ставят модификатором
 * [guideTarget] на сам элемент; экран или лист, о котором гайду надо знать, что он открыт, — тем же
 * модификатором на его элемент (такую цель шаг не обводит: он ждёт её появления). Пустой Spacer для
 * метки не добавляй: в колонке со `spacedBy` он даст лишний промежуток.
 */
enum class GuideTarget {
    // «Сегодня»
    SLEEP_TILE,
    FEEDING_TILE,
    SWITCH_BREAST,
    CRYING_TILE,
    NOTE_TILE,
    DIAPER_TILE,
    HEALTH_TILE,
    WEIGHT_TILE,
    HEIGHT_TILE,
    ILLNESS_CARD,

    // Шапка, меню и нижняя панель
    CHILD_SELECTOR,
    MENU_BUTTON,
    NAV_DIARY,
    NAV_DEVELOPMENT,
    NAV_ANALYTICS,

    // «Дневник»
    HISTORY,
    PAST_DAY,
    DAY_PICKER,
    DAY_SELECTOR,
    EVENT_EDITOR,
    NOTES_BUTTON,
    NOTES_FEED,
    TAG_SUMMARY,

    // «Аналитика» и «Развитие»
    ANALYTICS_OVERVIEW,
    ANALYTICS_SLEEP_TILE,
    ANALYTICS_DETAIL,
    DEVELOPMENT,
    SKILLS_BUTTON,
    SKILLS,

    // Болезнь, дети, тема
    ILLNESS_SCREEN,
    RECOVER_BUTTON,
    CHILDREN_SHEET,
    ADD_CHILD,
    THEME_SHEET,
}

/**
 * Гайд, который идёт на экране прямо сейчас. Есть только в примере дневника: в настоящем
 * [LocalGuide] — null, и [guideTarget] ничего не делает.
 */
interface GuideTracker {
    /** Куда нажать на текущем шаге; null — шаг без цели или гайд закончен. */
    val target: GuideTarget?

    /** Текст текущего шага для открытого нижнего листа: панель гайда остаётся под листом. */
    val sheetHint: String?

    fun shown(target: GuideTarget)

    fun hidden(target: GuideTarget)
}

val LocalGuide = staticCompositionLocalOf<GuideTracker?> { null }

/**
 * Цель гайда для нижнего листа, который открывается внутри: [com.kid.babylog.core.ui.components.AppSheet]
 * ставит её на своё содержимое. Так лист правки записи или выбора дня становится целью, не протаскивая
 * параметр через все свои формы.
 */
val LocalSheetTarget = compositionLocalOf<GuideTarget?> { null }

/** Листы внутри [content] — цель [target] для гайда справки. */
@Composable
fun GuideSheet(target: GuideTarget, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSheetTarget provides target, content = content)
}

/**
 * Цель гайда: сообщает гайду, что элемент на экране, и обводит его, когда это цель текущего шага.
 * Обводка — снаружи элемента, по форме [shape] (по умолчанию — карточки), и пульсирует.
 * Ставь первым в цепочке модификаторов элемента: клип элемента иначе срежет обводку.
 */
fun Modifier.guideTarget(target: GuideTarget, shape: Shape? = null): Modifier = composed {
    val guide = LocalGuide.current ?: return@composed Modifier
    DisposableEffect(guide, target) {
        guide.shown(target)
        onDispose { guide.hidden(target) }
    }
    if (guide.target != target) return@composed Modifier
    val color = MaterialTheme.colorScheme.primary
    val outlineShape = shape ?: MaterialTheme.shapes.medium
    val alpha by rememberInfiniteTransition(label = "цель гайда").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
        label = "обводка",
    )
    Modifier.drawWithContent {
        drawContent()
        val gap = OutlineGap.toPx()
        val outline = outlineShape.createOutline(Size(size.width + 2 * gap, size.height + 2 * gap), layoutDirection, this)
        translate(-gap, -gap) {
            drawOutline(outline, color, alpha = alpha, style = Stroke(OutlineWidth.toPx()))
        }
    }
}

/**
 * Текст шага гайда вверху нижнего листа ([com.kid.babylog.core.ui.components.AppSheet]): лист —
 * отдельное окно и закрывает панель гайда. Вне гайда ничего не показывает.
 */
@Composable
fun GuideSheetHint(modifier: Modifier = Modifier) {
    val hint = LocalGuide.current?.sheetHint ?: return
    val colors = MaterialTheme.colorScheme
    val spacing = BabyLogTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.primaryContainer, MaterialTheme.shapes.small)
            .padding(horizontal = spacing.m, vertical = spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        AppIcon(R.drawable.ic_help, contentDescription = null, tint = colors.onPrimaryContainer)
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = colors.onPrimaryContainer)
    }
}

private const val PULSE_MS = 700
private val OutlineWidth = 3.dp
private val OutlineGap = 3.dp
