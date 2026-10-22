package com.kid.babylog.feature.help

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kid.babylog.core.data.HelpProgress
import com.kid.babylog.core.ui.theme.BabyLogTheme

/**
 * Тема справки (ADR-0043): что делать — шагами, кнопка «Попробовать на примере» и переход к следующей
 * теме. «Новое» берётся из [progress] при открытии: отметка «тему открыли» его не гасит, пока экран открыт.
 */
@Composable
fun HelpTopicScreen(
    topic: HelpTopic,
    progress: HelpProgress,
    next: HelpTopic?,
    onTry: () -> Unit,
    onOpenTopic: (HelpTopic) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val spacing = BabyLogTheme.spacing
    val newSteps = remember(topic.id) { topic.newSteps(progress) }
    HelpSurface(modifier) {
        HelpHeader(topic.title, onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = spacing.l, end = spacing.l, bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            item(key = "summary") {
                Text(topic.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            itemsIndexed(topic.readingSteps) { index, step ->
                StepRow(number = index + 1, step = step, isNew = step in newSteps)
            }
            item(key = "try") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.s), modifier = Modifier.padding(top = spacing.s)) {
                    Button(onClick = onTry, modifier = Modifier.fillMaxWidth()) { Text("Попробовать на примере") }
                    Text(
                        "Откроется пример дневника — ваши записи не изменятся.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (next != null) {
                        TextButton(onClick = { onOpenTopic(next) }) { Text("Следующая тема: «${next.title}»") }
                    }
                }
            }
        }
    }
}

/** Номер шага в кружке и что сделать. */
@Composable
private fun StepRow(number: Int, step: GuideStep, isNew: Boolean) {
    val spacing = BabyLogTheme.spacing
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
        Box(Modifier.size(StepNumberSize).background(colors.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Text("$number", style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            if (isNew) NewLabel()
            Text(step.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private val StepNumberSize = 28.dp
