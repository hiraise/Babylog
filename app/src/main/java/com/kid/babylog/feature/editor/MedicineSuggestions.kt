package com.kid.babylog.feature.editor

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Medicine

/** Больше подсказок под полем не нужно: они растут вниз и сдвигают остальную форму. */
private const val MAX_SUGGESTIONS = 3

private val WORD_BREAK = Regex("[^\\p{L}\\p{N}]+")

/** Лекарство из прежних записей ребёнка: подсказка в листе лекарства ([pastMedicines]). */
data class PastMedicine(val name: String, val note: String = "")

/**
 * Лекарства из записей [events] без повторов, недавние первыми. Названия, которые различаются
 * только регистром или «ё», — одно лекарство: остаётся последнее написание и последний непустой
 * комментарий — запись без комментария не стирает дозу, которую писали раньше.
 */
fun pastMedicines(events: List<BabyEvent>): List<PastMedicine> =
    events.filterIsInstance<Medicine>()
        .sortedByDescending { it.start }
        .groupBy { normalized(it.name) }
        .values
        .map { records ->
            PastMedicine(
                name = records.first().name.trim(),
                note = records.firstOrNull { it.note.isNotBlank() }?.note?.trim() ?: "",
            )
        }

/**
 * Подсказки к набранному [query] из [medicines] (недавние первыми): сначала те, чьё название
 * начинается с [query], затем те, где с него начинается другое слово («D» → «Витамин D»).
 * Регистр и «ё» не различаются. Пустой ввод — без подсказок; название, набранное целиком,
 * не подсказывается.
 */
fun medicineSuggestions(medicines: List<PastMedicine>, query: String): List<PastMedicine> {
    val typed = normalized(query)
    if (typed.isEmpty()) return emptyList()
    val candidates = medicines.map { it to normalized(it.name) }.filter { (_, name) -> name != typed }
    val byStart = candidates.filter { (_, name) -> name.startsWith(typed) }
    val byWord = candidates.filter { (_, name) ->
        !name.startsWith(typed) && name.split(WORD_BREAK).any { it.startsWith(typed) }
    }
    return (byStart + byWord).take(MAX_SUGGESTIONS).map { (medicine, _) -> medicine }
}

/**
 * Комментарий после касания подсказки [picked]. Пустой или подставленный прошлой подсказкой
 * ([autoNote]) заменяется комментарием [picked]: передумал и выбрал другое лекарство — сменится
 * и доза. Набранный или исправленный вручную остаётся.
 */
fun noteAfterSuggestion(note: String, autoNote: String, picked: PastMedicine): String =
    if (note.isBlank() || note == autoNote) picked.note else note

private fun normalized(text: String) = text.trim().lowercase().replace('ё', 'е')
