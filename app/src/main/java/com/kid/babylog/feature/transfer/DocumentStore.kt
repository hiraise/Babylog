package com.kid.babylog.feature.transfer

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Чтение и запись файла, который пользователь выбрал в системном диалоге (SAF).
 * Интерфейс отделяет ViewModel от Android: в тестах его заменяет фейк.
 */
interface DocumentStore {
    suspend fun read(uri: String): String

    suspend fun write(uri: String, text: String)
}

class ContentResolverDocumentStore(context: Context) : DocumentStore {

    private val resolver = context.applicationContext.contentResolver

    override suspend fun read(uri: String): String = withContext(Dispatchers.IO) {
        resolver.openInputStream(Uri.parse(uri))?.use(::readCsvText) ?: error("Не удалось открыть файл")
    }

    override suspend fun write(uri: String, text: String) = withContext(Dispatchers.IO) {
        resolver.openOutputStream(Uri.parse(uri), "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: error("Не удалось открыть файл")
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
