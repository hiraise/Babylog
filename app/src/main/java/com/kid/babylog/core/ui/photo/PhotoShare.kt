package com.kid.babylog.core.ui.photo

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import com.kid.babylog.core.model.EventPhoto
import java.io.File
import java.util.UUID

/**
 * Фото записи из файла [source] (снимок или копия из галереи) — JPEG не больше [EventPhoto.MAX_SIDE]
 * по длинной стороне, в новом файле кэша; путь к нему. Исходник не трогает.
 */
fun prepareEventPhoto(context: Context, source: String): String {
    val bitmap = loadUpright(source, EventPhoto.MAX_SIDE * 2).scaledToFit(EventPhoto.MAX_SIDE)
    return newPhotoCacheFile(context).apply { writeBytes(fittingJpeg(bitmap)) }.path
}

/**
 * JPEG не больше [maxBytes]: строка базы с фото должна уместиться в окно курсора SQLite (2 МБ), иначе запись
 * не прочитается. Очень детальное фото сжимается сильнее, а если не влезло и так — уменьшается на четверть.
 */
internal fun fittingJpeg(bitmap: Bitmap, maxBytes: Int = EventPhoto.MAX_BYTES): ByteArray {
    var current = bitmap
    while (true) {
        JPEG_QUALITIES.forEach { quality -> current.toJpeg(quality).let { if (it.size <= maxBytes) return it } }
        current = current.scale(maxOf(1, current.width * 3 / 4), maxOf(1, current.height * 3 / 4))
    }
}

/**
 * Системное «Поделиться» (ADR-0024): [text] и фото [jpegs] — в приложение, которое выберет
 * пользователь (Telegram, WhatsApp, почта). Само приложение в сеть не ходит. Фото копируются
 * в `cache/share/` и отдаются через `FileProvider` только на чтение. У каждой отправки своя папка:
 * прежнюю, может быть, ещё читает приложение, куда отправили (почта берёт вложения не сразу); старые
 * копии удаляет [sweepPhotoCache].
 */
fun shareWithPhotos(context: Context, text: String, jpegs: List<ByteArray>) {
    val dir = File(context.cacheDir, SHARE_DIR).resolve(UUID.randomUUID().toString()).apply { mkdirs() }
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
        File(context.cacheDir, name).listFiles()?.filter { now - it.lastModified() > STALE_MS }?.forEach(File::deleteRecursively)
    }
}

/** Качество JPEG по очереди: первое, которое уложилось в [EventPhoto.MAX_BYTES]. */
private val JPEG_QUALITIES = listOf(85, 70, 50)
private const val STALE_MS = 24 * 60 * 60 * 1000L
private const val SHARE_DIR = "share"
internal const val PHOTOS_DIR = "photos"
