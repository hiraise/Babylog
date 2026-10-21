package com.kid.babylog.core.model

/** Длина метки: «перевозбуждение» с запасом, но не фраза — фразу пишут в комментарий. */
const val MAX_TAG_LENGTH = 30

private val SEPARATORS = Regex("[;|]")
private val SPACES = Regex("\\s+")

/**
 * Метка плача (ADR-0030) — и пункт «что помогло» — в общем виде: строчные буквы, одиночные пробелы,
 * без «;» и «|» (ими метки разделены в `detail`), не длиннее [MAX_TAG_LENGTH]. null — пусто.
 * Свою метку пишут один раз: дальше она в подсказках, потому что встречалась в записях.
 */
fun normalizeTag(raw: String): String? =
    raw.replace(SEPARATORS, " ").replace(SPACES, " ").trim().lowercase().take(MAX_TAG_LENGTH).trim().ifEmpty { null }

/** Причины плача, которые лист предлагает сразу, ещё до своих меток. */
val DEFAULT_CRYING_REASONS = listOf("колики", "усталость", "голод", "живот", "зубки", "перевозбуждение")

/** Что помогло успокоить — свой короткий список, а не метки (ADR-0030). */
val DEFAULT_CRYING_HELPED = listOf("укачивание", "на руках", "грудь", "тёплое на живот", "белый шум", "соска")

/** Метки, которые лист заметки предлагает сразу (ADR-0031): новое умение, зубки, сыпь и то, что бывает связано с коликами. */
val DEFAULT_NOTE_TAGS = listOf("новое", "зубки", "сыпь", "питание мамы", "прививка", "колики")

/** Метки записи — один словарь (ADR-0031): причины плача и метки заметок; у остальных записей меток нет. */
val BabyEvent.eventTags: List<String>
    get() = when (this) {
        is Crying -> reasons
        is Note -> tags
        is Feeding, is Bottle, is Diaper, is Sleep, is Medicine, is Weight, is Height, is Illness, is Temperature -> emptyList()
    }
