package com.hiraise.babylog.feature.diary

import com.hiraise.babylog.core.model.BabyEvent
import com.hiraise.babylog.core.model.Bottle
import com.hiraise.babylog.core.model.Crying
import com.hiraise.babylog.core.model.Diaper
import com.hiraise.babylog.core.model.MAX_AWAKE_MILLIS
import com.hiraise.babylog.core.model.lastSleepEndBefore
import com.hiraise.babylog.core.model.usualAwakeBefore
import com.hiraise.babylog.core.ui.bottleLabel
import com.hiraise.babylog.core.ui.breastLabel
import com.hiraise.babylog.core.ui.formatDuration
import com.hiraise.babylog.feature.editor.CryingContext
import com.hiraise.babylog.feature.summary.feedingSessions

/**
 * «Что было до» плача [crying] (ADR-0030) по записям ребёнка [events]: от последнего сна, кормления
 * и подгузника до начала плача, и обычное бодрствование — медиана окон за неделю перед ним.
 * Записи давнее [MAX_AWAKE_MILLIS] — забытые, а не «не спит 9 часов»: их нет в карточке.
 */
internal fun cryingContext(events: List<BabyEvent>, crying: Crying): CryingContext {
    val at = crying.start
    fun recent(time: Long?) = time?.takeIf { at - it in 0..MAX_AWAKE_MILLIS }

    val sleepEnd = recent(events.lastSleepEndBefore(at))
    val usual = events.usualAwakeBefore(at)

    // Кормление — сессия груди целиком (смена груди — одно кормление) или бутылочка, что закончилось позже
    val session = feedingSessions(events).filter { it.end != null && it.end <= at }.maxByOrNull { it.end ?: 0 }
    val bottle = events.filterIsInstance<Bottle>().filter { it.start <= at }.maxByOrNull { it.start }
    val byBottle = bottle != null && (session?.end ?: Long.MIN_VALUE) < bottle.start
    val feedingEnd = recent(if (byBottle) bottle?.start else session?.end)
    val feeding = when {
        feedingEnd == null -> null
        byBottle && bottle != null -> bottleLabel(bottle.volumeMl, bottle.milk)
        session != null -> "${breastLabel(session.lastBreast)}, ${formatDuration((session.end ?: session.start) - session.start)}"
        else -> null
    }

    val diaper = events.filterIsInstance<Diaper>().filter { it.start <= at }.maxByOrNull { it.start }
    val diaperTime = recent(diaper?.start)
    return CryingContext(
        awakeMillis = sleepEnd?.let { at - it },
        usualAwakeMillis = usual,
        sinceFeedingMillis = feedingEnd?.let { at - it },
        feeding = feeding,
        feedingByBottle = byBottle,
        sinceDiaperMillis = diaperTime?.let { at - it },
        diaper = diaper?.kind?.takeIf { diaperTime != null },
    )
}
