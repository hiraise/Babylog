package com.kid.babylog.feature.analytics

/**
 * Раздел «Аналитики» (ADR-0029): плитка на обзоре и её подробный экран. Новый показатель — новый
 * раздел здесь, его плитка в [AnalyticsOverview] и экран в [AnalyticsDetail], а не карточка в ленте.
 */
enum class AnalyticsSection(val title: String) {
    RHYTHM("Ритм дня"),
    FEEDINGS("Кормления"),
    SLEEP("Сон"),
    DIAPERS("Подгузники"),
    CRYING("Плач"),
    WEIGHT("Вес"),
    HEIGHT("Рост"),
    MEDICINES("Лекарства"),
    TAGS("Метки"),
    ;

    /** На экране раздела есть выбор периода: у ритма всегда последняя неделя, у веса и роста — всё время. */
    val usesPeriod: Boolean
        get() = when (this) {
            FEEDINGS, SLEEP, DIAPERS, CRYING, MEDICINES, TAGS -> true
            RHYTHM, WEIGHT, HEIGHT -> false
        }
}

/** Раздел, на экране которого график [this]: бутылочка — у кормлений, своей плитки у неё нет (ADR-0028). */
internal val Metric.section: AnalyticsSection
    get() = when (this) {
        Metric.FEEDINGS, Metric.BOTTLES -> AnalyticsSection.FEEDINGS
        Metric.SLEEP -> AnalyticsSection.SLEEP
        Metric.DIAPERS -> AnalyticsSection.DIAPERS
        Metric.CRYING -> AnalyticsSection.CRYING
    }
