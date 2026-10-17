package com.kid.babylog.core.ui.photo

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.kid.babylog.core.model.EventPhoto
import java.io.File

/**
 * Фото записи из файла [source] (снимок или копия из галереи) — JPEG не больше [EventPhoto.MAX_SIDE]
 * по длинной стороне, в новом файле кэша; путь к нему. Исходник не трогает.
 * Очень детальное фото сжимается сильнее: строка базы с фото должна уместиться в окно курсора (2 МБ).
 */
fun prepareEventPhoto(context: Context, source: String): String {
    val bitmap = loadUpright(source, EventPhoto.MAX_SIDE * 2).scaledToFit(EventPhoto.MAX_SIDE)
    val jpeg = bitmap.toJpeg(EVENT_JPEG_QUALITY).takeIf { it.size <= MAX_JPEG_BYTES } ?: bitmap.toJpeg(FALLBACK_JPEG_QUALITY)
    return newPhotoCacheFile(context).apply { writeBytes(jpeg) }.path
}

/**
 * Системное «Поделиться» (ADR-0024): [text] и фото [jpegs] — в приложение, которое выберет
 * пользователь (Telegram, WhatsApp, почта). Само приложение в сеть не ходит. Фото копируются
 * в `cache/share/` и отдаются через `FileProvider` только на чтение; прежние копии удаляются —
 * их уже забрали, а держать лишние копии фото незачем.
 */
fun shareWithPhotos(context: Context, text: String, jpegs: List<ByteArray>) {
    val dir = File(context.cacheDir, SHARE_DIR).apply { deleteRecursively(); mkdirs() }
    val uris = jpegs.mapIndexed { i, jpeg ->
        val file = dir.resolve("photo-${i + 1}.jpg").apply { writeBytes(jpeg) }
        FileProvider.getUriForFile(context, providerAuthority(context), file)
    }
    val intent = when (uris.size) {
        0 -> Intent(Intent.ACTION_SEND).setType("text/plain")
        1 -> Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uris.single())
        else -> Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    }.putExtra(Intent.EXTRA_TEXT, text)
    if (uris.isNotEmpty()) {
        // Право на чтение получает выбранное приложение: через createChooser оно передаётся только с ClipData
        intent.clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Поделиться"))
}

/**
 * Удаляет из кэша фото старше суток: снимки и копии, которые не дошли до базы (форму закрыли, процесс
 * убит), и копии для «Поделиться». Вызывается при запуске приложения.
 */
fun sweepPhotoCache(context: Context, now: Long) {
    listOf(PHOTOS_DIR, SHARE_DIR).forEach { name ->
        File(context.cacheDir, name).listFiles()?.filter { now - it.lastModified() > STALE_MS }?.forEach(File::delete)
    }
}

private const val EVENT_JPEG_QUALITY = 85
private const val FALLBACK_JPEG_QUALITY = 70
private const val MAX_JPEG_BYTES = 1_200_000
private const val STALE_MS = 24 * 60 * 60 * 1000L
private const val SHARE_DIR = "share"
internal const val PHOTOS_DIR = "photos"
