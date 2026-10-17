package com.kid.babylog.feature.diary

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Weight

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
    /**
     * Конец последнего законченного кормления: «закончилось 40 мин назад» на плитке кормления.
     * Бутылочка — разовая запись: её конец — её время (ADR-0025).
     */
    val lastFeedingEnd: Long? = null,
    /** Последняя бутылочка: с её объёмом и «что внутри» открывается следующая (ADR-0025). */
    val lastBottle: Bottle? = null,
    val lastWeight: Weight? = null,
    /** Сообщение для снекбара; UI показывает его и вызывает `consumeMessage()`. */
    val message: String? = null,
    /** У сообщения есть «Отменить»: таймер остановили касанием карточки (ADR-0021). */
    val canUndo: Boolean = false,
) {
    companion object {
        fun of(children: List<Child>, child: Child?, events: List<BabyEvent>) = DiaryUiState(
            children = children,
            child = child,
            events = events,
            activeSleep = events.firstNotNullOfOrNull { (it as? Sleep)?.takeIf(Sleep::isActive) },
            activeFeeding = events.firstNotNullOfOrNull { (it as? Feeding)?.takeIf(Feeding::isActive) },
            lastSleepEnd = events.filterIsInstance<Sleep>().mapNotNull { it.end }.maxOrNull(),
            lastFeedingEnd = events.mapNotNull {
                when (it) {
                    is Feeding -> it.end
                    is Bottle -> it.start
                    else -> null
                }
            }.maxOrNull(),
            lastBottle = events.firstNotNullOfOrNull { it as? Bottle },
            lastWeight = events.firstNotNullOfOrNull { it as? Weight },
        )
    }
}
