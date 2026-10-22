package com.kid.babylog.feature.help

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.kid.babylog.R
import com.kid.babylog.core.data.HelpProgress
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.theme.BabyLogTheme

/**
 * Справка (ADR-0043): темы по порядку. «Пройти по порядку» открывает гайд первой непройденной темы,
 * касание темы — её экран. Тема с новыми шагами — «Новое», пройденная — галочка.
 */
@Composable
fun HelpScreen(
    topics: List<HelpTopic>,
    progress: HelpProgress,
    onOpenTopic: (HelpTopic) -> Unit,
    onStartGuide: (HelpTopic) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val spacing = BabyLogTheme.spacing
    val next = nextUnfinished(topics, progress)
    val started = topics.any { it.id in progress.completed }
    HelpSurface(modifier) {
        HelpHeader("Справка", onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = spacing.l, end = spacing.l, bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            item(key = "intro") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.m), modifier = Modifier.padding(bottom = spacing.s)) {
                    Text(
                        "Каждую тему можно пройти на примере дневника: нажимаете сами, а ваши записи не меняются. " +
                            "Пройдите темы по порядку или откройте ту, что нужна.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (next != null) {
                        Button(onClick = { onStartGuide(next) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (started) "Продолжить: «${next.title}»" else "Пройти по порядку")
                        }
                    }
                }
            }
            items(topics, key = { it.id }) { topic ->
                TopicRow(topic, isNew = topic.isNew(progress), completed = topic.id in progress.completed, onClick = { onOpenTopic(topic) })
            }
        }
    }
}

@Composable
private fun TopicRow(topic: HelpTopic, isNew: Boolean, completed: Boolean, onClick: () -> Unit) {
    val spacing = BabyLogTheme.spacing
    val colors = MaterialTheme.colorScheme
    AppCard(Modifier.fillMaxWidth(), onClick = onClick, onClickLabel = "открыть тему") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
            AppIcon(topic.icon, contentDescription = null, tint = colors.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                    Text(topic.title, style = MaterialTheme.typography.titleMedium)
                    if (isNew) NewLabel()
                }
                Text(topic.summary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (completed) {
                AppIcon(R.drawable.ic_check, contentDescription = "пройдено", tint = colors.primary)
            } else {
                AppIcon(R.drawable.ic_chevron_right, contentDescription = null)
            }
        }
    }
}

/**
 * Фон экрана справки. Surface, а не Column с фоном: он задаёт цвет текста и иконок (`onBackground`) —
 * без него они чёрные и в тёмной теме не читаются. Экраны справки стоят вне Scaffold дневника.
 */
@Composable
internal fun HelpSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(content = content)
    }
}

/** «‹ Справка»: заголовок экрана справки с кнопкой «Назад». */
@Composable
internal fun HelpHeader(title: String, onBack: () -> Unit) {
    val spacing = BabyLogTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.xs, vertical = spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { AppIcon(R.drawable.ic_chevron_left, contentDescription = "Назад") }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
    }
}

/** Метка «Новое» у темы и шага, которые появились после того, как тему открывали. */
@Composable
internal fun NewLabel() {
    val colors = MaterialTheme.colorScheme
    Text(
        "Новое",
        style = MaterialTheme.typography.labelSmall,
        color = colors.onPrimary,
        modifier = Modifier
            .background(colors.primary, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = BabyLogTheme.spacing.s),
    )
}
