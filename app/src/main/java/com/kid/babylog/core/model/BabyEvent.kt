package com.kid.babylog.core.model

/**
 * Запись дневника. Время — epoch millis с точностью до секунды (ADR-0008).
 *
 * Новый тип записи — новый класс здесь. Все `when` по [BabyEvent] пишутся без `else`,
 * поэтому компилятор сам покажет места, которые нужно дополнить.
 */
sealed interface BabyEvent {
    val id: Long
    val childId: Long
    val start: Long
    val note: String
}

/**
 * Кормление одной грудью; [end] = null — кормление идёт сейчас (у ребёнка не больше одного).
 * Записи до ADR-0015 — без длительности: у них [end] равен [start].
 */
data class Feeding(
    override val id: Long = 0,
    override val childId: Long,
    override val start: Long,
    val end: Long? = null,
    val breast: Breast,
    override val note: String = "",
) : BabyEvent {
    val isActive: Boolean get() = end == null
}

data class Diaper(
    override val id: Long = 0,
    override val childId: Long,
    override val start: Long,
    val kind: DiaperKind,
    override val note: String = "",
) : BabyEvent

/** Сон; [end] = null — ребёнок спит сейчас (активный сон, у ребёнка не больше одного). */
data class Sleep(
    override val id: Long = 0,
    override val childId: Long,
    override val start: Long,
    val end: Long? = null,
    override val note: String = "",
) : BabyEvent {
    val isActive: Boolean get() = end == null
}

data class Medicine(
    override val id: Long = 0,
    override val childId: Long,
    override val start: Long,
    val name: String,
    override val note: String = "",
) : BabyEvent

data class Weight(
    override val id: Long = 0,
    override val childId: Long,
    override val start: Long,
    val grams: Int,
    override val note: String = "",
) : BabyEvent {
    companion object {
        /** Допустимый вес в граммах (защита от опечаток). */
        val RANGE = 300..30_000
    }
}

enum class Breast {
    LEFT,
    RIGHT;

    /** Другая грудь — для «Сменить грудь». */
    val opposite: Breast
        get() = when (this) {
            LEFT -> RIGHT
            RIGHT -> LEFT
        }
}

enum class DiaperKind { WET, DIRTY, MIXED }

