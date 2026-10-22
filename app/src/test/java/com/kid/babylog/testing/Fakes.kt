package com.kid.babylog.testing

import com.kid.babylog.core.data.DatabaseRecovery
import com.kid.babylog.core.data.DevelopmentSeenStore
import com.kid.babylog.core.data.DiaryRepository
import com.kid.babylog.core.data.RecoveryNotices
import com.kid.babylog.core.data.SelectedChildStore
import com.kid.babylog.core.data.ShadeMemo
import com.kid.babylog.core.data.ShadeStore
import com.kid.babylog.core.data.truncatedToSecond
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.ChildPhoto
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.EventPhoto
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.model.PhotoEdit
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.toEvent
import com.kid.babylog.core.model.toRecord
import com.kid.babylog.feature.development.DevelopmentViewModel
import com.kid.babylog.feature.development.MilestoneTable
import com.kid.babylog.feature.development.Tip
import com.kid.babylog.feature.development.TipSource
import com.kid.babylog.feature.development.TipsSource
import com.kid.babylog.feature.transfer.DocumentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate

/** Репозиторий в памяти с тем же поведением, что у Room-реализации. */
class FakeDiaryRepository(
    children: List<Child> = listOf(Child(1, Child.DEFAULT_NAME, null)),
) : DiaryRepository {

    val children = MutableStateFlow(children)
    val events = MutableStateFlow<List<BabyEvent>>(emptyList())
    private var nextChildId = (children.maxOfOrNull { it.id } ?: 0) + 1
    private var nextEventId = 1L

    /** Фото по id записи, по порядку. */
    val photos = MutableStateFlow<Map<Long, List<EventPhoto>>>(emptyMap())
    private var nextPhotoId = 1L

    /** Место фото в записи, как `position` в базе: импорт добавляет фото не по порядку. */
    private val positions = mutableMapOf<Long, Int>()

    override fun observeChildren(): Flow<List<Child>> = children

    override fun observeEvents(childId: Long): Flow<List<BabyEvent>> = events.map { list ->
        list.filter { it.childId == childId }
            .sortedWith(compareByDescending<BabyEvent> { it.start }.thenByDescending { it.id })
    }

    override suspend fun allChildren(): List<Child> = children.value

    override suspend fun allEvents(): List<BabyEvent> =
        events.value.sortedWith(compareBy<BabyEvent> { it.start }.thenBy { it.id })

    override suspend fun addChild(name: String, birthDate: Long?, sex: Sex?, photo: ChildPhoto?): Long {
        val id = nextChildId++
        children.value += Child(id, name.trim(), birthDate, sex, photo)
        return id
    }

    override suspend fun updateChild(child: Child) {
        children.value = children.value.map { if (it.id == child.id) child.copy(name = child.name.trim()) else it }
    }

    override suspend fun deleteChild(childId: Long): Boolean {
        if (children.value.size <= 1) return false
        children.value = children.value.filter { it.id != childId }
        val removed = events.value.filter { it.childId == childId }.map { it.id }.toSet()
        events.value = events.value.filter { it.childId != childId }
        photos.value -= removed
        return true
    }

    override suspend fun addEvent(event: BabyEvent): Long {
        val id = nextEventId++
        events.value += stored(event, id)
        return id
    }

    override suspend fun updateEvent(event: BabyEvent) {
        events.value = events.value.map { if (it.id == event.id) stored(event, event.id) else it }
    }

    override suspend fun deleteEvent(event: BabyEvent) {
        events.value = events.value.filter { it.id != event.id }
        photos.value -= event.id
    }

    override fun observeEventPhotos(eventId: Long): Flow<List<EventPhoto>> = photos.map { it[eventId].orEmpty() }

    override fun observePhotoCounts(childId: Long): Flow<Map<Long, Int>> = combine(events, photos) { list, byEvent ->
        val ids = list.filter { it.childId == childId }.map { it.id }.toSet()
        byEvent.filterKeys { it in ids }.filterValues { it.isNotEmpty() }.mapValues { it.value.size }
    }

    override suspend fun setEventPhotos(eventId: Long, edit: PhotoEdit) {
        val current = photos.value[eventId].orEmpty().associateBy { it.id }
        val kept = edit.keep.mapNotNull { current[it] }
        val list = kept + edit.add.map { EventPhoto(nextPhotoId++, it) }
        list.forEachIndexed { i, photo -> positions[photo.id] = i }
        photos.value += eventId to list
    }

    override suspend fun photoIds(): Map<Long, List<Long>> =
        photos.value.filterValues { it.isNotEmpty() }.mapValues { (_, list) -> list.map { it.id } }

    override suspend fun photoJpeg(photoId: Long): ByteArray? =
        photos.value.values.flatten().firstOrNull { it.id == photoId }?.jpeg

    override suspend fun addEventPhoto(eventId: Long, position: Int, jpeg: ByteArray) {
        val photo = EventPhoto(nextPhotoId++, jpeg)
        positions[photo.id] = position
        val list = photos.value[eventId].orEmpty() + photo
        photos.value += eventId to list.sortedWith(compareBy<EventPhoto> { positions[it.id] }.thenBy { it.id })
    }

    override suspend fun startSleep(childId: Long, start: Long): Boolean {
        if (events.value.any { it.childId == childId && it is Sleep && it.isActive }) return false
        events.value.filterIsInstance<Feeding>().filter { it.childId == childId && it.isActive }
            .forEach { updateEvent(it.copy(end = maxOf(it.start, start))) }
        events.value.filterIsInstance<Crying>().filter { it.childId == childId && it.isActive }
            .forEach { updateEvent(it.copy(end = maxOf(it.start, start))) }
        addEvent(Sleep(childId = childId, start = start))
        return true
    }

    override suspend fun startCrying(childId: Long, start: Long): Long? {
        if (events.value.any { it.childId == childId && it is Crying && it.isActive }) return null
        events.value.filterIsInstance<Sleep>().filter { it.childId == childId && it.isActive }
            .forEach { updateEvent(it.copy(end = maxOf(it.start, start))) }
        return addEvent(Crying(childId = childId, start = start))
    }

    override suspend fun startIllness(illness: Illness): Long? {
        if (events.value.any { it.childId == illness.childId && it is Illness && it.isActive }) return null
        return addEvent(illness.copy(end = null))
    }

    override suspend fun startFeeding(childId: Long, start: Long, breast: Breast): Boolean {
        if (events.value.any { it.childId == childId && it is Feeding && it.isActive }) return false
        events.value.filterIsInstance<Sleep>().filter { it.childId == childId && it.isActive }
            .forEach { updateEvent(it.copy(end = maxOf(it.start, start))) }
        addEvent(Feeding(childId = childId, start = start, breast = breast))
        return true
    }

    override suspend fun addBottle(bottle: Bottle) {
        val at = bottle.start.truncatedToSecond()
        events.value.filterIsInstance<Sleep>().filter { it.childId == bottle.childId && it.isActive && it.start <= at }
            .forEach { updateEvent(it.copy(end = at)) }
        addEvent(bottle)
    }

    override suspend fun <T> inTransaction(block: suspend () -> T): T = block()

    private fun stored(event: BabyEvent, id: Long): BabyEvent {
        val r = event.toRecord()
        return r.copy(start = r.start.truncatedToSecond(), end = r.end?.truncatedToSecond())
            .toEvent(id = id, childId = event.childId)!!
    }
}

class FakeSelectedChildStore(initial: Long? = null) : SelectedChildStore {
    override val selectedChildId = MutableStateFlow(initial)

    override fun select(childId: Long) {
        selectedChildId.value = childId
    }
}

/** Кнопки в шторке (ADR-0042) в памяти. */
class FakeShadeStore(enabled: Boolean = true, memo: ShadeMemo = ShadeMemo()) : ShadeStore {
    override val enabled = MutableStateFlow(enabled)
    override val memo = MutableStateFlow(memo)

    override fun setEnabled(enabled: Boolean) {
        this.enabled.value = enabled
    }

    override fun updateMemo(transform: (ShadeMemo) -> ShadeMemo) {
        memo.value = transform(memo.value)
    }
}

/** «Файлы» по uri в памяти; [opened] — сколько раз файл открывали на чтение. */
class FakeDocumentStore(val files: MutableMap<String, ByteArray> = mutableMapOf()) : DocumentStore {
    var opened = 0
        private set

    override suspend fun <T> read(uri: String, block: suspend (InputStream) -> T): T {
        val bytes = files[uri] ?: error("Нет файла $uri")
        opened++
        return block(ByteArrayInputStream(bytes))
    }

    override suspend fun <T> write(uri: String, block: suspend (OutputStream) -> T): T {
        val out = ByteArrayOutputStream()
        return block(out).also { files[uri] = out.toByteArray() }
    }
}

/** Сообщение о восстановлении базы в памяти (ADR-0033). */
class FakeRecoveryNotices : RecoveryNotices {
    override val pending = MutableStateFlow<DatabaseRecovery?>(null)

    override fun report(recovery: DatabaseRecovery) {
        pending.value = recovery
    }

    override fun dismiss() {
        pending.value = null
    }
}

/** Отметки «раздел „Развитие“ открывали» в памяти (ADR-0037). */
class FakeDevelopmentSeenStore(initial: Map<Long, Int> = emptyMap()) : DevelopmentSeenStore {
    override val seenWeeks = MutableStateFlow(initial)

    override fun markSeen(childId: Long, week: Int) {
        seenWeeks.value += childId to week
    }
}

/** Советы и окна навыков для тестов; по умолчанию — пусто: полосы советов на «Сегодня» нет. */
class FakeTipsSource(
    private val tips: List<Tip> = emptyList(),
    private val milestones: MilestoneTable = MilestoneTable(TipSource("ВОЗ", "https://www.who.int/", "Таблица"), LocalDate.of(2026, 10, 20), emptyList()),
) : TipsSource {
    override fun tips() = tips

    override fun milestones() = milestones
}

/** «Развитие» для тестов экрана: данные готовы сразу, без фонового потока. */
fun testDevelopment(
    source: TipsSource = FakeTipsSource(),
    seen: DevelopmentSeenStore = FakeDevelopmentSeenStore(),
) = DevelopmentViewModel(source, seen, io = Dispatchers.Unconfined)
