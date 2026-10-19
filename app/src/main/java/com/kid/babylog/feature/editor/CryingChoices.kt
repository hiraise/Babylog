package com.kid.babylog.feature.editor

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.DEFAULT_CRYING_HELPED
import com.kid.babylog.core.model.DEFAULT_CRYING_REASONS
import com.kid.babylog.core.model.DEFAULT_NOTE_TAGS
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Note

/** Сколько вариантов показывает лист: дальше ряды чипов растут и сдвигают форму вниз. */
private const val MAX_REASONS = 10
private const val MAX_HELPED = 8
private const val MAX_NOTE_TAGS = 10

/**
 * Метки для листа (ADR-0031): [shown] — чипами: метки записей этого типа, частые первыми, потом
 * стандартные; [other] — метки записей другого типа (у плача — заметок, у заметки — плача): подсказки
 * в «своя метка». Словарь один, но чипы не смешиваются: «прививка» не теснит причины плача, а
 * «перевозбуждение» — метки заметок.
 */
data class TagChoices(val shown: List<String>, val other: List<String>)

/** Варианты для листа плача (ADR-0030): причины-метки и что помогло. Своя метка, раз появившись, дальше в чипах. */
data class CryingChoices(val reasons: TagChoices, val helped: List<String>)

/** Варианты из записей ребёнка [events]. */
fun cryingChoices(events: List<BabyEvent>): CryingChoices {
    val cryings = events.filterIsInstance<Crying>()
    return CryingChoices(
        reasons = tagChoices(cryings.flatMap { it.reasons }, events.filterIsInstance<Note>().flatMap { it.tags }, DEFAULT_CRYING_REASONS, MAX_REASONS),
        helped = ranked(cryings.flatMap { it.helped }, DEFAULT_CRYING_HELPED).take(MAX_HELPED),
    )
}

/** Метки для листа заметки (ADR-0031): метки заметок и свои стандартные; метки плача — подсказками. */
fun noteTagChoices(events: List<BabyEvent>): TagChoices =
    tagChoices(events.filterIsInstance<Note>().flatMap { it.tags }, events.filterIsInstance<Crying>().flatMap { it.reasons }, DEFAULT_NOTE_TAGS, MAX_NOTE_TAGS)

private fun tagChoices(own: List<String>, others: List<String>, defaults: List<String>, max: Int): TagChoices {
    val shown = ranked(own, defaults).take(max)
    return TagChoices(shown, ranked(others, emptyList()) - shown.toSet())
}

/** [used] по убыванию частоты (при равной — по алфавиту), затем не встречавшиеся [defaults]. */
private fun ranked(used: List<String>, defaults: List<String>): List<String> {
    val counts = used.groupingBy { it }.eachCount()
    val past = counts.keys.sortedWith(compareByDescending<String> { counts.getValue(it) }.thenBy { it })
    return (past + defaults).distinct()
}

/**
 * «Что было до» плача (ADR-0030): сколько не спал и сколько обычно не спит, сколько прошло после
 * кормления и чем кормили ([feeding]: «левая, 20 мин», «бутылочка 120 мл»), когда меняли подгузник.
 * Считает главный экран из дневника; null у поля — таких записей нет.
 */
data class CryingContext(
    val awakeMillis: Long?,
    val usualAwakeMillis: Long?,
    val sinceFeedingMillis: Long?,
    val feeding: String?,
    val feedingByBottle: Boolean,
    val sinceDiaperMillis: Long?,
    val diaper: DiaperKind?,
)
