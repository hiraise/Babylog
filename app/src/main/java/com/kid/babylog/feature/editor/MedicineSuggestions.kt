package com.kid.babylog.feature.editor

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Medicine

/** Больше подсказок под полем не нужно: они растут вниз и сдвигают остальную форму. */
private const val MAX_SUGGESTIONS = 3

private val WORD_BREAK = Regex("[^\\p{L}\\p{N}]+")

/**
 * Названия лекарств из записей [events] без повторов, недавние первыми. Названия, которые
 * различаются только регистром или «ё», — одно лекарство: остаётся последнее написание.
 */
fun pastMedicineNames(events: List<BabyEvent>): List<String> =
    events.filterIsInstance<Medicine>()
        .sortedByDescending { it.start }
        .map { it.name.trim() }
        .distinctBy(::normalized)

/**
 * Подсказки к набранному [query] из [names] (недавние первыми): сначала названия, которые
 * начинаются с [query], затем те, где с него начинается другое слово («D» → «Витамин D»).
 * Регистр и «ё» не различаются. Пустой ввод — без подсказок; название, набранное целиком,
 * не подсказывается.
 */
fun medicineSuggestions(names: List<String>, query: String): List<String> {
    val typed = normalized(query)
    if (typed.isEmpty()) return emptyList()
    val candidates = names.map { it to normalized(it) }.filter { (_, name) -> name != typed }
    val byStart = candidates.filter { (_, name) -> name.startsWith(typed) }
    val byWord = candidates.filter { (_, name) ->
        !name.startsWith(typed) && name.split(WORD_BREAK).any { it.startsWith(typed) }
    }
    return (byStart + byWord).take(MAX_SUGGESTIONS).map { (original, _) -> original }
}

private fun normalized(text: String) = text.trim().lowercase().replace('ё', 'е')
