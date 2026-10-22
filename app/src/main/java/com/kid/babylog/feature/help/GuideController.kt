package com.kid.babylog.feature.help

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.ui.guide.GuideTarget
import com.kid.babylog.core.ui.guide.GuideTracker
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong

/**
 * Ход гайда по теме (ADR-0043): какой шаг сейчас и засчитан ли он. Шаг засчитывается сам — по записям
 * примера ([onEvents]) или появлению цели ([shown]) — либо кнопкой шага ([press]). Пройденный шаг
 * не откатывается: остановленный потом таймер не возвращает гайд назад.
 */
class GuideController(val topic: HelpTopic, private val clock: GuideClock) : GuideTracker {

    var index by mutableIntStateOf(0)
        private set

    val step: GuideStep? get() = topic.steps.getOrNull(index)

    val finished: Boolean get() = index >= topic.steps.size

    private val onScreen = mutableStateMapOf<GuideTarget, Int>()

    /** null — записи примера ещё не пришли: шаги по записям ждут. */
    private var events: List<BabyEvent>? = null
    private var atStart: List<BabyEvent> = emptyList()

    override val target: GuideTarget? get() = step?.target

    override val sheetHint: String? get() = step?.let { it.sheetHint ?: it.text }

    override fun shown(target: GuideTarget) {
        onScreen[target] = (onScreen[target] ?: 0) + 1
        advance()
    }

    override fun hidden(target: GuideTarget) {
        val count = (onScreen[target] ?: 0) - 1
        if (count > 0) onScreen[target] = count else onScreen.remove(target)
    }

    /** Записи примера изменились. Первые — точка отсчёта первого шага. */
    fun onEvents(list: List<BabyEvent>) {
        if (events == null) atStart = list
        events = list
        advance()
    }

    /** Кнопка шага: перематывает часы примера на [GuideStep.skipMinutes] и засчитывает шаг. */
    fun press() {
        val current = step ?: return
        if (current.done != StepDone.Next) return
        clock.skip(current.skipMinutes)
        moveOn()
        advance()
    }

    private fun advance() {
        while (true) {
            val current = step ?: return
            if (!isDone(current.done)) return
            moveOn()
        }
    }

    private fun isDone(done: StepDone): Boolean = when (done) {
        is StepDone.Records -> events?.let { done.check(it, atStart) } ?: false
        is StepDone.Shown -> (onScreen[done.target] ?: 0) > 0
        StepDone.Next -> false
    }

    private fun moveOn() {
        index++
        atStart = events.orEmpty()
    }
}

/**
 * Часы примера дневника (ADR-0043): идут вместе с настоящими, а кнопка шага перематывает их вперёд —
 * «Прошло 40 минут» вместо ожидания. Записи примера получают время этих часов.
 */
class GuideClock(private val base: Clock) : Clock() {

    private val offset = AtomicLong(0)

    fun skip(minutes: Int) {
        offset.addAndGet(minutes * MINUTE)
    }

    override fun getZone(): ZoneId = base.zone

    override fun withZone(zone: ZoneId): Clock = offset(base.withZone(zone), Duration.ofMillis(offset.get()))

    override fun instant(): Instant = base.instant().plusMillis(offset.get())

    override fun millis(): Long = base.millis() + offset.get()

    private companion object {
        const val MINUTE = 60_000L
    }
}
