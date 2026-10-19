package com.kid.babylog.feature.editor

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.kid.babylog.R
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.EventPhoto
import com.kid.babylog.core.model.PhotoEdit
import com.kid.babylog.core.ui.LocalClock
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.components.SheetAction
import com.kid.babylog.core.ui.diaperIcon
import com.kid.babylog.core.ui.diaperLabel
import com.kid.babylog.core.ui.photo.shareWithPhotos
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.core.ui.toLocalDate

/**
 * Подгузник: вид, время, комментарий и фото (ADR-0024). Фото — до [EventPhoto.MAX_PER_EVENT]: снять
 * или выбрать в галерее, открыть крупно и убрать ([PhotosField], [PhotoSheets]). В базу они
 * попадают по «Сохранить» вместе с записью; «Отмена» выбрасывает добавленные. «Поделиться»
 * отправляет то, что сейчас в форме: подпись ([diaperShareText]) и фото — в выбранное приложение.
 *
 * [child] — чей подгузник: пол для «покакал / покакала», имя и возраст для подписи.
 * [initialPhotos] — фото записи из базы при правке.
 */
@Composable
internal fun DiaperSheet(
    initial: Diaper?,
    child: Child,
    initialPhotos: List<EventPhoto>,
    onDismiss: () -> Unit,
    onSave: (kind: DiaperKind, time: Long, note: String, photos: PhotoEdit) -> Unit,
) {
    val isEdit = initial != null
    val sex = child.sex
    var kind by rememberSaveable { mutableStateOf(initial?.kind) }
    var minutesAgo by rememberSaveable { mutableIntStateOf(0) }
    var editTime by rememberSaveable { mutableLongStateOf(initial?.start ?: 0L) }
    var note by rememberSaveable { mutableStateOf(initial?.note ?: "") }
    val photos = rememberFormPhotos(initialPhotos)
    val resolveTime = rememberTimeResolver()
    val clock = LocalClock.current
    val context = LocalContext.current
    val selected = kind

    AppSheet(
        onDismiss = {
            photos.discardPending()
            onDismiss()
        },
        title = "Подгузник",
        icon = R.drawable.ic_diaper,
        confirm = SheetAction("Сохранить", enabled = selected != null && !photos.busy) {
            if (selected != null) onSave(selected, resolveTime(isEdit, minutesAgo, editTime), note.trim(), photos.takeEdit())
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
        PhotosField(photos)
        TextButton(
            onClick = {
                if (selected != null) {
                    val text = diaperShareText(child, selected, resolveTime(isEdit, minutesAgo, editTime), note, clock.millis().toLocalDate())
                    runCatching { shareWithPhotos(context, text, photos.jpegs()) }
                        .onFailure { photos.error = "Не удалось поделиться" }
                }
            },
            enabled = selected != null && !photos.busy,
        ) {
            AppIcon(R.drawable.ic_share, contentDescription = null)
            Spacer(Modifier.width(BabyLogTheme.spacing.s))
            Text("Поделиться")
        }
    }

    PhotoSheets(photos)
}

