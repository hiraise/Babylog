package com.kid.babylog.core.data

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.ChildPhoto
import com.kid.babylog.core.model.EventPhoto
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.model.PhotoEdit
import com.kid.babylog.core.model.Sex
import kotlinx.coroutines.flow.Flow

/**
 * Единственный доступ к данным дневника (ADR-0007). Реализация — `RoomDiaryRepository`,
 * в тестах — `FakeDiaryRepository`. Время записей усекается до секунды здесь (ADR-0008).
 */
interface DiaryRepository {

    /** Дети в порядке добавления. Всегда есть хотя бы один ребёнок. */
    fun observeChildren(): Flow<List<Child>>

    /** Записи ребёнка, новые сверху (по времени, затем по id). */
    fun observeEvents(childId: Long): Flow<List<BabyEvent>>

    suspend fun allChildren(): List<Child>

    /** Все записи всех детей по возрастанию времени — для экспорта и импорта. */
    suspend fun allEvents(): List<BabyEvent>

    /** Возвращает id нового ребёнка. Имя проверяет вызывающий код ([com.kid.babylog.core.model.isNameTaken]). */
    suspend fun addChild(name: String, birthDate: Long?, sex: Sex?, photo: ChildPhoto?): Long

    suspend fun updateChild(child: Child)

    /** Удаляет ребёнка вместе с его записями. Последнего ребёнка не удаляет и возвращает false. */
    suspend fun deleteChild(childId: Long): Boolean

    /** Возвращает id новой записи. */
    suspend fun addEvent(event: BabyEvent): Long

    suspend fun updateEvent(event: BabyEvent)

    suspend fun deleteEvent(event: BabyEvent)

    /**
     * Начинает сон ребёнка, если он ещё не спит; false — активный сон уже есть. Идущее кормление
     * заканчивается в момент начала сна: сон и кормление не идут одновременно (ADR-0020). Идущий
     * плач тоже заканчивается: уснул — значит, успокоился (ADR-0030).
     */
    suspend fun startSleep(childId: Long, start: Long): Boolean

    /**
     * Начинает плач (ADR-0030), если он ещё не идёт, и возвращает id записи; null — плач уже идёт.
     * Идущий сон заканчивается в момент начала плача: плачет — значит, не спит. Кормлению плач не мешает.
     */
    suspend fun startCrying(childId: Long, start: Long): Long?

    /**
     * Начинает болезнь [illness] (ADR-0040) и возвращает id записи; null — у ребёнка уже идёт болезнь:
     * она одна. Таймеры сна и кормления болезнь не трогает.
     */
    suspend fun startIllness(illness: Illness): Long?

    /**
     * Начинает кормление ребёнка грудью [breast], если он ещё не ест; false — кормление уже идёт.
     * Идущий сон заканчивается в момент начала кормления (ADR-0020).
     */
    suspend fun startFeeding(childId: Long, start: Long, breast: Breast): Boolean

    /** Фото записи по порядку (ADR-0024). */
    fun observeEventPhotos(eventId: Long): Flow<List<EventPhoto>>

    /** Сколько фото у записей ребёнка: id записи → число; записей без фото в ней нет. */
    fun observePhotoCounts(childId: Long): Flow<Map<Long, Int>>

    /** Оставляет у записи фото [PhotoEdit.keep] в этом порядке, остальные удаляет, добавляет [PhotoEdit.add]. */
    suspend fun setEventPhotos(eventId: Long, edit: PhotoEdit)

    /**
     * Добавляет бутылочку (ADR-0025). Идущий сон, начатый не позже неё, заканчивается в её время:
     * кормят не во сне (ADR-0020). Сон, начатый позже, — бутылочку записали задним числом, он идёт.
     */
    suspend fun addBottle(bottle: Bottle)

    /** Выполняет [block] в одной транзакции: либо применяются все изменения, либо ни одного. */
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

/** Усечение времени записи до секунды: CSV хранит секунды, иначе ломается дедупликация импорта. */
fun Long.truncatedToSecond(): Long = Math.floorDiv(this, 1000L) * 1000L
