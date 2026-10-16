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

    @Query("SELECT COUNT(*) FROM events WHERE childId = :childId AND type = 'SLEEP' AND endTime IS NULL")
    suspend fun countActiveSleeps(childId: Long): Int

    @Insert
    suspend fun insertEvent(event: EventEntity): Long

    @Update
    suspend fun updateEvent(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteEvent(eventId: Long)

    @Query("DELETE FROM events WHERE childId = :childId")
    suspend fun deleteEventsOf(childId: Long)
}
