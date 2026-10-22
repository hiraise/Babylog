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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
 * Места экрана, на которые показывает гайд справки (ADR-0043). Цель ставят модификатором
 * [guideTarget] на сам элемент; шаг гайда называет цель и ждёт её появления.
 */
enum class GuideTarget {
    SLEEP_TILE,
    FEEDING_TILE,
    SWITCH_BREAST,
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
