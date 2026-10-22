package com.kid.babylog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kid.babylog.core.data.HelpProgress
import com.kid.babylog.core.data.ThemeChoice
import com.kid.babylog.core.ui.LocalClock
import com.kid.babylog.core.ui.guide.LocalGuide
import com.kid.babylog.feature.diary.DiaryScreen
import com.kid.babylog.feature.diary.MainSection
import com.kid.babylog.feature.help.GuidePanel
import com.kid.babylog.feature.help.GuideStart
import com.kid.babylog.feature.help.HelpScreen
import com.kid.babylog.feature.help.HelpTopic
import com.kid.babylog.feature.help.HelpTopicScreen
import com.kid.babylog.feature.help.topicAfter

/** Где пользователь в справке (ADR-0043); null — в дневнике. */
sealed interface HelpRoute {
    /** Тема экрана; у списка тем — null. */
    val topicId: String?

    data object Topics : HelpRoute {
        override val topicId: String? = null
    }

    data class Topic(override val topicId: String) : HelpRoute

    data class Guide(override val topicId: String) : HelpRoute
}

/** Справка переживает поворот экрана: маршрут сохраняется строкой «topics», «topic:id», «guide:id». */
val HelpRouteSaver = Saver<HelpRoute?, String>(
    save = { route ->
        when (route) {
            null -> null
            HelpRoute.Topics -> "topics"
            is HelpRoute.Topic -> "topic:${route.topicId}"
            is HelpRoute.Guide -> "guide:${route.topicId}"
        }
    },
    restore = { saved ->
        when {
            saved == "topics" -> HelpRoute.Topics
            saved.startsWith("topic:") -> HelpRoute.Topic(saved.removePrefix("topic:"))
            saved.startsWith("guide:") -> HelpRoute.Guide(saved.removePrefix("guide:"))
            else -> null
        }
    },
)

/**
 * Справка поверх дневника (ADR-0043): список тем, экран темы и гайд на примере дневника. «Назад» ведёт
 * гайд → тема → список → дневник. Тему отмечает открытой экран темы и гайд, пройденной — конец гайда.
 */
@Composable
fun HelpHost(
    route: HelpRoute,
    onRoute: (HelpRoute?) -> Unit,
    topics: List<HelpTopic>,
    progress: HelpProgress,
    onTopicOpened: (HelpTopic) -> Unit,
    onTopicCompleted: (HelpTopic) -> Unit,
    sessions: GuideSessions,
    theme: ThemeChoice,
    onThemeChange: (ThemeChoice) -> Unit,
    shadeEnabled: Boolean,
    onShadeChange: (Boolean) -> Unit,
) {
    val topic = route.topicId?.let { id -> topics.find { it.id == id } }
    if (topic == null) {
        if (route == HelpRoute.Topics) {
            HelpScreen(
                topics = topics,
                progress = progress,
                onOpenTopic = { onRoute(HelpRoute.Topic(it.id)) },
                onStartGuide = { onRoute(HelpRoute.Guide(it.id)) },
                onBack = { onRoute(null) },
            )
        } else {
            // Тему убрали из справки, а маршрут к ней восстановился после поворота — к списку
            LaunchedEffect(route) { onRoute(HelpRoute.Topics) }
        }
        return
    }
    LaunchedEffect(topic.id) { onTopicOpened(topic) }
    val next = topicAfter(topics, topic)
    if (route !is HelpRoute.Guide) {
        HelpTopicScreen(
            topic = topic,
            progress = progress,
            next = next,
            onTry = { onRoute(HelpRoute.Guide(topic.id)) },
            onOpenTopic = { onRoute(HelpRoute.Topic(it.id)) },
            onBack = { onRoute(HelpRoute.Topics) },
        )
        return
    }
    var sandbox by remember(topic.id) { mutableStateOf(sessions.open(topic)) }
    LaunchedEffect(sandbox, sandbox.controller.finished) {
        if (sandbox.controller.finished) onTopicCompleted(topic)
    }
    GuideScreen(
        sandbox = sandbox,
        next = next,
        onClose = {
            sessions.close()
            onRoute(HelpRoute.Topic(topic.id))
        },
        onRestart = { sandbox = sessions.restart(topic) },
        onNextTopic = {
            sessions.close()
            onRoute(HelpRoute.Guide(it.id))
        },
        theme = theme,
        onThemeChange = onThemeChange,
        shadeEnabled = shadeEnabled,
        onShadeChange = onShadeChange,
    )
}

/**
 * Гайд: панель шага сверху, под ней настоящий [DiaryScreen] на примере дневника. Часы и гайд примера
 * подставлены через [LocalClock] и [LocalGuide]. «Назад» сначала закрывает открытое в дневнике (листы,
 * разделы), потом — гайд.
 */
@Composable
private fun GuideScreen(
    sandbox: GuideSandbox,
    next: HelpTopic?,
    onClose: () -> Unit,
    onRestart: () -> Unit,
    onNextTopic: (HelpTopic) -> Unit,
    theme: ThemeChoice,
    onThemeChange: (ThemeChoice) -> Unit,
    shadeEnabled: Boolean,
    onShadeChange: (Boolean) -> Unit,
) {
    // Объявлен раньше «Назад» дневника, поэтому срабатывает после них
    BackHandler(onBack = onClose)
    CompositionLocalProvider(LocalGuide provides sandbox.controller, LocalClock provides sandbox.clock) {
        // Surface, а не фон: задаёт цвет текста под панелью, как Scaffold дневника
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                GuidePanel(sandbox.controller, next, onClose = onClose, onRestart = onRestart, onNextTopic = onNextTopic)
                Box(Modifier.weight(1f)) {
                    if (sandbox.ready) {
                        key(sandbox) {
                            DiaryScreen(
                                viewModel = sandbox.diary,
                                theme = theme,
                                onThemeChange = onThemeChange,
                                development = sandbox.development,
                                initialSection = sandbox.topic.start.section,
                                shadeEnabled = shadeEnabled,
                                onShadeChange = onShadeChange,
                                example = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val GuideStart.section: MainSection
    get() = when (this) {
        GuideStart.TODAY -> MainSection.TODAY
        GuideStart.DIARY -> MainSection.DIARY
        GuideStart.DEVELOPMENT -> MainSection.DEVELOPMENT
        GuideStart.ANALYTICS -> MainSection.ANALYTICS
    }
