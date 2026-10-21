package com.kid.babylog.feature.shade

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kid.babylog.core.data.DiaryRepository
import com.kid.babylog.core.data.SelectedChildStore
import com.kid.babylog.core.data.ShadeStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Уведомление с кнопками в шторке (ADR-0042): держит его в согласии с дневником и выполняет его кнопки.
 * Дневник меняют только экран и кнопки — оба в процессе приложения, — поэтому, пока процесс жив, хватает
 * подписки [start]; после перезагрузки телефона и нажатий уведомление перерисовывает [refresh].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShadeController(
    context: Context,
    private val repository: DiaryRepository,
    private val selection: SelectedChildStore,
    private val store: ShadeStore,
    private val clock: Clock,
    receiver: Class<out BroadcastReceiver>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val commands = ShadeCommands(repository, store, clock)
    private val notifier = ShadeNotifier(context.applicationContext, receiver, clock)
    private val started = AtomicBoolean(false)

    /** Что показать сейчас; null — уведомление выключено в меню. Ребёнок — выбранный на экране. */
    private val content: Flow<ShadeContent?> = store.enabled.flatMapLatest { enabled ->
        if (!enabled) return@flatMapLatest flowOf(null)
        combine(repository.observeChildren(), selection.selectedChildId) { children, id -> children.find { it.id == id } ?: children.firstOrNull() }
            .distinctUntilChanged()
            .flatMapLatest { child ->
                if (child == null) flowOf(null)
                else combine(repository.observeEvents(child.id), store.memo) { events, memo -> shadeContent(child, events, memo, clock.millis()) }
            }
    }

    /** Следить за дневником, пока жив процесс. Повторный вызов ничего не делает. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            content
                // Ошибка чтения базы не должна навсегда остановить уведомление: база могла восстановиться (ADR-0033)
                .retryWhen { cause, _ ->
                    if (cause is CancellationException) return@retryWhen false
                    Log.w(TAG, "Уведомление в шторке: не прочитался дневник", cause)
                    delay(RETRY_MS)
                    true
                }
                .distinctUntilChanged()
                .collect(notifier::show)
        }
    }

    /** Перерисовать уведомление сейчас; [done] — когда закончено (для `BroadcastReceiver.goAsync`). */
    fun refresh(done: () -> Unit = {}) {
        scope.launch {
            try {
                notifier.show(content.first())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Уведомление в шторке не обновилось", e)
            } finally {
                done()
            }
        }
    }

    /** Нажатие кнопки уведомления: запись в дневник, затем уведомление перерисовывается. */
    fun handle(intent: Intent, done: () -> Unit) {
        val press = ShadeIntents.parse(intent)
        scope.launch {
            try {
                if (press != null) commands.perform(press.action, press.childId, press.eventId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Чаще всего — нет места в памяти телефона: запись не прошла, уведомление покажет, как есть
                Log.w(TAG, "Кнопка уведомления в шторке: ${press?.action}", e)
            } finally {
                refresh(done)
            }
        }
    }

    /** «Отметить» открыл лист «Плач закончился»: строка в уведомлении больше не нужна. */
    fun cryingDetailsOpened() = commands.cryingDetailsOpened()

    private companion object {
        const val TAG = "BabyLog"
        const val RETRY_MS = 5_000L
    }
}
