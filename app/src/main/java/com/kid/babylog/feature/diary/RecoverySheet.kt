package com.kid.babylog.feature.diary

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.kid.babylog.core.data.DatabaseRecovery
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.components.SheetAction
import com.kid.babylog.core.ui.formatClock
import com.kid.babylog.core.ui.formatDate

/**
 * Лист после восстановления повреждённой базы (ADR-0033): что случилось и что делать. Лист, а не снекбар:
 * снекбар легко не заметить, а записи после копии придётся внести заново. «Импорт из CSV» вернёт их
 * из свежего экспорта, если он есть: импорт только добавляет и пропускает дубликаты (ADR-0009).
 */
@Composable
fun DatabaseRecoveredSheet(recovery: DatabaseRecovery, onDismiss: () -> Unit, onImport: () -> Unit) {
    AppSheet(
        onDismiss = onDismiss,
        title = recoveryTitle(recovery),
        confirm = SheetAction("Понятно", onClick = onDismiss),
        secondary = SheetAction("Импорт из CSV", onClick = onImport),
    ) {
        Text(recoveryText(recovery), style = MaterialTheme.typography.bodyMedium)
    }
}

internal fun recoveryTitle(recovery: DatabaseRecovery): String =
    if (recovery.backupTime != null) "Дневник восстановлен" else "Дневник начат заново"

internal fun recoveryText(recovery: DatabaseRecovery): String {
    val backup = recovery.backupTime
    val cause = "Файл дневника в памяти телефона повредился — так бывает при сбое памяти или внезапном выключении."
    return if (backup != null) {
        "$cause Записи восстановлены из копии, которую приложение сделало ${formatDate(backup)} в ${formatClock(backup)}. " +
            "Что записали позже, внесите заново или импортируйте из CSV, если делали экспорт."
    } else {
        "$cause Копии ещё не было: приложение делает её раз в сутки. Если делали экспорт в CSV, импортируйте его."
    }
}
