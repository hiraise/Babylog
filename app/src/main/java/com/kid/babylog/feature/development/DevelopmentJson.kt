package com.kid.babylog.feature.development

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Советы из `tips_ru.json` (ADR-0037): массив объектов с полями совета. Нарушение схемы, неизвестная
 * тема, источник не из [ALLOWED_SOURCE_HOSTS] — [IllegalArgumentException] с `id` совета: такой файл
 * не должен попасть в сборку, его ловит `TipsContentTest`.
 */
fun parseTips(json: String): List<Tip> {
    val array = parse { JSONArray(json) }
    return (0 until array.length()).map { index ->
        val obj = parse { array.getJSONObject(index) }
        val id = obj.requiredString("id", "совет №$index")
        parse(id) { obj.toTip(id) }
    }
}

/** Окна навыков из `who_motor_milestones.json`: источник, дата сверки и шесть навыков с окнами в днях и месяцах. */
fun parseMilestones(json: String): MilestoneTable {
    val obj = parse { JSONObject(json) }
    return parse("окна навыков") {
        val items = obj.getJSONArray("milestones")
        MilestoneTable(
            source = obj.getJSONObject("source").toSource(),
            reviewedAt = obj.date("reviewedAt"),
            milestones = (0 until items.length()).map { index ->
                val item = items.getJSONObject(index)
                val months = item.getDouble("fromMonths")..item.getDouble("toMonths")
                require(months.start < months.endInclusive) { "fromMonths больше toMonths" }
                MilestoneWindow(
                    id = item.requiredString("id", "навык №$index"),
                    title = item.requiredString("title", "навык №$index"),
                    window = item.dayRange("fromDays", "toDays"),
                    months = months,
                )
            },
        )
    }
}

private fun JSONObject.toTip(id: String): Tip {
    val ages = dayRange("ageFromDays", "ageToDays")
    require(ages.first >= 0 && ages.last <= LAST_TIP_DAY) { "возраст ${ages.first}–${ages.last} вне 0–$LAST_TIP_DAY" }
    val sources = getJSONArray("sources").objects().map { it.toSource() }
    require(sources.isNotEmpty()) { "нет источников" }
    return Tip(
        id = id,
        ages = ages,
        category = enumValue<TipCategory>("category"),
        kind = enumValue<TipKind>("kind"),
        title = requiredString("title", id),
        summary = requiredString("summary", id),
        body = requiredString("body", id),
        points = optJSONArray("points")?.strings().orEmpty(),
        sources = sources,
        disagreement = optJSONArray("disagreement")?.objects().orEmpty().map {
            SourcePosition(it.requiredString("source", id), it.requiredString("position", id))
        },
        reviewedAt = date("reviewedAt"),
    )
}

private fun JSONObject.toSource(): TipSource {
    val url = requiredString("url", "источник")
    require(isAllowedSourceUrl(url)) { "источник не из списка ADR-0037: $url" }
    return TipSource(requiredString("name", url), url, requiredString("section", url))
}

private fun JSONObject.dayRange(from: String, to: String): IntRange {
    val range = getInt(from)..getInt(to)
    require(!range.isEmpty()) { "$from больше $to" }
    return range
}

private fun JSONObject.requiredString(name: String, owner: String): String {
    val value = optString(name).trim()
    require(value.isNotEmpty()) { "$owner: нет поля $name" }
    return value
}

private inline fun <reified E : Enum<E>> JSONObject.enumValue(name: String): E {
    val code = getString(name)
    return enumValues<E>().firstOrNull { it.name == code } ?: throw IllegalArgumentException("$name: неизвестное значение $code")
}

private fun JSONObject.date(name: String): LocalDate = try {
    LocalDate.parse(getString(name))
} catch (e: DateTimeParseException) {
    throw IllegalArgumentException("$name: не дата ГГГГ-ММ-ДД", e)
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it).trim() }.filter { it.isNotEmpty() }

/** Ошибка `org.json` — проверяемое исключение; наружу отдаём одно [IllegalArgumentException] с местом ошибки. */
private inline fun <T> parse(where: String? = null, block: () -> T): T = try {
    block()
} catch (e: JSONException) {
    throw IllegalArgumentException(listOfNotNull(where, e.message).joinToString(": "), e)
} catch (e: IllegalArgumentException) {
    if (where == null) throw e
    throw IllegalArgumentException("$where: ${e.message}", e)
}
