package com.kid.babylog.core.data

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Sleep
import kotlinx.coroutines.flow.first
import java.time.Clock

/** Идущие таймеры ребёнка: сон и кормление не идут вместе (ADR-0020), плач идёт с кормлением (ADR-0030). */
data class ActiveTimers(val sleep: Sleep? = null, val feeding: Feeding? = null, val crying: Crying? = null) {
    companion object {
        fun of(events: List<BabyEvent>) = ActiveTimers(
            sleep = events.firstNotNullOfOrNull { (it as? Sleep)?.takeIf(Sleep::isActive) },
            feeding = events.firstNotNullOfOrNull { (it as? Feeding)?.takeIf(Feeding::isActive) },
            crying = events.firstNotNullOfOrNull { (it as? Crying)?.takeIf(Crying::isActive) },
        )
    }
}

/** Начатый плач [cryingId] и сон [sleep], который он прервал: «Отменить» вернёт этот сон. */
data class StartedCrying(val cryingId: Long, val sleep: Sleep?)

/** Грудь для следующего кормления: другая, чем в последнем кормлении грудью; кормлений ещё не было — левая. */
fun nextBreast(events: List<BabyEvent>): Breast =
    events.filterIsInstance<Feeding>().maxByOrNull { it.start }?.breast?.opposite ?: Breast.LEFT

/**
 * Таймеры сна, кормления и плача — одни правила для экрана и уведомления в шторке (ADR-0042).
 * Что заканчивается при старте другого таймера, решает [DiaryRepository] (ADR-0020, ADR-0030);
 * здесь — остановка не раньше начала, «Сменить грудь» и возврат остановленного по ошибке (ADR-0021).
 * Идущие таймеры берутся из базы, а не с экрана: уведомление экрана не видит.
 */
class Timers(private val repository: DiaryRepository, private val clock: Clock) {

    suspend fun active(childId: Long): ActiveTimers = ActiveTimers.of(repository.observeEvents(childId).first())

    /** false — сон уже идёт. */
    suspend fun startSleep(childId: Long, start: Long = clock.millis()): Boolean = repository.startSleep(childId, start)

    /** false — кормление уже идёт. */
    suspend fun startFeeding(childId: Long, breast: Breast, start: Long = clock.millis()): Boolean =
        repository.startFeeding(childId, start, breast)

    /** Плач начинается сейчас; null — он уже идёт. */
    suspend fun startCrying(childId: Long): StartedCrying? {
        val sleep = active(childId).sleep
        val id = repository.startCrying(childId, clock.millis()) ?: return null
        return StartedCrying(id, sleep)
    }

    /** Идущий сон заканчивается сейчас, но не раньше своего начала; null — сон не шёл. */
    suspend fun endSleep(childId: Long): Sleep? =
        active(childId).sleep?.let { it.copy(end = endAt(it.start)) }?.also { repository.updateEvent(it) }

    suspend fun endFeeding(childId: Long): Feeding? =
        active(childId).feeding?.let { it.copy(end = endAt(it.start)) }?.also { repository.updateEvent(it) }

    suspend fun endCrying(childId: Long): Crying? =
        active(childId).crying?.let { it.copy(end = endAt(it.start)) }?.also { repository.updateEvent(it) }

    /** Идущее кормление заканчивается, с той же секунды начинается кормление другой грудью. */
    suspend fun switchBreast(childId: Long): Boolean {
        val feeding = active(childId).feeding ?: return false
        val now = endAt(feeding.start)
        repository.inTransaction {
            repository.updateEvent(feeding.copy(end = now))
            repository.startFeeding(feeding.childId, now, feeding.breast.opposite)
        }
        return true
    }

    /**
     * «Отменить» остановку (ADR-0021): сон или кормление [timer] в том виде, как шли до остановки, идут
     * дальше — если за это время не начался другой сон или кормление.
     */
    suspend fun resume(timer: BabyEvent): Boolean {
        val now = active(timer.childId)
        if (now.sleep != null || now.feeding != null) return false
        repository.updateEvent(timer)
        return true
    }

    /**
     * «Отменить» начало плача (ADR-0030): плач [cryingId] удаляется, прерванный им сон [sleep] идёт дальше —
     * на тех же условиях, что [resume].
     */
    suspend fun cancelCrying(childId: Long, cryingId: Long, sleep: Sleep?) {
        repository.observeEvents(childId).first().find { it.id == cryingId }?.let { repository.deleteEvent(it) }
        if (sleep != null) resume(sleep.copy(end = null))
    }

    private fun endAt(start: Long): Long = maxOf(clock.millis(), start)
}
