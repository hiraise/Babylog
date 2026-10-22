package com.kid.babylog.core.model

/**
 * Фото записи (ADR-0024) — JPEG не больше [MAX_SIDE] px по длинной стороне. Хранится отдельно
 * от записи: список записей грузится без фото. Сравнивается по id и содержимому.
 */
class EventPhoto(val id: Long, val jpeg: ByteArray) {
    override fun equals(other: Any?): Boolean = other is EventPhoto && id == other.id && jpeg.contentEquals(other.jpeg)

    override fun hashCode(): Int = 31 * id.hashCode() + jpeg.contentHashCode()

    override fun toString(): String = "EventPhoto(id=$id, ${jpeg.size} B)"

    companion object {
        /** Длинная сторона: цвет и консистенцию врачу видно, а база растёт медленно. */
        const val MAX_SIDE = 1600

        /** Сколько фото можно приложить к одной записи. */
        const val MAX_PER_EVENT = 6

        /**
         * Наибольший JPEG в базе: строка с фото должна уместиться в окно курсора SQLite (2 МБ), иначе запись
         * не прочитается. Фото больше сжимает `fittingJpeg`, а импорт такие не берёт (ADR-0045).
         */
        const val MAX_BYTES = 1_200_000
    }
}

/**
 * Фото записи после формы: [keep] — id сохранённых фото, которые остаются, в этом порядке;
 * [add] — новые JPEG, встают после них.
 */
class PhotoEdit(val keep: List<Long>, val add: List<ByteArray>)
