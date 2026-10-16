package com.kid.babylog.core.model

/** Ребёнок. [birthDate] — epoch millis локальной полуночи дня рождения, null — не указана. */
data class Child(
    val id: Long,
    val name: String,
    val birthDate: Long?,
) {
    companion object {
        /** Имя первого ребёнка: создаётся с новой базой и при миграции со старой версии. */
        const val DEFAULT_NAME = "Малыш"
        const val MAX_NAME_LENGTH = 40
    }
}

/** Имена сравниваются без учёта регистра и пробелов по краям: по имени дети сопоставляются в CSV. */
fun sameChildName(a: String, b: String): Boolean = a.trim().equals(b.trim(), ignoreCase = true)

/** Занято ли имя другим ребёнком (кроме [exceptId]). Имена должны быть уникальны — см. ADR-0008. */
fun List<Child>.isNameTaken(name: String, exceptId: Long? = null): Boolean =
    any { it.id != exceptId && sameChildName(it.name, name) }

fun isValidChildName(name: String): Boolean =
    name.isNotBlank() && name.trim().length <= Child.MAX_NAME_LENGTH
