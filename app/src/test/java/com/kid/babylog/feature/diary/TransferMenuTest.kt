package com.kid.babylog.feature.diary

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.core.net.toUri
import com.kid.babylog.core.data.ThemeChoice
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.PhotoEdit
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.feature.analytics.TALL_PIXEL_5
import com.kid.babylog.feature.transfer.exportArchive
import com.kid.babylog.testing.ComponentActivityRule
import com.kid.babylog.testing.FakeDiaryRepository
import com.kid.babylog.testing.FakeDocumentStore
import com.kid.babylog.testing.FakeSelectedChildStore
import com.kid.babylog.testing.testDevelopment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Перенос дневника из меню ⋮ (ADR-0045): «Экспорт в файл» предлагает сохранить архив .zip и пишет в выбранный
 * файл записи с фото, «Импорт из файла» берёт любой файл — архив или CSV — и возвращает фото.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PIXEL_5)
class TransferMenuTest {

    @get:Rule(order = 0)
    val activity = ComponentActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneOffset.UTC)
    private val documents = FakeDocumentStore()

    /** Системный выбор файла вместо настоящего: запоминает запрос, ответ даёт тест. */
    private val picker = object : ActivityResultRegistry() {
        val launched = mutableListOf<Launch>()

        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            launched += Launch(requestCode, input, contract.createIntent(RuntimeEnvironment.getApplication(), input).type)
        }
    }

    private class Launch(val requestCode: Int, val input: Any?, val mimeType: String?)

    private fun show(repository: FakeDiaryRepository) {
        val viewModel = DiaryViewModel(repository, FakeSelectedChildStore(), documents, clock)
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = picker
        }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                BabyLogTheme(clock = clock, darkTheme = false) {
                    DiaryScreen(viewModel, ThemeChoice(), onThemeChange = {}, development = testDevelopment())
                }
            }
        }
    }

    private fun menu(item: String): Launch {
        compose.onNodeWithContentDescription("Меню").performClick()
        compose.onNodeWithText(item).performClick()
        compose.waitForIdle()
        return picker.launched.last()
    }

    @Test
    fun `export saves records and photos to the zip the user picked`() {
        val repository = FakeDiaryRepository()
        runBlocking {
            val diaper = repository.addEvent(Diaper(childId = 1, start = clock.millis(), kind = DiaperKind.DIRTY))
            repository.setEventPhotos(diaper, PhotoEdit(emptyList(), listOf(JPEG)))
        }
        show(repository)

        val export = menu("Экспорт в файл")
        assertEquals("baby-diary-2026-10-22.zip", export.input)
        assertEquals("application/zip", export.mimeType)

        compose.runOnIdle { picker.dispatchResult(export.requestCode, "content://picked/diary.zip".toUri()) }
        compose.waitUntil { documents.files.containsKey("content://picked/diary.zip") }
        compose.waitUntil { compose.onAllNodes(hasText("Экспортировано записей: 1, фото: 1")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `import takes any file and brings photos back`() {
        val source = FakeDiaryRepository()
        runBlocking {
            val diaper = source.addEvent(Diaper(childId = 1, start = clock.millis(), kind = DiaperKind.DIRTY))
            source.setEventPhotos(diaper, PhotoEdit(emptyList(), listOf(JPEG)))
            documents.write("content://picked/diary.zip") { exportArchive(source, it) }
        }
        val target = FakeDiaryRepository()
        show(target)

        val import = menu("Импорт из файла")
        assertEquals(listOf("*/*"), (import.input as Array<*>).toList())

        compose.runOnIdle { picker.dispatchResult(import.requestCode, "content://picked/diary.zip".toUri()) }
        compose.waitUntil { compose.onAllNodes(hasText("Импорт: добавлено 1, фото 1")).fetchSemanticsNodes().isNotEmpty() }
        assertArrayEquals(JPEG, target.photos.value.values.single().single().jpeg)
    }

    private companion object {
        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
    }
}
