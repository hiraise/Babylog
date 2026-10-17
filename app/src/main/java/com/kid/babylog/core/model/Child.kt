package com.kid.babylog.core.model

/**
 * Ребёнок. [birthDate] — epoch millis локальной полуночи дня рождения, null — не указана;
 * [sex] — пол, null — не указан (ADR-0016); [photo] — фото для аватара, null — нет (ADR-0022).
 */
data class Child(
    val id: Long,
    val name: String,
    val birthDate: Long?,
    val sex: Sex? = null,
    val photo: ChildPhoto? = null,
) {
    companion object {
        /** Имя первого ребёнка: создаётся с новой базой и при миграции со старой версии. */
        const val DEFAULT_NAME = "Малыш"
        const val MAX_NAME_LENGTH = 40
    }
}

/** Пол ребёнка: от него зависят слова вроде «уснул / уснула» (ADR-0016). Код в базе и CSV — [name]. */
enum class Sex {
    BOY,
    GIRL;

    companion object {
        /** Код из базы или CSV без учёта регистра; пустой или неизвестный — null. */
        fun fromCode(code: String?): Sex? = entries.firstOrNull { it.name == code?.trim()?.uppercase() }
    }
}

/**
 * Фото ребёнка — уже кадрированный квадратный JPEG [SIZE]×[SIZE] (ADR-0022). Сравнивается по
 * содержимому: массив в `data class` сравнивался бы по ссылке, и каждый список детей из базы
 * считался бы новым.
 */
class ChildPhoto(val jpeg: ByteArray) {
    override fun equals(other: Any?): Boolean = other is ChildPhoto && jpeg.contentEquals(other.jpeg)

    override fun hashCode(): Int = jpeg.contentHashCode()

    override fun toString(): String = "ChildPhoto(${jpeg.size} B)"

    companion object {
        /** Сторона фото в пикселях: с запасом для аватара 96 dp на самом плотном экране. */
        const val SIZE = 512
    }
}

/** Имена сравниваются без учёта регистра и пробелов по краям: по имени дети сопоставляются в CSV. */
fun sameChildName(a: String, b: String): Boolean = a.trim().equals(b.trim(), ignoreCase = true)

/** Занято ли имя другим ребёнком (кроме [exceptId]). Имена должны быть уникальны — см. ADR-0008. */
fun List<Child>.isNameTaken(name: String, exceptId: Long? = null): Boolean =
    any { it.id != exceptId && sameChildName(it.name, name) }

fun isValidChildName(name: String): Boolean =
    name.isNotBlank() && name.trim().length <= Child.MAX_NAME_LENGTH
