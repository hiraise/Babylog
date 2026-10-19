package com.kid.babylog.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** База была повреждена и восстановлена (ADR-0033) в момент [at]; [backupTime] — время копии, null — копии не было. */
data class DatabaseRecovery(val at: Long, val backupTime: Long?)

/**
 * Сообщение о восстановлении базы. Ждёт, пока пользователь его не закроет, — и после перезапуска: сбой
 * может закрыть приложение раньше, чем сообщение покажут.
 */
interface RecoveryNotices {
    /** Непрочитанное сообщение; null — нечего показывать. */
    val pending: StateFlow<DatabaseRecovery?>

    fun report(recovery: DatabaseRecovery)

    fun dismiss()
}

class PrefsRecoveryNotices(context: Context) : RecoveryNotices {

    private val prefs = context.getSharedPreferences("babylog", Context.MODE_PRIVATE)

    override val pending = MutableStateFlow(
        prefs.getLong(AT, NONE).takeIf { it != NONE }?.let { at ->
            DatabaseRecovery(at, prefs.getLong(BACKUP_TIME, NONE).takeIf { it != NONE })
        }
    )

    override fun report(recovery: DatabaseRecovery) {
        // commit, а не apply: запись должна дойти до диска, даже если приложение сейчас закроется
        prefs.edit().putLong(AT, recovery.at).putLong(BACKUP_TIME, recovery.backupTime ?: NONE).commit()
        pending.value = recovery
    }

    override fun dismiss() {
        prefs.edit().remove(AT).remove(BACKUP_TIME).apply()
        pending.value = null
    }

    private companion object {
        const val AT = "db_recovered_at"
        const val BACKUP_TIME = "db_recovered_from_backup"
        const val NONE = -1L
    }
}
