package com.kid.babylog.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Что пользователь видел в справке (ADR-0043). Это настройка телефона, а не дневник: в базу и CSV
 * не попадает.
 *
 * @property baseline версия первого запуска со справкой: шаги не новее неё «Новым» не помечаются.
 * @property opened на какой версии открывали тему — по id темы.
 * @property completed id тем, гайд которых прошли до конца.
 * @property pointerDone карточку «Где справка» на «Сегодня» закрыли или уже открывали справку.
 */
data class HelpProgress(
    val baseline: String? = null,
    val opened: Map<String, String> = emptyMap(),
    val completed: Set<String> = emptySet(),
    val pointerDone: Boolean = false,
)

interface HelpStore {
    val progress: StateFlow<HelpProgress>

    fun markOpened(topicId: String, version: String)

    fun markCompleted(topicId: String)

    fun dismissPointer()
}

/** [version] — текущая версия приложения: при первом запуске со справкой она становится `baseline`. */
class PrefsHelpStore(context: Context, version: String) : HelpStore {

    private val prefs = context.getSharedPreferences("babylog", Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY_BASELINE)) prefs.edit().putString(KEY_BASELINE, version).apply()
    }

    override val progress = MutableStateFlow(
        HelpProgress(
            baseline = prefs.getString(KEY_BASELINE, null),
            opened = prefs.all.mapNotNull { (key, value) ->
                if (key.startsWith(KEY_OPENED) && value is String) key.removePrefix(KEY_OPENED) to value else null
            }.toMap(),
            completed = prefs.getStringSet(KEY_COMPLETED, null).orEmpty(),
            pointerDone = prefs.getBoolean(KEY_POINTER_DONE, false),
        )
    )

    override fun markOpened(topicId: String, version: String) {
        prefs.edit().putString(KEY_OPENED + topicId, version).apply()
        progress.value = progress.value.let { it.copy(opened = it.opened + (topicId to version)) }
    }

    override fun markCompleted(topicId: String) {
        val completed = progress.value.completed + topicId
        // Набор из getStringSet менять нельзя — пишем новый
        prefs.edit().putStringSet(KEY_COMPLETED, completed.toSet()).apply()
        progress.value = progress.value.copy(completed = completed)
    }

    override fun dismissPointer() {
        prefs.edit().putBoolean(KEY_POINTER_DONE, true).apply()
        progress.value = progress.value.copy(pointerDone = true)
    }

    private companion object {
        const val KEY_BASELINE = "help_baseline"
        const val KEY_OPENED = "help_opened_"
        const val KEY_COMPLETED = "help_completed"
        const val KEY_POINTER_DONE = "help_pointer_done"
    }
}
