package com.kid.babylog.feature.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kid.babylog.core.data.DiaryRepository
import com.kid.babylog.core.data.SelectedChildStore
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.isNameTaken
import com.kid.babylog.core.model.isValidChildName
import com.kid.babylog.feature.transfer.DocumentStore
import com.kid.babylog.feature.transfer.describe
import com.kid.babylog.feature.transfer.exportCsv
import com.kid.babylog.feature.transfer.importCsv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock

/** Главный экран. Без Android-зависимостей: тестируется на JVM с фейками (ADR-0007). */
@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModel(
    private val repository: DiaryRepository,
    private val selection: SelectedChildStore,
    private val documents: DocumentStore,
    private val clock: Clock,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<DiaryUiState> =
        combine(repository.observeChildren(), selection.selectedChildId) { children, selectedId ->
            // Выбранного нет (не выбирали или удалён) — показываем первого
            children to (children.find { it.id == selectedId } ?: children.firstOrNull())
        }
            .flatMapLatest { (children, child) ->
                val events = if (child == null) flowOf(emptyList()) else repository.observeEvents(child.id)
                events.map { DiaryUiState.of(children, child, it) }
            }
            .combine(message) { state, text -> state.copy(message = text) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiaryUiState())

    fun selectChild(childId: Long) = selection.select(childId)

    /** Добавляет ребёнка и сразу выбирает его. Пустое или занятое имя игнорируется. */
    fun addChild(name: String, birthDate: Long?) = viewModelScope.launch {
        if (!isValidChildName(name) || repository.allChildren().isNameTaken(name)) return@launch
        selection.select(repository.addChild(name.trim(), birthDate))
    }

    fun updateChild(child: Child) = viewModelScope.launch {
        val children = repository.allChildren()
        if (!isValidChildName(child.name) || children.isNameTaken(child.name, exceptId = child.id)) return@launch
        repository.updateChild(child)
    }

    /** Удаляет ребёнка с его записями; последнего ребёнка удалить нельзя. */
    fun deleteChild(child: Child) = viewModelScope.launch {
        if (repository.deleteChild(child.id)) message.value = "Удалено: ${child.name}"
    }

    /** Новая запись (id = 0) добавляется, существующая обновляется. */
    fun saveEvent(event: BabyEvent) = viewModelScope.launch {
        if (event.id == 0L) repository.addEvent(event) else repository.updateEvent(event)
    }

    fun deleteEvent(event: BabyEvent) = viewModelScope.launch {
        repository.deleteEvent(event)
    }

    fun startSleep(start: Long) = viewModelScope.launch {
        val child = state.value.child ?: return@launch
        repository.startSleep(child.id, start)
    }

    fun endSleep() = viewModelScope.launch {
        val sleep = state.value.activeSleep ?: return@launch
        repository.updateEvent(sleep.copy(end = maxOf(clock.millis(), sleep.start)))
    }

    fun exportTo(uri: String) = viewModelScope.launch {
        message.value = runCatching {
            val export = exportCsv(repository)
            documents.write(uri, export.text)
            export.eventCount
        }.fold(
            onSuccess = { "Экспортировано записей: $it" },
            onFailure = { "Ошибка экспорта: ${it.message}" },
        )
    }

    /** Строки без колонки `child` (файлы старых версий) попадают к выбранному ребёнку. */
    fun importFrom(uri: String) = viewModelScope.launch {
        val child = state.value.child ?: return@launch
        message.value = runCatching { importCsv(repository, documents.read(uri), defaultChildId = child.id) }
            .fold(
                onSuccess = { it.describe() },
                onFailure = { "Ошибка импорта: ${it.message}" },
            )
    }

    fun consumeMessage() {
        message.value = null
    }
}
