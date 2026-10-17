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
import com.kid.babylog.feature.summary.feedingSessions

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
    /** Последнее законченное кормление: «левая в 12:00, закончилось 40 мин назад» на плитке кормления (ADR-0026). */
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
            lastFeeding = lastFeeding(events),
            lastBottle = events.firstNotNullOfOrNull { it as? Bottle },
            lastDiaper = events.firstNotNullOfOrNull { (it as? Diaper)?.start },
            lastWeight = events.firstNotNullOfOrNull { it as? Weight },
            pastMedicines = pastMedicines(events),
            photoCounts = photoCounts,
        )
    }
}

/**
 * Законченное кормление для плитки: когда началось и закончилось и чем — грудью [breast] (при смене
 * груди — последней, а начало — всего кормления) или из бутылочки (null). Бутылочка — разовая запись:
 * у неё начало и конец — её время (ADR-0025).
 */
data class LastFeeding(val start: Long, val end: Long, val breast: Breast?)

/** Кормление грудью целиком ([feedingSessions]) или бутылочка — что закончилось позже; идущее не в счёт. */
private fun lastFeeding(events: List<BabyEvent>): LastFeeding? {
    val breast = feedingSessions(events).mapNotNull { session -> session.end?.let { LastFeeding(session.start, it, session.lastBreast) } }
        .maxByOrNull { it.end }
    val bottle = events.firstNotNullOfOrNull { it as? Bottle }?.let { LastFeeding(it.start, it.start, breast = null) }
    return listOfNotNull(breast, bottle).maxByOrNull { it.end }
}
