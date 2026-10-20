package com.kid.babylog.feature.development

import java.net.URI
import java.time.LocalDate

/** Тема совета (ADR-0037); код в `tips_ru.json` — [name], [title] — подпись на экране. */
enum class TipCategory(val title: String) {
    SLEEP("Сон"),
    FEEDING("Кормление"),
    DEVELOPMENT("Развитие"),
    SAFETY("Безопасность"),
    CRYING("Плач и поведение"),
    HEALTH("Здоровье"),
}

/** [DOCTOR] — «когда показать педиатру»: тревожные признаки, на экране — отдельным блоком. */
enum class TipKind { TIP, DOCTOR }

/** Источник совета: [section] — раздел или страница, где стоит факт; по нему совет сверяют. */
data class TipSource(val name: String, val url: String, val section: String)

/** Позиция одного источника, когда источники расходятся: «ВОЗ» — «прикорм с 6 месяцев». */
data class SourcePosition(val source: String, val position: String)

/**
 * Совет раздела «Развитие» (ADR-0037). [ages] — дни жизни, в которые совет показывается, включительно;
 * [points] — необязательный список; [disagreement] — пуст, если источники не расходятся.
 */
data class Tip(
    val id: String,
    val ages: IntRange,
    val category: TipCategory,
    val kind: TipKind,
    val title: String,
    val summary: String,
    val body: String,
    val points: List<String>,
    val sources: List<TipSource>,
    val disagreement: List<SourcePosition>,
    val reviewedAt: LocalDate,
)

/** Последний день, для которого пишутся советы: первый год жизни — дни 0–364 (ADR-0037). */
const val LAST_TIP_DAY = 364

/**
 * Сайты, на которые может ссылаться совет (ADR-0037): ВОЗ, CDC, AAP и его сайт для родителей, NHS,
 * Минздрав России и портал публикации его приказов, Союз педиатров России, статьи по DOI.
 * Поддомены подходят: `cdn.who.int`, `cr.minzdrav.gov.ru`, `publication.pravo.gov.ru`.
 */
val ALLOWED_SOURCE_HOSTS = listOf(
    "who.int",
    "cdc.gov",
    "aap.org",
    "healthychildren.org",
    "nhs.uk",
    "minzdrav.gov.ru",
    "pravo.gov.ru",
    "pediatr-russia.ru",
    "doi.org",
)

/** Ссылка по `https` на сайт из [ALLOWED_SOURCE_HOSTS]. */
fun isAllowedSourceUrl(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val host = uri.host?.lowercase() ?: return false
    return uri.scheme == "https" && ALLOWED_SOURCE_HOSTS.any { host == it || host.endsWith(".$it") }
}
