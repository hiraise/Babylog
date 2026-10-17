package com.kid.babylog.core.ui.photo

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Снимок с камеры или фото из галереи; результат — пути к файлам в кэше приложения. */
@Stable
class PhotoPicker(private val camera: () -> Unit, private val gallery: () -> Unit) {
    fun takePhoto() = camera()

    fun pickFromGallery() = gallery()
}

/**
 * Камера и галерея без разрешений (ADR-0023): снимок делает системное приложение камеры в файл
 * кэша через `FileProvider`, фото выбирается системным пикером и копируется в кэш. Файл в кэше,
 * а не ссылка из пикера: обработка должна пережить поворот экрана и перезапуск процесса.
 *
 * Вызывай в composable, который остаётся на экране, пока открыта камера, — не в листе-меню, который
 * закрывается при выборе пункта: иначе результат придёт без получателя. [maxItems] больше 1 —
 * в галерее можно выбрать несколько фото; лишние отбрасываются с сообщением в [onError].
 * [onPicked] получает пути; файлы после обработки удаляй ([deletePhotoFile]).
 */
@Composable
fun rememberPhotoPicker(onPicked: (paths: List<String>) -> Unit, onError: (String) -> Unit, maxItems: Int = 1): PhotoPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    val error by rememberUpdatedState(onError)
    val limit by rememberUpdatedState(maxItems)
    val cameraFile = remember(context) { photoCacheFile(context, CAMERA_FILE) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved && cameraFile.length() > 0) picked(listOf(cameraFile.path))
    }
    val receive = { uris: List<Uri> ->
        if (uris.size > limit) error("Можно добавить ещё $limit фото — остальные не добавлены")
        scope.launch {
            val files = withContext(Dispatchers.IO) { uris.take(limit).mapNotNull { runCatching { copyToCache(context, it) }.getOrNull() } }
            if (files.size < minOf(uris.size, limit)) error("Не удалось открыть фото")
            if (files.isNotEmpty()) picked(files.map { it.path })
        }
    }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) receive(listOf(uri))
    }
    // Пикер нескольких фото требует предел больше 1; сколько ещё можно — решает limit
    val multiple = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxOf(maxItems, 2))) { uris ->
        if (uris.isNotEmpty()) receive(uris)
    }
    return remember(context, camera, single, multiple) {
        PhotoPicker(
            camera = {
                // Прежний снимок не должен выдать себя за новый, если камера ничего не записала
                cameraFile.delete()
                val uri = FileProvider.getUriForFile(context, providerAuthority(context), cameraFile)
                try {
                    camera.launch(uri)
                } catch (_: ActivityNotFoundException) {
                    error("На телефоне нет приложения камеры")
                }
            },
            gallery = {
                val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                if (limit > 1) multiple.launch(request) else single.launch(request)
            },
        )
    }
}

/** Удаляет файл фото из кэша: оригиналы не храним (ADR-0023). */
fun deletePhotoFile(path: String) {
    File(path).delete()
}

/** Новый файл в `cache/photos/` для обработанного фото, ещё не сохранённого в базу. */
fun newPhotoCacheFile(context: Context): File = photoCacheFile(context, "pending-${UUID.randomUUID()}.jpg")

private fun copyToCache(context: Context, uri: Uri): File {
    val file = photoCacheFile(context, "gallery-${UUID.randomUUID()}")
    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { input.copyTo(it) }
    } ?: error("Не удалось открыть фото")
    return file
}

/** Файл в `cache/photos/` — эту папку `FileProvider` открывает камере (`res/xml/photo_paths.xml`). */
private fun photoCacheFile(context: Context, name: String): File =
    File(context.cacheDir, PHOTOS_DIR).apply { mkdirs() }.resolve(name)

/** Authority провайдера — `${applicationId}.photos`, как в `AndroidManifest.xml`. */
internal fun providerAuthority(context: Context): String = "${context.packageName}.photos"

private const val CAMERA_FILE = "camera.jpg"
