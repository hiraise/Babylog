package com.kid.babylog.feature.shade

import com.kid.babylog.core.data.CancelableCrying
import com.kid.babylog.core.data.DiaryRepository
import com.kid.babylog.core.data.ShadeStore
import com.kid.babylog.core.data.StoppedTimer
import com.kid.babylog.core.data.Timers
import com.kid.babylog.core.data.nextBreast
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Sleep
import kotlinx.coroutines.flow.first
import java.time.Clock

/** Кнопка уведомления в шторке (ADR-0042). [REFRESH] — не кнопка: уведомление перерисовывается по времени. */
enum class ShadeAction {
    START_SLEEP, END_SLEEP, START_FEEDING, END_FEEDING, SWITCH_BREAST, START_CRYING, END_CRYING, CANCEL_CRYING, RESUME, REFRESH
}

/**
 * Что делают кнопки уведомления. Правила таймеров — те же, что на экране ([Timers]); кроме записи в дневник
 * кнопка запоминает в [ShadeStore], что можно отменить и о каком плаче спросить причины.
 */
class ShadeCommands(
    private val repository: DiaryRepository,
    private val store: ShadeStore,
    private val clock: Clock,
) {
    private val timers = Timers(repository, clock)

    /** [eventId] — запись, к которой относится кнопка: остановленный таймер для [ShadeAction.RESUME], плач для [ShadeAction.CANCEL_CRYING]. */
    suspend fun perform(action: ShadeAction, childId: Long, eventId: Long = 0) {
        // Ребёнка могли удалить, пока уведомление висело
        if (repository.allChildren().none { it.id == childId }) return
        val now = clock.millis()
        when (action) {
            ShadeAction.START_SLEEP -> {
                val crying = timers.active(childId).crying
                if (timers.startSleep(childId)) {
                    // Уснул во время плача: плач закончился — его причины спросит строка «Отметить»
                    store.updateMemo { it.copy(stopped = null, startedCrying = null, finishedCryingId = crying?.id ?: it.finishedCryingId) }
                }
            }
            ShadeAction.END_SLEEP -> timers.endSleep(childId)?.let { sleep ->
                store.updateMemo { it.copy(stopped = StoppedTimer(sleep.id, now + STOP_UNDO_MS)) }
            }
            ShadeAction.START_FEEDING -> {
                val breast = nextBreast(repository.observeEvents(childId).first())
                if (timers.startFeeding(childId, breast)) store.updateMemo { it.copy(stopped = null, startedCrying = null) }
            }
            ShadeAction.END_FEEDING -> timers.endFeeding(childId)?.let { feeding ->
                store.updateMemo { it.copy(stopped = StoppedTimer(feeding.id, now + STOP_UNDO_MS)) }
            }
            ShadeAction.SWITCH_BREAST -> timers.switchBreast(childId)
            ShadeAction.START_CRYING -> timers.startCrying(childId)?.let { started ->
                val cancel = started.sleep?.let { CancelableCrying(started.cryingId, it.id, now + CANCEL_CRYING_MS) }
                store.updateMemo { it.copy(stopped = null, startedCrying = cancel, finishedCryingId = null) }
            }
            ShadeAction.END_CRYING -> timers.endCrying(childId)?.let { crying ->
                store.updateMemo { it.copy(startedCrying = null, finishedCryingId = crying.id) }
            }
            ShadeAction.CANCEL_CRYING -> {
                val cancel = store.memo.value.startedCrying?.takeIf { it.cryingId == eventId }
                store.updateMemo { it.copy(startedCrying = null) }
                if (cancel != null && cancel.until > now) {
                    val sleep = repository.observeEvents(childId).first().firstNotNullOfOrNull { (it as? Sleep)?.takeIf { s -> s.id == cancel.sleepId } }
                    timers.cancelCrying(childId, cancel.cryingId, sleep)
                }
            }
            ShadeAction.RESUME -> {
                val stopped = store.memo.value.stopped?.takeIf { it.eventId == eventId }
                store.updateMemo { it.copy(stopped = null) }
                if (stopped != null && stopped.until > now) {
                    val timer = repository.observeEvents(childId).first().firstNotNullOfOrNull { event ->
                        when {
                            event.id != eventId -> null
                            event is Sleep -> event.copy(end = null)
                            event is Feeding -> event.copy(end = null)
                            else -> null
                        }
                    }
                    if (timer != null) timers.resume(timer)
                }
            }
            ShadeAction.REFRESH -> Unit
        }
    }

    /** Лист «Плач закончился» открыт из уведомления: строка «Отметить» больше не нужна. */
    fun cryingDetailsOpened() = store.updateMemo { it.copy(finishedCryingId = null) }
}
