package com.kid.babylog.core.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {

    @Query("SELECT * FROM children ORDER BY id")
    fun observeChildren(): Flow<List<ChildEntity>>

    @Query("SELECT * FROM children ORDER BY id")
    suspend fun getChildren(): List<ChildEntity>

    @Query("SELECT COUNT(*) FROM children")
    suspend fun countChildren(): Int

    @Insert
    suspend fun insertChild(child: ChildEntity): Long

    @Update
    suspend fun updateChild(child: ChildEntity)

    @Query("DELETE FROM children WHERE id = :childId")
    suspend fun deleteChild(childId: Long)

    @Query("SELECT * FROM events WHERE childId = :childId ORDER BY startTime DESC, id DESC")
    fun observeEvents(childId: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events ORDER BY startTime ASC, id ASC")
    suspend fun getAllEvents(): List<EventEntity>

    /** Идущие таймеры ребёнка — записи типа [type] без конца: сон, кормление. */
    @Query("SELECT COUNT(*) FROM events WHERE childId = :childId AND type = :type AND endTime IS NULL")
    suspend fun countActive(childId: Long, type: String): Int

    @Query("SELECT * FROM events WHERE childId = :childId AND type = :type AND endTime IS NULL")
    suspend fun activeEvents(childId: Long, type: String): List<EventEntity>

    @Insert
    suspend fun insertEvent(event: EventEntity): Long

    @Update
    suspend fun updateEvent(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteEvent(eventId: Long)

    @Query("DELETE FROM events WHERE childId = :childId")
    suspend fun deleteEventsOf(childId: Long)

    @Query("SELECT * FROM event_photos WHERE eventId = :eventId ORDER BY position, id")
    fun observeEventPhotos(eventId: Long): Flow<List<EventPhotoEntity>>

    /** Без самих фото: только сколько их у каждой записи ребёнка. */
    @Query(
        "SELECT p.eventId AS eventId, COUNT(*) AS count FROM event_photos p " +
            "JOIN events e ON e.id = p.eventId WHERE e.childId = :childId GROUP BY p.eventId"
    )
    fun observePhotoCounts(childId: Long): Flow<List<PhotoCount>>

    @Query("DELETE FROM event_photos WHERE eventId = :eventId AND id NOT IN (:keep)")
    suspend fun deletePhotosExcept(eventId: Long, keep: List<Long>)

    @Query("UPDATE event_photos SET position = :position WHERE id = :id AND eventId = :eventId")
    suspend fun movePhoto(id: Long, eventId: Long, position: Int)

    @Insert
    suspend fun insertPhoto(photo: EventPhotoEntity)

    /** Без самих фото: id и запись каждого, по порядку внутри записи. */
    @Query("SELECT id, eventId FROM event_photos ORDER BY eventId, position, id")
    suspend fun getPhotoRefs(): List<PhotoRef>

    @Query("SELECT jpeg FROM event_photos WHERE id = :id")
    suspend fun getPhotoJpeg(id: Long): ByteArray?
}
