package com.kid.babylog.feature.help

import androidx.annotation.DrawableRes
import com.kid.babylog.core.data.HelpProgress
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.ui.guide.GuideTarget

/**
 * Тема справки (ADR-0043). Текст шагов — одновременно справка на экране темы и подсказки гайда,
 * который проходит тему на примере дневника.
 *
 * @property id не меняется: по нему помнятся открытые и пройденные темы.
 * @property start раздел примера, с которого начинается гайд.
 */
data class HelpTopic(
    val id: String,
    val title: String,
    @DrawableRes val icon: Int,
    val summary: String,
    val steps: List<GuideStep>,
    val start: GuideStart = GuideStart.TODAY,
)

/** Раздел главного экрана, с которого гайд открывает пример. */
enum class GuideStart { TODAY, DIARY, DEVELOPMENT, ANALYTICS }

/**
 * Шаг темы.
 *
 * @property text что сделать — так он читается и на экране темы, и в панели гайда.
 * @property target куда нажать: гайд обводит этот элемент.
 * @property sheetHint текст для открытого нижнего листа, если там нужно сказать другое, чем [text].
 * @property button подпись кнопки шага с [StepDone.Next]; null — «Далее».
 * @property skipMinutes на сколько кнопка шага перематывает часы примера.
 * @property demoOnly шаг только для гайда (перемотка времени): на экране темы его нет.
 * @property since версия, в которой шаг появился или изменился; null — шаг первой версии справки.
 */
data class GuideStep(
    val text: String,
    val done: StepDone,
    val target: GuideTarget? = null,
    val sheetHint: String? = null,
    val button: String? = null,
    val skipMinutes: Int = 0,
    val demoOnly: Boolean = false,
    val since: String? = null,
)

/**
 * Когда шаг засчитан. Засчитанный шаг не откатывается, даже если условие потом перестало выполняться.
 * Шаг, который не засчитывается кнопкой, можно пропустить («Пропустить шаг» на панели).
 */
sealed interface StepDone {
    /** По записям примера: [check] получает записи сейчас и в начале шага. */
    class Records(val check: (now: List<BabyEvent>, atStart: List<BabyEvent>) -> Boolean) : StepDone

    /** По детям примера: [check] получает детей сейчас и в начале шага. */
    class Children(val check: (now: List<Child>, atStart: List<Child>) -> Boolean) : StepDone

    /** Цель появилась на экране: открылся раздел, экран или лист. */
    data class Shown(val target: GuideTarget) : StepDone

    /** Цель была на экране во время шага и закрылась: лист открыли и выбрали в нём или закрыли. */
    data class Visited(val target: GuideTarget) : StepDone

    /** Кнопкой шага. */
    data object Next : StepDone
}

/** Шаги для экрана темы — без шагов только для гайда. */
val HelpTopic.readingSteps: List<GuideStep> get() = steps.filterNot { it.demoOnly }

/**
 * Шаги темы, помеченные «Новое»: новее версии, на которой тему открывали, а если не открывали —
 * новее первого запуска со справкой.
 */
fun HelpTopic.newSteps(progress: HelpProgress): Set<GuideStep> {
    val seen = progress.opened[id] ?: progress.baseline
    return steps.filter { step -> step.since != null && (seen == null || compareVersions(step.since, seen) > 0) }.toSet()
}

fun HelpTopic.isNew(progress: HelpProgress): Boolean = newSteps(progress).isNotEmpty()

/** Точка на ⋮ и у «Справки»: в справке есть новое или ещё не сказали, где она. */
fun helpBadge(topics: List<HelpTopic>, progress: HelpProgress): Boolean =
    !progress.pointerDone || topics.any { it.isNew(progress) }

/** С какой темы продолжать «по порядку»: первая непройденная, а если пройдены все — первая. */
fun nextUnfinished(topics: List<HelpTopic>, progress: HelpProgress): HelpTopic? =
    topics.firstOrNull { it.id !in progress.completed } ?: topics.firstOrNull()

/** Тема после [topic] в списке; null — это последняя. */
fun topicAfter(topics: List<HelpTopic>, topic: HelpTopic): HelpTopic? =
    topics.getOrNull(topics.indexOfFirst { it.id == topic.id } + 1)

/**
 * Сравнивает версии по числам: «1.10» новее «1.9». Хвост после «-» («1.9-debug») не считается,
 * нечисловая часть — ноль.
 */
fun compareVersions(a: String, b: String): Int {
    fun parts(version: String) = version.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val left = parts(a)
    val right = parts(b)
    for (i in 0 until maxOf(left.size, right.size)) {
        val diff = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
        if (diff != 0) return diff
    }
    return 0
}
