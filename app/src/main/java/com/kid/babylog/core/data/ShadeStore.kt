package com.kid.babylog.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Сон или кормление [eventId], остановленные кнопкой уведомления: «Отменить» работает до [until]. */
data class StoppedTimer(val eventId: Long, val until: Long)

/** Плач [cryingId], начатый кнопкой уведомления и прервавший сон [sleepId]: «Отменить» работает до [until]. */
data class CancelableCrying(val cryingId: Long, val sleepId: Long, val until: Long)

/**
 * Что уведомление в шторке помнит между нажатиями (ADR-0042): процесс приложения между ними может
 * завершиться. [finishedCryingId] — плач, остановленный кнопкой: «Отметить» откроет лист с причинами.
 */
data class ShadeMemo(
    val stopped: StoppedTimer? = null,
    val startedCrying: CancelableCrying? = null,
    val finishedCryingId: Long? = null,
)

/**
 * Уведомление с кнопками в шторке (ADR-0042): включено ли и [ShadeMemo]. Это настройка телефона,
 * а не дневника: в базу и CSV не попадает.
 */
interface ShadeStore {
    val enabled: StateFlow<Boolean>

    fun setEnabled(enabled: Boolean)

    val memo: StateFlow<ShadeMemo>

    fun updateMemo(transform: (ShadeMemo) -> ShadeMemo)
}

class PrefsShadeStore(context: Context) : ShadeStore {

    private val prefs = context.getSharedPreferences("babylog", Context.MODE_PRIVATE)

    override val enabled = MutableStateFlow(prefs.getBoolean(ENABLED, false))

    override fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(ENABLED, enabled).apply()
        this.enabled.value = enabled
    }

    override val memo = MutableStateFlow(readMemo())

    @Synchronized
    override fun updateMemo(transform: (ShadeMemo) -> ShadeMemo) {
        val next = transform(memo.value)
        prefs.edit()
            .putLong(STOPPED_ID, next.stopped?.eventId ?: NONE)
            .putLong(STOPPED_UNTIL, next.stopped?.until ?: NONE)
            .putLong(CRYING_ID, next.startedCrying?.cryingId ?: NONE)
            .putLong(CRYING_SLEEP_ID, next.startedCrying?.sleepId ?: NONE)
            .putLong(CRYING_UNTIL, next.startedCrying?.until ?: NONE)
            .putLong(FINISHED_CRYING_ID, next.finishedCryingId ?: NONE)
            .apply()
        memo.value = next
    }

    private fun readMemo(): ShadeMemo {
        fun long(key: String) = prefs.getLong(key, NONE).takeIf { it != NONE }
        val stoppedId = long(STOPPED_ID)
        val stoppedUntil = long(STOPPED_UNTIL)
        val cryingId = long(CRYING_ID)
        val cryingSleepId = long(CRYING_SLEEP_ID)
        val cryingUntil = long(CRYING_UNTIL)
        return ShadeMemo(
            stopped = if (stoppedId != null && stoppedUntil != null) StoppedTimer(stoppedId, stoppedUntil) else null,
            startedCrying = if (cryingId != null && cryingSleepId != null && cryingUntil != null) {
                CancelableCrying(cryingId, cryingSleepId, cryingUntil)
            } else null,
            finishedCryingId = long(FINISHED_CRYING_ID),
        )
    }

    private companion object {
        const val NONE = -1L
        const val ENABLED = "shade_enabled"
        const val STOPPED_ID = "shade_stopped_id"
        const val STOPPED_UNTIL = "shade_stopped_until"
        const val CRYING_ID = "shade_crying_id"
        const val CRYING_SLEEP_ID = "shade_crying_sleep_id"
        const val CRYING_UNTIL = "shade_crying_until"
        const val FINISHED_CRYING_ID = "shade_finished_crying_id"
    }
}
