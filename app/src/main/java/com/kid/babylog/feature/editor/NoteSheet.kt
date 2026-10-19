package com.kid.babylog.feature.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.kid.babylog.R
import com.kid.babylog.core.model.EventPhoto
import com.kid.babylog.core.model.Note
import com.kid.babylog.core.model.PhotoEdit
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.components.SheetAction

/** Поле «Что заметили» — три строки сразу: заметка — фраза, а не слово. */
private const val TEXT_MIN_LINES = 3

/**
 * Заметка (ADR-0031): что заметили, метки ([TagPicker], варианты [tagOptions] — [noteTagChoices]:
 * метки заметок чипами, метки плача — подсказками), когда и фото, как у подгузника ([PhotosField]).
 * «Сохранить» доступно, когда есть текст, метка или фото: пустая заметка ничего не говорит. У новой
 * заметки поле текста сразу в фокусе — заметку начинают со слов. [initialPhotos] — фото записи при правке.
 */
@Composable
internal fun NoteSheet(
    initial: Note?,
    tagOptions: TagChoices,
    initialPhotos: List<EventPhoto>,
    onDismiss: () -> Unit,
    onSave: (text: String, tags: List<String>, time: Long, photos: PhotoEdit) -> Unit,
) {
    val isEdit = initial != null
    var text by rememberSaveable { mutableStateOf(initial?.note ?: "") }
    var tags by rememberSaveable { mutableStateOf(initial?.tags.orEmpty().packed()) }
    var minutesAgo by rememberSaveable { mutableIntStateOf(0) }
    var editTime by rememberSaveable { mutableLongStateOf(initial?.start ?: 0L) }
    val photos = rememberFormPhotos(initialPhotos)
    val resolveTime = rememberTimeResolver()
    val selected = tags.unpacked()
    val focus = remember { FocusRequester() }
    val filled = text.isNotBlank() || selected.isNotEmpty() || photos.photos.isNotEmpty()

    AppSheet(
        onDismiss = {
            photos.discardPending()
            onDismiss()
        },
        title = "Заметка",
        icon = R.drawable.ic_note,
        confirm = SheetAction("Сохранить", enabled = filled && !photos.busy) {
            onSave(text.trim(), selected, resolveTime(isEdit, minutesAgo, editTime), photos.takeEdit())
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Что заметили") },
            minLines = TEXT_MIN_LINES,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        // Здесь, после поля: лист — своё окно, и до него запрос фокуса не дойдёт
        if (!isEdit) LaunchedEffect(Unit) { focus.requestFocus() }
        TagPicker("Метки", tagOptions.shown, selected, { tags = it.packed() }, addLabel = "своя метка", other = tagOptions.other, otherTitle = "Метки плача:")
        TimeSection(isEdit, minutesAgo, { minutesAgo = it }, editTime, { editTime = it })
        PhotosField(photos, title = "Фото — например, сыпь или покраснение:")
    }

    PhotoSheets(photos)
}
