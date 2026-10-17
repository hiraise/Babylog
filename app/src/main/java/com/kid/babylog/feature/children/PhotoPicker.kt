package com.kid.babylog.feature.children

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

/** Снимок с камеры или фото из галереи; результат — путь к файлу в кэше приложения. */
@Stable
internal class PhotoPicker(private val camera: () -> Unit, private val gallery: () -> Unit) {
    fun takePhoto() = camera()

    fun pickFromGallery() = gallery()
}

/**
 * Камера и галерея без разрешений (ADR-0023): снимок делает системное приложение камеры в файл
 * кэша через `FileProvider`, фото выбирается системным пикером и копируется в кэш. Файл в кэше,
 * а не ссылка из пикера: кадрирование должно пережить поворот экрана и перезапуск процесса.
 *
 * Вызывай в composable, который остаётся на экране, пока открыта камера, — не в листе-меню, который
 * закрывается при выборе пункта: иначе результат придёт без получателя. [onPicked] получает путь,
 * файл после кадрирования удаляй ([deletePhotoSource]).
 */
@Composable
internal fun rememberPhotoPicker(onPicked: (path: String) -> Unit, onError: (String) -> Unit): PhotoPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    val error by rememberUpdatedState(onError)
    val cameraFile = remember(context) { photoSourceFile(context, CAMERA_FILE) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved && cameraFile.length() > 0) picked(cameraFile.path)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val file = withContext(Dispatchers.IO) { runCatching { copyToCache(context, uri) }.getOrNull() }
                if (file != null) picked(file.path) else error("Не удалось открыть фото")
            }
        }
    }
    return remember(context, camera, gallery) {
        PhotoPicker(
            camera = {
                // Прежний снимок не должен выдать себя за новый, если камера ничего не записала
                cameraFile.delete()
                val uri = FileProvider.getUriForFile(context, "${context.packageName}$PROVIDER_SUFFIX", cameraFile)
                try {
                    camera.launch(uri)
                } catch (_: ActivityNotFoundException) {
                    error("На телефоне нет приложения камеры")
                }
            },
            gallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
}

/** Удаляет файл, из которого кадрировали: оригинал фото не храним (ADR-0023). */
internal fun deletePhotoSource(path: String) {
    File(path).delete()
}

private fun copyToCache(context: Context, uri: Uri): File {
    val file = photoSourceFile(context, GALLERY_FILE)
    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { input.copyTo(it) }
    } ?: error("Не удалось открыть фото")
    return file
}

/** Файл в `cache/photos/` — только эту папку открывает `FileProvider` (`res/xml/photo_paths.xml`). */
private fun photoSourceFile(context: Context, name: String): File =
    File(context.cacheDir, "photos").apply { mkdirs() }.resolve(name)

private const val CAMERA_FILE = "camera.jpg"
private const val GALLERY_FILE = "gallery"

/** Authority провайдера — `${applicationId}.photos`, как в `AndroidManifest.xml`. */
private const val PROVIDER_SUFFIX = ".photos"
