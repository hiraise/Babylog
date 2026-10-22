package com.kid.babylog.feature.transfer

import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.EventRecord
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.toRecord
import com.kid.babylog.feature.help.SAMPLE_AGE_DAYS
import com.kid.babylog.feature.help.SAMPLE_CHILD_NAME
import com.kid.babylog.feature.help.sampleMonths
import com.kid.babylog.testing.FakeDiaryRepository
import java.io.File
import java.lang.reflect.Modifier
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Тестовый дневник для `scripts/dev.sh seed` (скилл `/seed`): девочка «Соня» с рождения по сегодня,
 * записи — из [sampleMonths], всех типов. Тест проверяет, что файл импортируется без ошибок,
 * и кладёт его в `app/build/seed/`. Время отсчитывается от сейчас, поэтому повторный импорт
 * удвоил бы записи — перед ним «Соню» удаляют.
 */
class SeedCsvTest {

    @Test
    fun `seed diary covers every record type and imports without errors`() = runTest {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val birth = today.minusDays(SAMPLE_AGE_DAYS)
        val child = Child(1, SAMPLE_CHILD_NAME, birth.atStartOfDay(zone).toInstant().toEpochMilli(), Sex.GIRL)
        // Идущие таймеры не берём: незавершённый сон импорт пропустит, а кормление станет записью без длительности
        val events = sampleMonths(child.id, today, Instant.now().toEpochMilli(), zone, days = SAMPLE_AGE_DAYS.toInt())
            .filterNot { it is Sleep && it.end == null || it is Feeding && it.end == null }
            .sortedBy { it.start }

        assertEquals(
            "Нет записей этих типов — добавь их в sampleMonths (testing/SampleMonths.kt)",
            emptySet<String>(),
            recordTypes() - events.map { it.toRecord().type }.toSet(),
        )
        val csv = CsvCodec.encode(listOf(child), events)
        val result = importCsv(FakeDiaryRepository(), csv, defaultChildId = 1)
        assertEquals(0, result.errors)
        assertEquals(events.size, result.added)

        File("build/seed").apply { mkdirs() }.resolve(FILE_NAME).writeText(csv)
    }

    /** Все типы записей: коды в `EventRecord` — константы его companion, статические поля класса. */
    private fun recordTypes(): Set<String> = EventRecord::class.java.declaredFields
        .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
        .map { it.get(null) as String }
        .toSet()

    private companion object {
        const val FILE_NAME = "babylog-seed.csv"
    }
}
