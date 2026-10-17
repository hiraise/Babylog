package com.kid.babylog.feature.diary

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Weight
import com.kid.babylog.feature.editor.PastMedicine
import com.kid.babylog.feature.editor.pastMedicines

/** Всё, что главный экран показывает из данных. Эфемерное состояние UI (открытые листы) — в Compose. */
data class DiaryUiState(
    val children: List<Child> = emptyList(),
    /** Выбранный ребёнок; null — данные ещё загружаются. */
    val child: Child? = null,
    /** Записи выбранного ребёнка, новые сверху. */
    val events: List<BabyEvent> = emptyList(),
    val activeSleep: Sleep? = null,
    val activeFeeding: Feeding? = null,
    /** Конец последнего законченного сна: «не спит 1 ч 20 мин» на плитке сна. */
    val lastSleepEnd: Long? = null,
    /** Последнее законченное кормление: «левая, 40 мин назад» на плитке кормления (ADR-0026). */
    val lastFeeding: LastFeeding? = null,
    /** Время последней смены подгузника: «2 ч 10 мин назад» на плитке «Подгузник». */
    val lastDiaper: Long? = null,
    /** Последняя бутылочка: с её объёмом и «что внутри» открывается следующая (ADR-0025). */
    val lastBottle: Bottle? = null,
    val lastWeight: Weight? = null,
    /** Лекарства из записей ребёнка, недавние первыми: подсказки в листе лекарства. */
    val pastMedicines: List<PastMedicine> = emptyList(),
    /** Сколько фото у записей: id записи → число, для отметки в истории (ADR-0024). */
    val photoCounts: Map<Long, Int> = emptyMap(),
    /** Сообщение для снекбара; UI показывает его и вызывает `consumeMessage()`. */
    val message: String? = null,
    /** У сообщения есть «Отменить»: таймер остановили касанием карточки (ADR-0021). */
    val canUndo: Boolean = false,
) {
    companion object {
        fun of(children: List<Child>, child: Child?, events: List<BabyEvent>, photoCounts: Map<Long, Int> = emptyMap()) = DiaryUiState(
            children = children,
            child = child,
            events = events,
            activeSleep = events.firstNotNullOfOrNull { (it as? Sleep)?.takeIf(Sleep::isActive) },
            activeFeeding = events.firstNotNullOfOrNull { (it as? Feeding)?.takeIf(Feeding::isActive) },
            lastSleepEnd = events.filterIsInstance<Sleep>().mapNotNull { it.end }.maxOrNull(),
            lastFeeding = events.mapNotNull {
                when (it) {
                    is Feeding -> it.end?.let { end -> LastFeeding(end, it.breast) }
                    is Bottle -> LastFeeding(it.start, breast = null)
                    else -> null
                }
            }.maxByOrNull { it.end },
            lastBottle = events.firstNotNullOfOrNull { it as? Bottle },
            lastDiaper = events.firstNotNullOfOrNull { (it as? Diaper)?.start },
            lastWeight = events.firstNotNullOfOrNull { it as? Weight },
            pastMedicines = pastMedicines(events),
            photoCounts = photoCounts,
        )
    }
}

/**
 * Законченное кормление для плитки: когда закончилось и чем — грудью [breast] (при смене груди —
 * последней) или из бутылочки (null). Бутылочка — разовая запись: её конец — её время (ADR-0025).
 */
data class LastFeeding(val end: Long, val breast: Breast?)
