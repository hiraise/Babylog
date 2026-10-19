package com.kid.babylog.core.data.room

import android.util.Log
import androidx.room.withTransaction
import com.kid.babylog.core.data.DiaryRepository
import com.kid.babylog.core.data.truncatedToSecond
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.ChildPhoto
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.EventPhoto
import com.kid.babylog.core.model.EventRecord
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.PhotoEdit
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDiaryRepository(private val db: AppDatabase) : DiaryRepository {

    private val dao = db.diaryDao()

    override fun observeChildren(): Flow<List<Child>> =
        dao.observeChildren().map { list -> list.map { it.toChild() } }

    override fun observeEvents(childId: Long): Flow<List<BabyEvent>> =
        dao.observeEvents(childId).map { it.toEvents() }

    override suspend fun allChildren(): List<Child> = dao.getChildren().map { it.toChild() }

    override suspend fun allEvents(): List<BabyEvent> = dao.getAllEvents().toEvents()

    override suspend fun addChild(name: String, birthDate: Long?, sex: Sex?, photo: ChildPhoto?): Long =
        dao.insertChild(Child(id = 0, name = name.trim(), birthDate = birthDate, sex = sex, photo = photo).toEntity())

    override suspend fun updateChild(child: Child) =
        dao.updateChild(child.copy(name = child.name.trim()).toEntity())

    override suspend fun deleteChild(childId: Long): Boolean = db.withTransaction {
        if (dao.countChildren() <= 1) return@withTransaction false
        // Явно, не полагаясь на ON DELETE CASCADE: внешние ключи SQLite могут быть выключены
        dao.deleteEventsOf(childId)
        dao.deleteChild(childId)
        true
    }

    override suspend fun addEvent(event: BabyEvent): Long = dao.insertEvent(event.toEntity().truncated().copy(id = 0))

    override suspend fun updateEvent(event: BabyEvent) = dao.updateEvent(event.toEntity().truncated())

    override suspend fun deleteEvent(event: BabyEvent) = dao.deleteEvent(event.id)

    override fun observeEventPhotos(eventId: Long): Flow<List<EventPhoto>> =
        dao.observeEventPhotos(eventId).map { list -> list.map { EventPhoto(it.id, it.jpeg) } }

    override fun observePhotoCounts(childId: Long): Flow<Map<Long, Int>> =
        dao.observePhotoCounts(childId).map { rows -> rows.associate { it.eventId to it.count } }

    override suspend fun setEventPhotos(eventId: Long, edit: PhotoEdit) = db.withTransaction {
        dao.deletePhotosExcept(eventId, edit.keep)
        edit.keep.forEachIndexed { position, id -> dao.movePhoto(id, eventId, position) }
        edit.add.forEachIndexed { i, jpeg ->
            dao.insertPhoto(EventPhotoEntity(eventId = eventId, position = edit.keep.size + i, jpeg = jpeg))
        }
    }

    override suspend fun startSleep(childId: Long, start: Long): Boolean = db.withTransaction {
        if (dao.countActive(childId, EventRecord.SLEEP) > 0) return@withTransaction false
        finishActive(childId, EventRecord.FEEDING, at = start)
        finishActive(childId, EventRecord.CRYING, at = start)
        addEvent(Sleep(childId = childId, start = start))
        true
    }

    override suspend fun startCrying(childId: Long, start: Long): Long? = db.withTransaction {
        if (dao.countActive(childId, EventRecord.CRYING) > 0) return@withTransaction null
        finishActive(childId, EventRecord.SLEEP, at = start)
        addEvent(Crying(childId = childId, start = start))
    }

    override suspend fun startFeeding(childId: Long, start: Long, breast: Breast): Boolean = db.withTransaction {
        if (dao.countActive(childId, EventRecord.FEEDING) > 0) return@withTransaction false
        finishActive(childId, EventRecord.SLEEP, at = start)
        addEvent(Feeding(childId = childId, start = start, breast = breast))
        true
    }

    /** Сон и кормление не идут одновременно (ADR-0020): идущее заканчивается в момент [at], но не раньше своего начала. */
    override suspend fun addBottle(bottle: Bottle) {
        db.withTransaction {
            val at = bottle.start.truncatedToSecond()
            dao.activeEvents(bottle.childId, EventRecord.SLEEP).filter { it.startTime <= at }.forEach { sleep ->
                dao.updateEvent(sleep.copy(endTime = at))
            }
            addEvent(bottle)
        }
    }

    private suspend fun finishActive(childId: Long, type: String, at: Long) {
        dao.activeEvents(childId, type).forEach { event ->
            dao.updateEvent(event.copy(endTime = maxOf(event.startTime, at.truncatedToSecond())))
        }
    }

    override suspend fun <T> inTransaction(block: suspend () -> T): T = db.withTransaction { block() }

    private fun EventEntity.truncated() = copy(
        startTime = startTime.truncatedToSecond(),
        endTime = endTime?.truncatedToSecond(),
    )

    private fun List<EventEntity>.toEvents(): List<BabyEvent> = mapNotNull { entity ->
        entity.toEvent() ?: run {
            Log.w("BabyLog", "Пропущена нераспознанная запись id=${entity.id} type=${entity.type}")
            null
        }
    }
}
