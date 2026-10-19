package com.kid.babylog.testing

import com.kid.babylog.core.data.DatabaseRecovery
import com.kid.babylog.core.data.DiaryRepository
import com.kid.babylog.core.data.RecoveryNotices
import com.kid.babylog.core.data.SelectedChildStore
import com.kid.babylog.core.data.truncatedToSecond
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.ChildPhoto
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.EventPhoto
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.PhotoEdit
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.toEvent
import com.kid.babylog.core.model.toRecord
import com.kid.babylog.feature.transfer.DocumentStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

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
        photos.value += eventId to kept + edit.add.map { EventPhoto(nextPhotoId++, it) }
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

/** «Файлы» по uri в памяти. */
class FakeDocumentStore(val files: MutableMap<String, String> = mutableMapOf()) : DocumentStore {
    override suspend fun read(uri: String): String = files[uri] ?: error("Нет файла $uri")

    override suspend fun write(uri: String, text: String) {
        files[uri] = text
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
