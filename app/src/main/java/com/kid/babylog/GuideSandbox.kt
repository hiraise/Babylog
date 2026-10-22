package com.kid.babylog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kid.babylog.core.data.DiaryRepository
import com.kid.babylog.core.data.MemoryDevelopmentSeenStore
import com.kid.babylog.core.data.MemorySelectedChildStore
import com.kid.babylog.feature.development.DevelopmentViewModel
import com.kid.babylog.feature.development.TipsSource
import com.kid.babylog.feature.diary.DiaryViewModel
import com.kid.babylog.feature.help.GuideClock
import com.kid.babylog.feature.help.GuideController
import com.kid.babylog.feature.help.HelpTopic
import com.kid.babylog.feature.help.fillSampleDiary
import com.kid.babylog.feature.transfer.DocumentStore
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Пример дневника для гайда справки (ADR-0043): те же ViewModel, что в [AppContainer], но над
 * [repository] примера (в приложении — база в памяти), со своими часами [GuideClock], выбором ребёнка
 * и «Развитием». В настоящую базу и настройки дневника ничего не пишет. [close] останавливает ViewModel
 * примера и вызывает [onClose] — там закрывают базу.
 */
class GuideSandbox(
    val topic: HelpTopic,
    repository: DiaryRepository,
    tips: TipsSource,
    documents: DocumentStore,
    baseClock: Clock,
    private val onClose: () -> Unit = {},
    main: CoroutineDispatcher = Dispatchers.Main,
    io: CoroutineDispatcher = Dispatchers.IO,
) {
    val clock = GuideClock(baseClock)

    val controller = GuideController(topic, clock)

    private val store = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + main)
    private val selection = MemorySelectedChildStore()

    private val provider = ViewModelProvider.create(
        store,
        viewModelFactory {
            initializer { DiaryViewModel(repository, selection, documents, clock) }
            initializer { DevelopmentViewModel(tips, MemoryDevelopmentSeenStore(), io) }
        },
    )

    val diary: DiaryViewModel = provider[DiaryViewModel::class]

    val development: DevelopmentViewModel = provider[DevelopmentViewModel::class]

    /** Записи примера уже в базе: до этого экран дневника не показывают — он мелькнул бы пустым. */
    var ready by mutableStateOf(false)
        private set

    init {
        scope.launch {
            val childId = fillSampleDiary(repository, clock.millis(), clock.zone)
            selection.select(childId)
            ready = true
            repository.observeEvents(childId).collect(controller::onEvents)
        }
    }

    fun close() {
        scope.cancel()
        store.clear()
        onClose()
    }
}

/**
 * Пример дневника идущего гайда: переживает поворот экрана, закрывается, когда гайд закрыли или
 * Activity закончилась. [create] собирает пример для темы.
 */
class GuideSessions(private val create: (HelpTopic) -> GuideSandbox) : ViewModel() {

    private var current: GuideSandbox? = null

    /** Пример для [topic]: тот же, если гайд по ней уже идёт (после поворота), иначе — новый. */
    fun open(topic: HelpTopic): GuideSandbox =
        current?.takeIf { it.topic.id == topic.id } ?: restart(topic)

    /** Пример заново: «Ещё раз» в конце гайда. */
    fun restart(topic: HelpTopic): GuideSandbox {
        close()
        return create(topic).also { current = it }
    }

    fun close() {
        current?.close()
        current = null
    }

    override fun onCleared() = close()
}
