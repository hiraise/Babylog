package com.kid.babylog.feature.transfer

import com.kid.babylog.core.data.DiaryRepository
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.EventPhoto
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Архив экспорта (ADR-0045): первым `diary.csv` по ADR-0009, затем фото в `photos/`. Колонка `photos` в CSV
 * связывает строку с файлами фото.
 */
const val ARCHIVE_CSV = "diary.csv"
private const val PHOTOS_DIR = "photos/"
private val PHOTO_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

data class DiaryExport(val events: Int, val photos: Int)

fun DiaryExport.describe(): String = buildString {
    append("Экспортировано записей: $events")
    if (photos > 0) append(", фото: $photos")
}

/**
 * Весь дневник с фото — архивом в [output]. Дети, записи и список фото читаются одной транзакцией, а сами фото —
 * по одному после неё: всё сразу в память не поместится, а долгая транзакция задержала бы запись в дневник.
 * Фото, удалённое, пока шёл экспорт, в архив не попадёт — импорт посчитает его не загруженным.
 */
suspend fun exportArchive(
    repository: DiaryRepository,
    output: OutputStream,
    zone: ZoneId = ZoneId.systemDefault(),
): DiaryExport {
    val (children, events, photoIds) = repository.inTransaction {
        Triple(repository.allChildren(), repository.allEvents(), repository.photoIds())
    }
    val files = PhotoFiles(zone)
    val childFiles = children.withIndex().filter { it.value.photo != null }.associate { (i, child) -> child.id to files.child(i) }
    val eventFiles = events.filter { it.id in photoIds }.associate { event ->
        event.id to photoIds.getValue(event.id).map { photoId -> photoId to files.event(event) }
    }
    val zip = ZipOutputStream(output)
    zip.putNextEntry(ZipEntry(ARCHIVE_CSV))
    val csv = CsvCodec.encode(children, events, childFiles, eventFiles.mapValues { (_, list) -> list.map { it.second } })
    zip.write(csv.toByteArray(Charsets.UTF_8))
    zip.closeEntry()
    var photos = 0
    for (child in children) {
        val jpeg = child.photo?.jpeg ?: continue
        zip.putStored(childFiles.getValue(child.id), jpeg, time = null)
        photos++
    }
    for (event in events) {
        for ((photoId, name) in eventFiles[event.id].orEmpty()) {
            val jpeg = repository.photoJpeg(photoId) ?: continue
            zip.putStored(name, jpeg, time = event.start)
            photos++
        }
    }
    zip.finish()
    return DiaryExport(events.size, photos)
}

/**
 * Имена файлов фото — только ASCII: кириллицу в именах понимают не все распаковщики. Фото записи — по её
 * началу (`photos/2026-08-15_15-10-00_1.jpg`): в папке они идут по времени; фото ребёнка — по его порядку.
 */
private class PhotoFiles(private val zone: ZoneId) {
    private val used = HashMap<String, Int>()

    fun child(index: Int) = "${PHOTOS_DIR}child_${index + 1}.jpg"

    fun event(event: BabyEvent): String {
        val base = PHOTOS_DIR + Instant.ofEpochMilli(event.start).atZone(zone).format(PHOTO_TIME)
        val n = (used[base] ?: 0) + 1
        used[base] = n
        return "${base}_$n.jpg"
    }
}

/** JPEG уже сжат: без сжатия (`STORED`) архив пишется быстрее, а места не прибавилось бы. */
private fun ZipOutputStream.putStored(name: String, bytes: ByteArray, time: Long?) {
    val entry = ZipEntry(name).apply {
        method = ZipEntry.STORED
        size = bytes.size.toLong()
        compressedSize = bytes.size.toLong()
        crc = CRC32().apply { update(bytes) }.value
        // Дата файла — время записи: так фото удобно смотреть в распакованной папке
        if (time != null) this.time = time
    }
    putNextEntry(entry)
    write(bytes)
    closeEntry()
}

/**
 * Импорт файла [uri] (ADR-0045): архива экспорта с фото или CSV (ADR-0009) — архив узнаётся по сигнатуре.
 * Архив читается дважды: сначала CSV, затем фото, — копии файла на диске не нужно. Всё — одной транзакцией:
 * если архив не дочитался, дневник не меняется.
 */
suspend fun importFile(repository: DiaryRepository, documents: DocumentStore, uri: String, defaultChildId: Long): ImportResult {
    val file = documents.read(uri) { readImportFile(it) }
    val csvDir = file.csvDir ?: return importCsv(repository, file.text, defaultChildId)
    return repository.inTransaction {
        val importer = CsvImporter(repository, defaultChildId)
        importer.run(file.text)
        if (importer.wantedPhotos.isNotEmpty()) {
            documents.read(uri) { input -> readArchivePhotos(input, csvDir, importer.wantedPhotos, importer::addPhoto) }
        }
        importer.finish(archive = true)
    }
}

/** Текст CSV из файла импорта; [csvDir] — папка `diary.csv` в архиве (обычно корень, ""), null — файл не архив. */
internal class ImportFile(val text: String, val csvDir: String?)

/** Первое чтение: CSV как есть или `diary.csv` из архива. Архив, распакованный и собранный заново, — тоже: CSV в папке. */
internal fun readImportFile(input: InputStream): ImportFile {
    val buffered = BufferedInputStream(input)
    buffered.mark(ZIP_SIGNATURE_SIZE)
    val head = ByteArray(ZIP_SIGNATURE_SIZE)
    var size = 0
    while (size < head.size) {
        val read = buffered.read(head, size, head.size - size)
        if (read < 0) break
        size += read
    }
    buffered.reset()
    if (!isZip(head, size)) return ImportFile(readCsvText(buffered), csvDir = null)
    return readingArchive {
        val zip = archiveInput(buffered)
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory && isArchiveCsv(entry.name)) {
                return ImportFile(readCsvText(zip), csvDir = entry.name.removeSuffix(ARCHIVE_CSV))
            }
        }
        throw IllegalArgumentException("в архиве нет $ARCHIVE_CSV — это не дневник BabyLog")
    }
}

/**
 * Второе чтение: фото из [wanted] (пути от папки CSV [csvDir]) — в [onPhoto] по одному. Файл больше
 * [EventPhoto.MAX_BYTES] или не JPEG приходит как null: целиком в память его не читаем.
 */
internal suspend fun readArchivePhotos(
    input: InputStream,
    csvDir: String,
    wanted: Set<String>,
    onPhoto: suspend (name: String, jpeg: ByteArray?) -> Unit,
) = readingArchive {
    val zip = archiveInput(BufferedInputStream(input))
    while (wanted.isNotEmpty()) {
        val entry = zip.nextEntry ?: break
        if (entry.isDirectory || !entry.name.startsWith(csvDir)) continue
        val name = entry.name.removePrefix(csvDir)
        if (name in wanted) onPhoto(name, readJpeg(zip))
    }
}

/**
 * Имена без флага UTF-8 (архив, собранный заново в Windows с кириллицей в имени папки) читаются как Latin-1:
 * в UTF-8 они не разбираются и обрывали бы импорт, а сверяются имена всё равно внутри одного архива.
 * Имена с флагом UTF-8 — наши и большинства архиваторов — читаются как UTF-8.
 */
private fun archiveInput(input: InputStream) = ZipInputStream(input, Charsets.ISO_8859_1)

private fun isZip(head: ByteArray, size: Int): Boolean {
    if (size < ZIP_SIGNATURE_SIZE || head[0] != 'P'.code.toByte() || head[1] != 'K'.code.toByte()) return false
    // Локальный заголовок файла или пустой архив
    return (head[2].toInt() == 3 && head[3].toInt() == 4) || (head[2].toInt() == 5 && head[3].toInt() == 6)
}

/** `diary.csv` в корне или в папке; служебная папка архиватора macOS — не дневник. */
private fun isArchiveCsv(name: String): Boolean =
    name == ARCHIVE_CSV || (name.endsWith("/$ARCHIVE_CSV") && !name.startsWith("__MACOSX/"))

private fun readJpeg(input: InputStream): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        out.write(buffer, 0, read)
        if (out.size() > EventPhoto.MAX_BYTES) return null
    }
    val bytes = out.toByteArray()
    val jpeg = bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
    return bytes.takeIf { jpeg }
}

/** Оборванный или испорченный архив — понятное сообщение вместо текста исключения ZIP. */
private inline fun <T> readingArchive(block: () -> T): T = try {
    block()
} catch (e: ZipException) {
    throw IllegalArgumentException("архив повреждён — сохраните его заново", e)
} catch (e: EOFException) {
    throw IllegalArgumentException("архив оборван — сохраните его заново", e)
}

private const val ZIP_SIGNATURE_SIZE = 4
