package com.kid.babylog.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.kid.babylog.core.model.EventPhoto
import com.kid.babylog.core.model.PhotoEdit
import com.kid.babylog.core.ui.photo.PhotoSourceSheet
import com.kid.babylog.core.ui.photo.deletePhotoFile
import com.kid.babylog.core.ui.photo.prepareEventPhoto
import com.kid.babylog.core.ui.photo.rememberPhotoPicker
import com.kid.babylog.core.ui.theme.BabyLogTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val NONE = -1

/**
 * Фото в листе записи (ADR-0024) — у подгузника и заметки: что сейчас в форме, сколько ещё
 * готовится, ошибка, открыт ли лист «Фото» и какое фото смотрят крупно. Создаёт [rememberFormPhotos];
 * строку фото показывает [PhotosField] внутри листа, листы «Фото» и просмотра — [PhotoSheets] рядом с ним.
 * В базу фото попадают по «Сохранить» ([takeEdit]); закрытый без сохранения лист выбрасывает новые ([discardPending]).
 */
@Stable
internal class FormPhotosState(
    photos: MutableState<List<FormPhoto>>,
    error: MutableState<String?>,
    processing: MutableIntState,
    menuOpen: MutableState<Boolean>,
    viewing: MutableIntState,
    private val saved: Map<Long, ByteArray>,
) {
    var photos by photos
    var error by error
    var processing by processing
    var menuOpen by menuOpen
    var viewing by viewing

    /** Сколько фото ещё можно добавить. */
    val remaining: Int get() = EventPhoto.MAX_PER_EVENT - photos.size

    /** Новые фото ещё уменьшаются: «Сохранить» и «Поделиться» ждут. */
    val busy: Boolean get() = processing > 0

    fun jpegOf(photo: FormPhoto): ByteArray? = when (photo) {
        is FormPhoto.Saved -> saved[photo.id]
        is FormPhoto.Pending -> readPhoto(photo.path)
    }

    /** Байты всех фото формы — для «Поделиться». */
    fun jpegs(): List<ByteArray> = photos.mapNotNull(::jpegOf)

    /** Фото для сохранения вместе с записью; файлы новых фото после этого не нужны. */
    fun takeEdit(): PhotoEdit = photos.toEdit(::readPhoto).also { discardPending() }

    /** Новые фото, не дошедшие до базы, — не храним. */
    fun discardPending() = photos.filterIsInstance<FormPhoto.Pending>().forEach { deletePhotoFile(it.path) }
}

/** Фото формы: при правке — [initialPhotos] записи из базы. Переживает поворот, как остальные поля листа. */
@Composable
internal fun rememberFormPhotos(initialPhotos: List<EventPhoto>): FormPhotosState {
    val photos = rememberSaveable(stateSaver = FormPhotosSaver) {
        mutableStateOf<List<FormPhoto>>(initialPhotos.map { FormPhoto.Saved(it.id) })
    }
    val error = rememberSaveable { mutableStateOf<String?>(null) }
    val menuOpen = rememberSaveable { mutableStateOf(false) }
    val viewing = rememberSaveable { mutableIntStateOf(NONE) }
    val processing = remember { mutableIntStateOf(0) }
    val saved = remember(initialPhotos) { initialPhotos.associate { it.id to it.jpeg } }
    return remember(saved) { FormPhotosState(photos, error, processing, menuOpen, viewing, saved) }
}

/** «Фото:» и строка миниатюр с «+»; ошибка — под ней. Кладётся в содержимое листа записи. */
@Composable
internal fun PhotosField(state: FormPhotosState, title: String = "Фото:") {
    Column(verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        PhotoStrip(
            photos = state.photos,
            jpegOf = state::jpegOf,
            canAdd = state.remaining > 0,
            processing = state.busy,
            onAdd = { state.menuOpen = true },
            onOpen = { state.viewing = it },
        )
        state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

/**
 * Лист «Фото» (камера или галерея) и просмотр фото — рядом с листом записи, а не внутри него. Здесь же
 * выбор фото: он должен жить, пока открыт лист записи, — лист «Фото» закрывается раньше, чем камера
 * вернёт снимок.
 */
@Composable
internal fun PhotoSheets(state: FormPhotosState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberPhotoPicker(
        onPicked = { paths ->
            state.error = null
            state.processing += paths.size
            scope.launch {
                val prepared = withContext(Dispatchers.IO) {
                    paths.mapNotNull { path -> runCatching { prepareEventPhoto(context, path) }.getOrNull().also { deletePhotoFile(path) } }
                }
                state.processing -= paths.size
                if (prepared.size < paths.size) state.error = "Не удалось открыть фото"
                state.photos = state.photos + prepared.map(FormPhoto::Pending)
            }
        },
        onError = { state.error = it },
        maxItems = state.remaining.coerceAtLeast(1),
    )

    if (state.menuOpen) {
        PhotoSourceSheet(
            onCamera = {
                state.menuOpen = false
                picker.takePhoto()
            },
            onGallery = {
                state.menuOpen = false
                picker.pickFromGallery()
            },
            onDismiss = { state.menuOpen = false },
        )
    }

    state.photos.getOrNull(state.viewing)?.let { photo ->
        PhotoViewSheet(
            jpeg = state.jpegOf(photo),
            onRemove = {
                if (photo is FormPhoto.Pending) deletePhotoFile(photo.path)
                state.photos = state.photos - photo
                state.viewing = NONE
            },
            onDismiss = { state.viewing = NONE },
        )
    }
}

private fun readPhoto(path: String): ByteArray? = File(path).takeIf(File::exists)?.readBytes()
