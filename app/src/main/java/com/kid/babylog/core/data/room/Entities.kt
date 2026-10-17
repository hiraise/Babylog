package com.kid.babylog.core.data.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.EventRecord
import com.kid.babylog.core.model.toEvent
import com.kid.babylog.core.model.toRecord

@Entity(tableName = "children")
data class ChildEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val birthDate: Long?,
    /** Код [com.kid.babylog.core.model.Sex], null — пол не указан. */
    val sex: String? = null,
)

/** Записи всех типов в одной таблице; смысл `detail` по типам — в [EventRecord] и ADR-0008. */
@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = ChildEntity::class,
            parentColumns = ["id"],
            childColumns = ["childId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("childId")],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long,
    val type: String,
    val startTime: Long,
    val endTime: Long?,
    val detail: String,
    val note: String,
)

fun ChildEntity.toChild() = Child(id = id, name = name, birthDate = birthDate, sex = Sex.fromCode(sex))

fun Child.toEntity() = ChildEntity(id = id, name = name, birthDate = birthDate, sex = sex?.name)

/** null — строка не разбирается (такого не пишет ни одна версия приложения). */
fun EventEntity.toEvent(): BabyEvent? =
    EventRecord(type, startTime, endTime, detail, note).toEvent(id = id, childId = childId)

fun BabyEvent.toEntity(): EventEntity {
    val record = toRecord()
    return EventEntity(
        id = id,
        childId = childId,
        type = record.type,
        startTime = record.start,
        endTime = record.end,
        detail = record.detail,
        note = record.note,
    )
}
