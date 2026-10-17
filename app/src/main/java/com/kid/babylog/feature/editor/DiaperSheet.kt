package com.kid.babylog.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.kid.babylog.core.ui.photo.PhotoSourceSheet
import com.kid.babylog.core.ui.photo.deletePhotoFile
import com.kid.babylog.core.ui.photo.prepareEventPhoto
import com.kid.babylog.core.ui.photo.rememberPhotoPicker
import com.kid.babylog.core.ui.photo.shareWithPhotos
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.core.ui.toLocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Подгузник: вид, время, комментарий и фото (ADR-0024). Фото — до [EventPhoto.MAX_PER_EVENT]: снять
 * или выбрать в галерее ([PhotoSourceSheet]), открыть крупно и убрать ([PhotoViewSheet]). В базу они
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
    var photos by rememberSaveable(stateSaver = FormPhotosSaver) {
        mutableStateOf<List<FormPhoto>>(initialPhotos.map { FormPhoto.Saved(it.id) })
    }
    var photoMenu by rememberSaveable { mutableStateOf(false) }
    var viewing by rememberSaveable { mutableIntStateOf(NONE) }
    var photoError by rememberSaveable { mutableStateOf<String?>(null) }
    var processing by remember { mutableIntStateOf(0) }
    val resolveTime = rememberTimeResolver()
    val clock = LocalClock.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = remember(initialPhotos) { initialPhotos.associate { it.id to it.jpeg } }
    val jpegOf = { photo: FormPhoto ->
        when (photo) {
            is FormPhoto.Saved -> saved[photo.id]
            is FormPhoto.Pending -> readPhoto(photo.path)
        }
    }
    val remaining = EventPhoto.MAX_PER_EVENT - photos.size
    // Здесь, а не в листе «Фото»: он закрывается раньше, чем камера вернёт снимок
    val picker = rememberPhotoPicker(
        onPicked = { paths ->
            photoError = null
            processing += paths.size
            scope.launch {
                val prepared = withContext(Dispatchers.IO) {
                    paths.mapNotNull { path -> runCatching { prepareEventPhoto(context, path) }.getOrNull().also { deletePhotoFile(path) } }
                }
                processing -= paths.size
                if (prepared.size < paths.size) photoError = "Не удалось открыть фото"
                photos = photos + prepared.map(FormPhoto::Pending)
            }
        },
        onError = { photoError = it },
        maxItems = remaining.coerceAtLeast(1),
    )
    // Новые фото, не дошедшие до базы, — не храним
    val discardPending = { photos.filterIsInstance<FormPhoto.Pending>().forEach { deletePhotoFile(it.path) } }
    val selected = kind

    AppSheet(
        onDismiss = {
            discardPending()
            onDismiss()
        },
        title = "Подгузник",
        icon = R.drawable.ic_diaper,
        confirm = SheetAction("Сохранить", enabled = selected != null && processing == 0) {
            if (selected != null) {
                val edit = photos.toEdit(::readPhoto)
                discardPending()
                onSave(selected, resolveTime(isEdit, minutesAgo, editTime), note.trim(), edit)
            }
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
        Column(verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s)) {
            Text("Фото:", style = MaterialTheme.typography.titleSmall)
            PhotoStrip(
                photos = photos,
                jpegOf = jpegOf,
                canAdd = remaining > 0,
                processing = processing > 0,
                onAdd = { photoMenu = true },
                onOpen = { viewing = it },
            )
            photoError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        TextButton(
            onClick = {
                if (selected != null) {
                    val text = diaperShareText(child, selected, resolveTime(isEdit, minutesAgo, editTime), note, clock.millis().toLocalDate())
                    runCatching { shareWithPhotos(context, text, photos.mapNotNull(jpegOf)) }
                        .onFailure { photoError = "Не удалось поделиться" }
                }
            },
            enabled = selected != null && processing == 0,
        ) {
            AppIcon(R.drawable.ic_share, contentDescription = null)
            Spacer(Modifier.width(BabyLogTheme.spacing.s))
            Text("Поделиться")
        }
    }

    if (photoMenu) {
        PhotoSourceSheet(
            onCamera = {
                photoMenu = false
                picker.takePhoto()
            },
            onGallery = {
                photoMenu = false
                picker.pickFromGallery()
            },
            onDismiss = { photoMenu = false },
        )
    }

    photos.getOrNull(viewing)?.let { photo ->
        PhotoViewSheet(
            jpeg = jpegOf(photo),
            onRemove = {
                if (photo is FormPhoto.Pending) deletePhotoFile(photo.path)
                photos = photos - photo
                viewing = NONE
            },
            onDismiss = { viewing = NONE },
        )
    }
}

private fun readPhoto(path: String): ByteArray? = File(path).takeIf(File::exists)?.readBytes()

private const val NONE = -1
