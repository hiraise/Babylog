package com.kid.babylog.feature.development

import java.time.LocalDate

/**
 * Навык из исследования ВОЗ Motor Development Study (ADR-0037). [window] — дни жизни от 1-го до 99-го
 * перцентиля: в этом возрасте навык осваивают 98 детей из 100. [months] — те же границы в месяцах,
 * как они напечатаны в таблице ВОЗ: показывай их, а не пересчёт дней — у ВОЗ дни до округления,
 * и 409 дней там — 13,5 месяца, а не 13,4.
 */
data class MilestoneWindow(val id: String, val title: String, val window: IntRange, val months: ClosedFloatingPointRange<Double>)

/** Где ребёнок относительно окна: до него, в нём или окно уже прошло. */
enum class MilestoneState { AHEAD, IN_WINDOW, PAST }

/** Окна навыков вместе с источником таблицы и датой, когда её сверяли. */
data class MilestoneTable(val source: TipSource, val reviewedAt: LocalDate, val milestones: List<MilestoneWindow>)

fun MilestoneWindow.stateAt(ageDays: Int): MilestoneState = when {
    ageDays < window.first -> MilestoneState.AHEAD
    ageDays > window.last -> MilestoneState.PAST
    else -> MilestoneState.IN_WINDOW
}
