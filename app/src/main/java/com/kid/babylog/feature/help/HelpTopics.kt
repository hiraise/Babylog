package com.kid.babylog.feature.help

import com.kid.babylog.core.model.BabyEvent

/**
 * Темы справки по порядку прохождения (ADR-0043): сначала плитки «Сегодня» (`HelpTopicsToday.kt`),
 * потом разделы и меню (`HelpTopicsSections.kt`). Справка — главное описание того, что делает
 * пользователь: новая функция или изменённый жест приходит с шагом в своей теме и с `since` —
 * версией, в которой шаг появился. У каждой темы есть UI-тест, который проходит её гайд до конца.
 */
val HELP_TOPICS: List<HelpTopic> = listOf(
    SLEEP_FEEDING,
    RECORDS,
    CRYING,
    NOTES,
    HEALTH,
    DIARY,
    ANALYTICS,
    DEVELOPMENT,
    CHILDREN,
    SETTINGS,
)

/** Сколько записей типа [T]: шаг «добавьте запись» засчитывается, когда их стало больше. */
internal inline fun <reified T : BabyEvent> List<BabyEvent>.countOf(): Int = count { it is T }

/** Записей типа [T] стало больше, чем в начале шага. */
internal inline fun <reified T : BabyEvent> added(): StepDone =
    StepDone.Records { now, atStart -> now.countOf<T>() > atStart.countOf<T>() }
