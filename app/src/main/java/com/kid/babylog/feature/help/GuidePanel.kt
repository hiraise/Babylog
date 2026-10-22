package com.kid.babylog.feature.help

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.kid.babylog.R
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.theme.BabyLogTheme

/**
 * Панель гайда над примером дневника (ADR-0043): что это пример, шаг из скольких, текст шага и его
 * кнопка, если шаг засчитывается кнопкой. В конце — «Готово», «Ещё раз» и следующая тема.
 * ✕ закрывает пример. Текст шага объявляет TalkBack, когда шаг сменился.
 */
@Composable
fun GuidePanel(
    controller: GuideController,
    next: HelpTopic?,
    onClose: () -> Unit,
    onRestart: () -> Unit,
    onNextTopic: (HelpTopic) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = BabyLogTheme.spacing
    val colors = MaterialTheme.colorScheme
    val steps = controller.topic.steps
    val step = controller.step
    val shape = MaterialTheme.shapes.large.copy(topStart = CornerSize(0), topEnd = CornerSize(0))
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.primaryContainer, shape)
            .padding(start = spacing.l, end = spacing.xs, bottom = spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Пример · ничего не сохранится",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                AppIcon(R.drawable.ic_close, contentDescription = "Закрыть пример", tint = colors.onPrimaryContainer)
            }
        }
        Column(
            modifier = Modifier.padding(end = spacing.m),
            verticalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            if (step != null) {
                Text(
                    "${controller.topic.title} · шаг ${controller.index + 1} из ${steps.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onPrimaryContainer,
                )
                LinearProgressIndicator(progress = { controller.index.toFloat() / steps.size }, modifier = Modifier.fillMaxWidth())
                Text(
                    step.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (step.done == StepDone.Next) {
                    Button(onClick = controller::press) { Text(step.button ?: "Далее") }
                } else {
                    // Шаг ждёт действия в примере; не выходит — гайд не застревает
                    TextButton(onClick = controller::skip, colors = ButtonDefaults.textButtonColors(contentColor = colors.onPrimaryContainer)) {
                        Text("Пропустить шаг")
                    }
                }
            } else {
                Text(
                    "Готово: тема «${controller.topic.title}» пройдена",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                    OutlinedButton(onClick = onRestart) { Text("Ещё раз") }
                    if (next != null) {
                        Button(onClick = { onNextTopic(next) }) { Text("Дальше: «${next.title}»") }
                    } else {
                        Button(onClick = onClose) { Text("К справке") }
                    }
                }
            }
        }
    }
}
