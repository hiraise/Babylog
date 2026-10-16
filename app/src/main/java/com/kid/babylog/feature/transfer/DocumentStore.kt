package com.kid.babylog.feature.transfer

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        resolver.openInputStream(Uri.parse(uri))?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Не удалось открыть файл")
    }

    override suspend fun write(uri: String, text: String) = withContext(Dispatchers.IO) {
        resolver.openOutputStream(Uri.parse(uri), "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: error("Не удалось открыть файл")
    }
}
