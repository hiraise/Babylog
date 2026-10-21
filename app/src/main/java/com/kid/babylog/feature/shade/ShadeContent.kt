package com.kid.babylog.feature.shade

import com.kid.babylog.core.data.ActiveTimers
import com.kid.babylog.core.data.ShadeMemo
import com.kid.babylog.core.data.nextBreast
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.ui.breastLabel
import com.kid.babylog.core.ui.bySex
import com.kid.babylog.core.ui.formatClock
import com.kid.babylog.core.ui.formatDuration
import com.kid.babylog.core.ui.formatShortDay
import com.kid.babylog.core.ui.toLocalDate

/** Сколько после остановки кнопкой работает «Отменить»: дольше снекбара — в шторке промахиваются чаще. */
const val STOP_UNDO_MS = 15_000L

/** Сколько работает «Отменить, спит дальше» у плача, прервавшего сон. */
const val CANCEL_CRYING_MS = 60_000L

/** Сколько после плача, остановленного кнопкой, видна строка «Отметить» — потом причины уже не вспомнить. */
const val CRYING_DETAILS_MS = 60 * 60_000L

/** Идущий таймер в уведомлении: [title] и [text] слева, часы от [start] справа, [link] — кнопка под текстом. */
data class ShadeTimer(val title: String, val text: String, val start: Long, val link: ShadeLink? = null)

/** Кнопка под текстом таймера. */
sealed interface ShadeLink {
    val label: String

    data object SwitchBreast : ShadeLink {
        override val label = "Сменить грудь"
    }

    /** Плач [cryingId] начат кнопкой и прервал сон: «Отменить» убирает плач, сон идёт дальше. */
    data class CancelCrying(val cryingId: Long) : ShadeLink {
        override val label = "Отменить, спит дальше"
    }
}

/** Строка с кнопкой под таймерами: [text] и [button] для записи [eventId]. */
data class ShadeRow(val text: String, val button: String, val eventId: Long)

/**
 * Что показывает уведомление в шторке (ADR-0042) для ребёнка [childId]. Время — часами («с 14:05»), а не
 * «40 мин назад»: уведомление между изменениями не перерисовывается, а идущие таймеры считает система.
 */
data class ShadeContent(
    val childId: Long,
    val childName: String,
    val sleep: ShadeTimer? = null,
    val feeding: ShadeTimer? = null,
    val crying: ShadeTimer? = null,
    /** Пока таймеры не идут: «Не спит с 14:05», под ним — последнее кормление. */
    val idleTitle: String,
    val idleText: String? = null,
    /** То же в свёрнутом уведомлении, двумя короткими строками: «Не спит» / «с 14:05». */
    val idleShort: Pair<String, String?>,
    /** Грудь, с которой начнёт кнопка «Кормление»: другая, чем в прошлый раз. */
    val nextBreast: Breast,
    /** «Сон: 1 ч 12 мин» · «Отменить» — как в сообщении на экране. */
    val stopped: ShadeRow? = null,
    /** «Плакала 6 мин» · «Отметить». */
    val cryingDone: ShadeRow? = null,
    /** Когда уведомление поменяется само — истечёт «Отменить» или «Отметить»; null — не поменяется. */
    val refreshAt: Long? = null,
) {
    val isIdle: Boolean get() = sleep == null && feeding == null && crying == null
}

/** Содержимое уведомления по записям ребёнка [events] (новые сверху) и памяти кнопок [memo] в момент [now]. */
fun shadeContent(child: Child, events: List<BabyEvent>, memo: ShadeMemo, now: Long): ShadeContent {
    val active = ActiveTimers.of(events)
    val refresh = mutableListOf<Long>()

    // Вернуть прерванный сон можно, только пока не начались другой сон или кормление
    val cancelCrying = memo.startedCrying?.takeIf {
        it.until > now && it.cryingId == active.crying?.id && active.sleep == null && active.feeding == null
    }
    cancelCrying?.let { refresh += it.until }

    val stopped = memo.stopped?.takeIf { it.until > now }?.let { memo ->
        val text = events.firstNotNullOfOrNull { event ->
            when {
                event.id != memo.eventId -> null
                event is Sleep && event.end != null -> "Сон: ${formatDuration(event.end - event.start)}"
                event is Feeding && event.end != null -> "Кормление: ${formatDuration(event.end - event.start)}"
                else -> null
            }
        }
        text?.let { refresh += memo.until; ShadeRow(it, "Отменить", memo.eventId) }
    }

    val cryingDone = events.firstNotNullOfOrNull { event ->
        (event as? Crying)?.takeIf { it.id == memo.finishedCryingId && it.hasNoDetails() }
    }?.let { crying ->
        val end = crying.end ?: return@let null
        if (end + CRYING_DETAILS_MS <= now) return@let null
        refresh += end + CRYING_DETAILS_MS
        val cried = bySex(child.sex, "Плакал", "Плакала")
        ShadeRow("$cried ${formatDuration(end - crying.start)}", "Отметить", crying.id)
    }

    val lastSleepEnd = events.filterIsInstance<Sleep>().mapNotNull { it.end }.maxOrNull()
    val feedingLine = lastFeedingLine(events, now)
    return ShadeContent(
        childId = child.id,
        childName = child.name,
        sleep = active.sleep?.let { ShadeTimer("Сон", "с ${at(it.start, now)}", it.start) },
        feeding = active.feeding?.let {
            ShadeTimer("Кормление", "${breastLabel(it.breast)} с ${at(it.start, now)}", it.start, ShadeLink.SwitchBreast)
        },
        crying = active.crying?.let {
            val text = "с ${at(it.start, now)}" + if (cancelCrying != null) " · сон закончен" else ""
            ShadeTimer("Плач", text, it.start, cancelCrying?.let { c -> ShadeLink.CancelCrying(c.cryingId) })
        },
        idleTitle = lastSleepEnd?.let { "Не спит с ${at(it, now)}" } ?: feedingLine ?: "Сон, кормление и плач — кнопками ниже",
        idleText = feedingLine.takeIf { lastSleepEnd != null },
        idleShort = lastSleepEnd?.let { "Не спит" to "с ${at(it, now)}" } ?: (child.name to null),
        nextBreast = nextBreast(events),
        stopped = stopped,
        cryingDone = cryingDone,
        refreshAt = refresh.minOrNull(),
    )
}

private fun Crying.hasNoDetails() = !isActive && reasons.isEmpty() && helped.isEmpty() && note.isBlank()

/** Последнее кормление грудью или бутылочка: «Кормление в 12:40, правая», «Бутылочка в 13:10, 120 мл». */
private fun lastFeedingLine(events: List<BabyEvent>, now: Long): String? {
    val breast = events.filterIsInstance<Feeding>().filter { !it.isActive }.maxByOrNull { it.start }
    val bottle = events.filterIsInstance<Bottle>().maxByOrNull { it.start }
    return when {
        bottle != null && (breast == null || bottle.start > breast.start) -> "Бутылочка в ${at(bottle.start, now)}, ${bottle.volumeMl} мл"
        breast != null -> "Кормление в ${at(breast.start, now)}, ${breastLabel(breast.breast)}"
        else -> null
    }
}

/** «14:05» сегодня, «23:10, вчера» и «09:00, 15 окт.» — в другие дни. */
private fun at(time: Long, now: Long): String {
    val day = time.toLocalDate()
    val today = now.toLocalDate()
    return if (day == today) formatClock(time) else "${formatClock(time)}, ${formatShortDay(day, today)}"
}
