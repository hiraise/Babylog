package com.kid.babylog.feature.transfer

import android.content.Context
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Чтение и запись файла, который пользователь выбрал в системном диалоге (SAF). Файл отдаётся потоком: архив
 * с фото бывает в сотни мегабайт (ADR-0045). Интерфейс отделяет ViewModel от Android: в тестах его заменяет фейк.
 */
interface DocumentStore {
    /** Открывает файл [uri] на чтение — каждый раз с начала — и отдаёт поток [block]; закрывает после него. */
    suspend fun <T> read(uri: String, block: suspend (InputStream) -> T): T

    /** Открывает файл [uri] на запись с нуля и отдаёт поток [block]; закрывает после него. */
    suspend fun <T> write(uri: String, block: suspend (OutputStream) -> T): T
}

class ContentResolverDocumentStore(context: Context) : DocumentStore {

    private val resolver = context.applicationContext.contentResolver

    override suspend fun <T> read(uri: String, block: suspend (InputStream) -> T): T = withContext(Dispatchers.IO) {
        val input = resolver.openInputStream(uri.toUri()) ?: error("Не удалось открыть файл")
        input.use { block(it) }
    }

    override suspend fun <T> write(uri: String, block: suspend (OutputStream) -> T): T = withContext(Dispatchers.IO) {
        val output = resolver.openOutputStream(uri.toUri(), "wt") ?: error("Не удалось открыть файл")
        output.buffered().use { block(it) }
    }
}

/** Больше не бывает: дневник за годы — несколько мегабайт CSV. */
internal const val MAX_IMPORT_BYTES = 20 * 1024 * 1024

/**
 * Текст CSV из [input]. Выбор файла пропускает любой тип: у CSV в разных приложениях разный MIME-тип.
 * Поэтому читаем не больше [MAX_IMPORT_BYTES] — видео, выбранное по ошибке, не должно кончиться нехваткой
 * памяти, — а двоичный файл (нулевой байт в тексте) отклоняем сразу.
 */
internal fun readCsvText(input: InputStream): String {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        out.write(buffer, 0, read)
        require(out.size() <= MAX_IMPORT_BYTES) { "файл больше 20 МБ — это не дневник BabyLog" }
    }
    val text = out.toByteArray().toString(Charsets.UTF_8)
    require('\u0000' !in text) { "это не CSV-файл" }
    return text
}
