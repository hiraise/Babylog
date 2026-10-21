package com.kid.babylog.feature.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kid.babylog.core.data.DiaryRepository
import com.kid.babylog.core.data.SelectedChildStore
import com.kid.babylog.core.data.Timers
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.ChildPhoto
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.EventPhoto
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.model.IllnessName
import com.kid.babylog.core.model.Milk
import com.kid.babylog.core.model.PhotoEdit
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Temperature
import com.kid.babylog.core.model.isNameTaken
import com.kid.babylog.core.model.isValidChildName
import com.kid.babylog.core.ui.formatDuration
import com.kid.babylog.feature.transfer.DocumentStore
import com.kid.babylog.feature.transfer.describe
import com.kid.babylog.feature.transfer.exportCsv
import com.kid.babylog.feature.transfer.importCsv
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
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

    /** Правила таймеров — общие с уведомлением в шторке (ADR-0042). */
    private val timers = Timers(repository, clock)

    private val message = MutableStateFlow<String?>(null)

    /** Кнопка сообщения: что вернёт «Отменить». */
    private val action = MutableStateFlow<MessageAction?>(null)

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
            // Ошибка чтения базы не закрывает приложение: сообщение и новые попытки — база могла восстановиться
            // из копии (ADR-0033) или освободиться
            .retryWhen { cause, attempt ->
                if (cause is CancellationException) return@retryWhen false
                if (attempt == 0L) show(READ_ERROR)
                delay(READ_RETRY_MS * (attempt + 1).coerceAtMost(READ_RETRY_MAX_STEPS))
                true
            }
            // Сообщение видно, даже если дневник не прочитался ни разу
            .onStart { emit(DiaryUiState()) }
            .combine(message) { state, text -> state.copy(message = text) }
            .combine(action) { state, button -> state.copy(messageAction = button) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiaryUiState())

    fun selectChild(childId: Long) = selection.select(childId)

    /** Добавляет ребёнка и сразу выбирает его. Пустое или занятое имя игнорируется. */
    fun addChild(name: String, birthDate: Long?, sex: Sex?, photo: ChildPhoto?) = write {
        if (!isValidChildName(name) || repository.allChildren().isNameTaken(name)) return@write
        selection.select(repository.addChild(name.trim(), birthDate, sex, photo))
    }

    fun updateChild(child: Child) = write {
        val children = repository.allChildren()
        if (!isValidChildName(child.name) || children.isNameTaken(child.name, exceptId = child.id)) return@write
        repository.updateChild(child)
    }

    /** Удаляет ребёнка с его записями; последнего ребёнка удалить нельзя. */
    fun deleteChild(child: Child) = write {
        if (repository.deleteChild(child.id)) show("Удалено: ${child.name}")
    }

    /** Новая запись (id = 0) или правка; [photos] — фото после формы, null — фото не трогали (ADR-0024). */
    fun saveEvent(event: BabyEvent, photos: PhotoEdit? = null) = write {
        repository.inTransaction {
            val id = if (event.id == 0L) repository.addEvent(event) else event.id.also { repository.updateEvent(event) }
            if (photos != null) repository.setEventPhotos(id, photos)
        }
    }

    /** Фото записи — для формы правки и «Поделиться». */
    fun eventPhotos(eventId: Long): Flow<List<EventPhoto>> = repository.observeEventPhotos(eventId)

    fun deleteEvent(event: BabyEvent) = write {
        repository.deleteEvent(event)
    }

    /** Сон начат; идущий плач при этом закончился — его подробности спросит лист, который откроет экран (ADR-0030). */
    fun startSleep(start: Long) = write {
        val child = state.value.child ?: return@write
        repository.startSleep(child.id, start)
    }

    fun endSleep() = write {
        val child = state.value.child ?: return@write
        val sleep = timers.endSleep(child.id) ?: return@write
        show("Сон: ${formatDuration((sleep.end ?: sleep.start) - sleep.start)}", MessageAction.ResumeTimer(sleep.copy(end = null)))
    }

    /**
     * Плач (ADR-0030): таймер идёт сразу, без листа старта — плачущему ребёнку не до настроек.
     * Идущий сон заканчивается: плачет — значит, не спит. Плитка широкая и стоит под сном, поэтому
     * «Отменить» в сообщении убирает случайный плач и возвращает сон.
     */
    fun startCrying() = write {
        val child = state.value.child ?: return@write
        val started = timers.startCrying(child.id) ?: return@write
        show(
            if (started.sleep != null) "Плач начат, сон закончен" else "Плач начат",
            MessageAction.CancelCrying(started.cryingId, started.sleep),
        )
    }

    /**
     * Плач закончился. Лист «Плач закончился» экран откроет сам — по id записи, когда у неё появится конец;
     * «Отменить» начала плача после этого не нужен — его заменяет «Ещё плачет» в листе.
     */
    fun endCrying() = write {
        val child = state.value.child ?: return@write
        val crying = state.value.activeCrying
        if (crying != null && (action.value as? MessageAction.CancelCrying)?.cryingId == crying.id) consumeMessage()
        timers.endCrying(child.id)
    }

    /**
     * Подробности плача из листа «Плач закончился». Начало сдвинули раньше — сон, который плач закончил
     * своим началом, заканчивается тогда же: проснулся, когда заплакал.
     */
    fun saveCryingDetails(crying: Crying, start: Long, reasons: List<String>, helped: List<String>, note: String) = write {
        val woke = state.value.events.filterIsInstance<Sleep>().filter { it.end == crying.start && it.start < crying.start }
        repository.inTransaction {
            if (start < crying.start) woke.forEach { repository.updateEvent(it.copy(end = maxOf(it.start, start))) }
            repository.updateEvent(crying.copy(start = start, reasons = reasons, helped = helped, note = note))
        }
    }

    /** «Ещё плачет»: остановленный по ошибке плач идёт дальше, если за это время не начался другой. */
    fun resumeCrying(crying: Crying) = write {
        if (state.value.activeCrying == null) repository.updateEvent(crying.copy(end = null))
    }

    /**
     * Болезнь началась (ADR-0040): [names] — с начала болезни, [temperatureTenths] — замер «сейчас» с местом
     * прошлого замера. Идущая болезнь уже есть — ничего: она у ребёнка одна.
     */
    fun startIllness(start: Long, names: List<String>, note: String, temperatureTenths: Int?) = write {
        val child = state.value.child ?: return@write
        val method = state.value.lastTemperature?.method
        repository.inTransaction {
            val illness = Illness(childId = child.id, start = start, names = names.map { IllnessName(it) }, note = note)
            repository.startIllness(illness) ?: return@inTransaction
            if (temperatureTenths != null) {
                repository.addEvent(Temperature(childId = child.id, start = maxOf(clock.millis(), start), tenths = temperatureTenths, method = method))
            }
        }
    }

    /** «Прошлая вернулась» или «Ещё болеет»: у болезни снова нет конца — если другой идущей нет. */
    fun resumeIllness(illness: Illness) = write {
        if (state.value.activeIllness == null) repository.updateEvent(current(illness).copy(end = null))
    }

    /** «Выздоровел(а)»: болезнь заканчивается в [end], но не раньше начала. */
    fun recoverIllness(illness: Illness, end: Long) = write {
        val fresh = current(illness)
        repository.updateEvent(fresh.copy(end = maxOf(end, fresh.start)))
    }

    /** Новое название болезни ([old] = null) или правка: названия без повторов без учёта регистра. */
    fun saveIllnessName(illness: Illness, old: IllnessName?, name: IllnessName) = write {
        val fresh = current(illness)
        val names = if (old == null) fresh.names + name else fresh.names.map { if (it == old) name else it }
        repository.updateEvent(fresh.copy(names = names.distinctBy { it.name.lowercase() }))
    }

    fun removeIllnessName(illness: Illness, name: IllnessName) = write {
        val fresh = current(illness)
        repository.updateEvent(fresh.copy(names = fresh.names - name))
    }

    /**
     * Замер температуры (ADR-0041), новый или правка; [startIllness] — «Заболел(а)» в листе: болезнь начинается
     * с этого замера, если идущей ещё нет.
     */
    fun saveTemperature(temperature: Temperature, startIllness: Boolean) = write {
        val child = state.value.child ?: return@write
        repository.inTransaction {
            if (temperature.id == 0L) repository.addEvent(temperature.copy(childId = child.id)) else repository.updateEvent(temperature)
            if (startIllness) repository.startIllness(Illness(childId = child.id, start = temperature.start))
        }
    }

    /** Свежая запись болезни: лист мог открыться до того, как в ней что-то поменялось. */
    private fun current(illness: Illness): Illness = state.value.events.firstNotNullOfOrNull { (it as? Illness)?.takeIf { found -> found.id == illness.id } } ?: illness

    fun startFeeding(start: Long, breast: Breast) = write {
        val child = state.value.child ?: return@write
        timers.startFeeding(child.id, breast, start)
    }

    /** Бутылочка выбранному ребёнку (ADR-0025): разовая запись, идущий сон заканчивается (ADR-0020). */
    fun addBottle(volumeMl: Int, milk: Milk?, time: Long) = write {
        val child = state.value.child ?: return@write
        repository.addBottle(Bottle(childId = child.id, start = time, volumeMl = volumeMl, milk = milk))
    }

    /** Смена груди: идущее кормление заканчивается, с той же секунды начинается кормление другой грудью. */
    fun switchBreast() = write {
        val child = state.value.child ?: return@write
        timers.switchBreast(child.id)
    }

    fun endFeeding() = write {
        val child = state.value.child ?: return@write
        val feeding = timers.endFeeding(child.id) ?: return@write
        show("Кормление: ${formatDuration((feeding.end ?: feeding.start) - feeding.start)}", MessageAction.ResumeTimer(feeding.copy(end = null)))
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
     * Снекбар закрыт. [performed] — нажали его кнопку. «Отменить» остановку: таймер идёт дальше, если
     * за это время не начался другой (сон и кормление не идут одновременно, ADR-0020). «Отменить» начало
     * плача: плач удаляется, сон идёт дальше на тех же условиях.
     */
    fun consumeMessage(performed: Boolean = false) {
        val button = action.value
        action.value = null
        message.value = null
        if (!performed) return
        when (button) {
            is MessageAction.ResumeTimer -> write { timers.resume(button.timer) }
            is MessageAction.CancelCrying -> write {
                val child = state.value.child ?: return@write
                timers.cancelCrying(child.id, button.cryingId, button.sleep)
            }
            null -> Unit
        }
    }

    /**
     * Изменение дневника в фоне. Ошибка базы (чаще всего — заполнена память телефона) становится сообщением,
     * а не закрывает приложение: вместе с ним пропало бы и то, что ввели в форму.
     */
    private fun write(block: suspend CoroutineScope.() -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            show(WRITE_ERROR)
        }
    }

    /** Сообщение для снекбара; [button] — что сделает его кнопка. */
    private fun show(text: String, button: MessageAction? = null) {
        action.value = button
        message.value = text
    }
}

private const val WRITE_ERROR = "Не удалось сохранить. Проверьте, есть ли свободное место в памяти телефона"
private const val READ_ERROR = "Не удалось прочитать дневник. Пробую ещё раз…"
private const val READ_RETRY_MS = 1_000L
private const val READ_RETRY_MAX_STEPS = 10L
