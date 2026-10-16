package com.kid.babylog.feature.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.kid.babylog.R
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.components.SheetAction
import com.kid.babylog.core.ui.diaperIcon
import com.kid.babylog.core.ui.diaperLabel

@Composable
internal fun DiaperSheet(
    initial: Diaper?,
    sex: Sex?,
    onDismiss: () -> Unit,
    onSave: (kind: DiaperKind, time: Long, note: String) -> Unit,
) {
    val isEdit = initial != null
    var kind by rememberSaveable { mutableStateOf(initial?.kind) }
    var minutesAgo by rememberSaveable { mutableIntStateOf(0) }
    var editTime by rememberSaveable { mutableLongStateOf(initial?.start ?: 0L) }
    var note by rememberSaveable { mutableStateOf(initial?.note ?: "") }
    val resolveTime = rememberTimeResolver()
    val selected = kind
    AppSheet(
        onDismiss = onDismiss,
        title = if (isEdit) "Подгузник — правка" else "Подгузник",
        icon = R.drawable.ic_diaper,
        confirm = SheetAction("Сохранить", enabled = selected != null) {
            if (selected != null) onSave(selected, resolveTime(isEdit, minutesAgo, editTime), note.trim())
        },
    ) {
        OptionChips(
            options = DiaperKind.entries.map { it to diaperLabel(it, sex).replaceFirstChar(Char::titlecase) },
            selected = kind,
            onSelect = { kind = it },
            icon = ::diaperIcon,
        )
        TimeSection(isEdit, minutesAgo, { minutesAgo = it }, editTime, { editTime = it })
        NoteField(note, { note = it })
    }
}
