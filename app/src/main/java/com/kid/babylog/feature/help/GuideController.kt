package com.kid.babylog.feature.help

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.ui.guide.GuideTarget
import com.kid.babylog.core.ui.guide.GuideTracker
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong

/**
 * Ход гайда по теме (ADR-0043): какой шаг сейчас и засчитан ли он. Шаг засчитывается сам — по записям
 * и детям примера ([onEvents], [onChildren]), появлению и закрытию цели ([shown], [hidden]) — либо
 * кнопкой шага ([press]); шаг с действием можно пропустить ([skip]). Пройденный шаг не откатывается:
 * остановленный потом таймер не возвращает гайд назад.
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
    private var children: List<Child>? = null
    private var childrenAtStart: List<Child> = emptyList()

    /** Цели, которые были на экране во время текущего шага, — для [StepDone.Visited]. */
    private val visited = mutableSetOf<GuideTarget>()

    override val target: GuideTarget? get() = step?.target

    override val sheetHint: String? get() = step?.let { it.sheetHint ?: it.text }

    override fun shown(target: GuideTarget) {
        onScreen[target] = (onScreen[target] ?: 0) + 1
        visited += target
        advance()
    }

    override fun hidden(target: GuideTarget) {
        val count = (onScreen[target] ?: 0) - 1
        if (count > 0) onScreen[target] = count else onScreen.remove(target)
        advance()
    }

    /** Дети примера изменились. Первые — точка отсчёта первого шага. */
    fun onChildren(list: List<Child>) {
        if (children == null) childrenAtStart = list
        children = list
        advance()
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

    /** «Пропустить шаг»: шаг с действием засчитывается без него — гайд не застревает. */
    fun skip() {
        if (step == null) return
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
        is StepDone.Children -> children?.let { done.check(it, childrenAtStart) } ?: false
        is StepDone.Shown -> done.target in onScreen
        is StepDone.Visited -> done.target in visited && done.target !in onScreen
        StepDone.Next -> false
    }

    private fun moveOn() {
        index++
        atStart = events.orEmpty()
        childrenAtStart = children.orEmpty()
        // Что уже открыто, считается увиденным и в новом шаге: лист, открытый раньше, закроют в нём
        visited.clear()
        visited += onScreen.keys
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
