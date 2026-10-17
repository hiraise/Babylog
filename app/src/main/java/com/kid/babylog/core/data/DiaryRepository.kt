package com.kid.babylog.core.data

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
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
    suspend fun addChild(name: String, birthDate: Long?, sex: Sex?): Long

    suspend fun updateChild(child: Child)

    /** Удаляет ребёнка вместе с его записями. Последнего ребёнка не удаляет и возвращает false. */
    suspend fun deleteChild(childId: Long): Boolean

    suspend fun addEvent(event: BabyEvent)

    suspend fun updateEvent(event: BabyEvent)

    suspend fun deleteEvent(event: BabyEvent)

    /**
     * Начинает сон ребёнка, если он ещё не спит; false — активный сон уже есть. Идущее кормление
     * заканчивается в момент начала сна: сон и кормление не идут одновременно (ADR-0020).
     */
    suspend fun startSleep(childId: Long, start: Long): Boolean

    /**
     * Начинает кормление ребёнка грудью [breast], если он ещё не ест; false — кормление уже идёт.
     * Идущий сон заканчивается в момент начала кормления (ADR-0020).
     */
    suspend fun startFeeding(childId: Long, start: Long, breast: Breast): Boolean

    /** Выполняет [block] в одной транзакции: либо применяются все изменения, либо ни одного. */
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

/** Усечение времени записи до секунды: CSV хранит секунды, иначе ломается дедупликация импорта. */
fun Long.truncatedToSecond(): Long = Math.floorDiv(this, 1000L) * 1000L
