package com.kid.babylog.feature.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kid.babylog.core.data.DiaryRepository
import com.kid.babylog.core.data.SelectedChildStore
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.ChildPhoto
import com.kid.babylog.core.model.EventPhoto
import com.kid.babylog.core.model.Milk
import com.kid.babylog.core.model.PhotoEdit
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.isNameTaken
import com.kid.babylog.core.model.isValidChildName
import com.kid.babylog.core.ui.formatDuration
import com.kid.babylog.feature.transfer.DocumentStore
import com.kid.babylog.feature.transfer.describe
import com.kid.babylog.feature.transfer.exportCsv
import com.kid.babylog.feature.transfer.importCsv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

    /** Таймер до остановки касанием карточки: его возвращает «Отменить» в снекбаре (ADR-0021). */
    private val stopped = MutableStateFlow<BabyEvent?>(null)

    val state: StateFlow<DiaryUiState> =
        combine(repository.observeChildren(), selection.selectedChildId) { children, selectedId ->
            // Выбранного нет (не выбирали или удалён) — показываем первого
            children to (children.find { it.id == selectedId } ?: children.firstOrNull())
        }
            .flatMapLatest { (children, child) ->
                val events = if (child == null) flowOf(emptyList()) else repository.observeEvents(child.id)
                val photos = if (child == null) flowOf(emptyMap()) else repository.observePhotoCounts(child.id)
                combine(events, photos) { list, counts -> DiaryUiState.of(children, child, list, counts) }
            }
            .combine(message) { state, text -> state.copy(message = text) }
            .combine(stopped) { state, timer -> state.copy(canUndo = timer != null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiaryUiState())

    fun selectChild(childId: Long) = selection.select(childId)

    /** Добавляет ребёнка и сразу выбирает его. Пустое или занятое имя игнорируется. */
    fun addChild(name: String, birthDate: Long?, sex: Sex?, photo: ChildPhoto?) = viewModelScope.launch {
        if (!isValidChildName(name) || repository.allChildren().isNameTaken(name)) return@launch
        selection.select(repository.addChild(name.trim(), birthDate, sex, photo))
    }

    fun updateChild(child: Child) = viewModelScope.launch {
        val children = repository.allChildren()
        if (!isValidChildName(child.name) || children.isNameTaken(child.name, exceptId = child.id)) return@launch
        repository.updateChild(child)
    }

    /** Удаляет ребёнка с его записями; последнего ребёнка удалить нельзя. */
    fun deleteChild(child: Child) = viewModelScope.launch {
        if (repository.deleteChild(child.id)) show("Удалено: ${child.name}")
    }

    /** Новая запись (id = 0) добавляется, существующая обновляется. */
    /** Новая запись (id = 0) или правка; [photos] — фото после формы, null — фото не трогали (ADR-0024). */
    fun saveEvent(event: BabyEvent, photos: PhotoEdit? = null) = viewModelScope.launch {
        repository.inTransaction {
            val id = if (event.id == 0L) repository.addEvent(event) else event.id.also { repository.updateEvent(event) }
            if (photos != null) repository.setEventPhotos(id, photos)
        }
    }

    /** Фото записи — для формы правки и «Поделиться». */
    fun eventPhotos(eventId: Long): Flow<List<EventPhoto>> = repository.observeEventPhotos(eventId)

    fun deleteEvent(event: BabyEvent) = viewModelScope.launch {
        repository.deleteEvent(event)
    }

    fun startSleep(start: Long) = viewModelScope.launch {
        val child = state.value.child ?: return@launch
        repository.startSleep(child.id, start)
    }

    fun endSleep() = viewModelScope.launch {
        val sleep = state.value.activeSleep ?: return@launch
        val end = maxOf(clock.millis(), sleep.start)
        repository.updateEvent(sleep.copy(end = end))
        show("Сон: ${formatDuration(end - sleep.start)}", undo = sleep)
    }

    fun startFeeding(start: Long, breast: Breast) = viewModelScope.launch {
        val child = state.value.child ?: return@launch
        repository.startFeeding(child.id, start, breast)
    }

    /** Бутылочка выбранному ребёнку (ADR-0025): разовая запись, идущий сон заканчивается (ADR-0020). */
    fun addBottle(volumeMl: Int, milk: Milk?, time: Long) = viewModelScope.launch {
        val child = state.value.child ?: return@launch
        repository.addBottle(Bottle(childId = child.id, start = time, volumeMl = volumeMl, milk = milk))
    }

    /** Смена груди: идущее кормление заканчивается, с той же секунды начинается кормление другой грудью. */
    fun switchBreast() = viewModelScope.launch {
        val feeding = state.value.activeFeeding ?: return@launch
        val now = maxOf(clock.millis(), feeding.start)
        repository.inTransaction {
            repository.updateEvent(feeding.copy(end = now))
            repository.startFeeding(feeding.childId, now, feeding.breast.opposite)
        }
    }

    fun endFeeding() = viewModelScope.launch {
        val feeding = state.value.activeFeeding ?: return@launch
        val end = maxOf(clock.millis(), feeding.start)
        repository.updateEvent(feeding.copy(end = end))
        show("Кормление: ${formatDuration(end - feeding.start)}", undo = feeding)
    }

    fun exportTo(uri: String) = viewModelScope.launch {
        show(runCatching {
            val export = exportCsv(repository)
            documents.write(uri, export.text)
            export.eventCount
        }.fold(
            onSuccess = { "Экспортировано записей: $it" },
            onFailure = { "Ошибка экспорта: ${it.message}" },
        ))
    }

    /** Строки без колонки `child` (файлы старых версий) попадают к выбранному ребёнку. */
    fun importFrom(uri: String) = viewModelScope.launch {
        val child = state.value.child ?: return@launch
        show(runCatching { importCsv(repository, documents.read(uri), defaultChildId = child.id) }
            .fold(
                onSuccess = { it.describe() },
                onFailure = { "Ошибка импорта: ${it.message}" },
            ))
    }

    /**
     * Снекбар закрыт. [undo] — нажали «Отменить»: остановленный таймер идёт дальше, если за это время
     * не начался другой (сон и кормление не идут одновременно, ADR-0020).
     */
    fun consumeMessage(undo: Boolean = false) {
        val timer = stopped.value
        stopped.value = null
        message.value = null
        if (undo && timer != null) viewModelScope.launch {
            val current = state.value
            if (current.activeSleep == null && current.activeFeeding == null) repository.updateEvent(timer)
        }
    }

    /** Сообщение для снекбара; [undo] — таймер, который вернёт «Отменить». */
    private fun show(text: String, undo: BabyEvent? = null) {
        stopped.value = undo
        message.value = text
    }
}
