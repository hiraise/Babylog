package com.kid.babylog.feature.diary

import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.kid.babylog.core.data.DarkPalette
import com.kid.babylog.core.data.ThemeChoice
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Height
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Weight
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.testing.FakeDiaryRepository
import com.kid.babylog.testing.FakeDocumentStore
import com.kid.babylog.testing.FakeSelectedChildStore
import com.kid.babylog.testing.SCREENSHOT_OPTIONS
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Главный экран на эталонных скриншотах вместо ручной сверки на эмуляторе (ADR-0013).
 * Эталоны — `app/src/test/screenshots/`; обновить их — `scripts/dev.sh goldens`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class DiaryScreenshotTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private lateinit var systemZone: TimeZone

    // Экран показывает время в поясе устройства: фиксируем его, иначе Mac и CI нарисуют разное
    @Before
    fun fixTimeZone() {
        systemZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    @Test
    fun `day with records, light`() = capture("diary_day_light", sampleDiary(), dark = false)

    @Test
    fun `day with records, dark`() = capture("diary_day_dark", sampleDiary(), dark = true)

    /** Остальные палитры тёмной темы (ADR-0032); палитра по умолчанию — `diary_day_dark`. */
    @Test
    fun `day with records, other dark palettes`() = DarkPalette.entries.filter { it != DarkPalette.DEFAULT }.forEach { palette ->
        capture("diary_day_dark_${palette.name.lowercase()}", sampleDiary(), dark = true, palette = palette)
    }

    @Test
    fun `feeding in progress`() = capture("diary_feeding", sampleDiary(feedingNow = true), dark = false)

    @Test
    fun `crying in progress`() = capture("diary_crying", sampleDiary(cryingNow = true), dark = false)

    @Test
    fun `history tab`() = capture("diary_history", sampleDiary(), dark = false, tab = DiaryTab.HISTORY)

    @Test
    fun `first launch`() = capture("diary_first_launch", FakeDiaryRepository(), dark = false)

    private fun capture(
        name: String,
        repository: FakeDiaryRepository,
        dark: Boolean,
        tab: DiaryTab = DiaryTab.TODAY,
        palette: DarkPalette = DarkPalette.DEFAULT,
    ) {
        val viewModel = DiaryViewModel(repository, FakeSelectedChildStore(), FakeDocumentStore(), clock)
        captureRoboImage("src/test/screenshots/$name.png", roborazziOptions = SCREENSHOT_OPTIONS) {
            BabyLogTheme(clock = clock, darkTheme = dark, darkPalette = palette) {
                DiaryScreen(viewModel, ThemeChoice(), onThemeChange = {}, initialTab = tab)
            }
        }
    }

    /**
     * Двое детей; у Маши за сегодня записи всех типов и идёт сон, а при [feedingNow] — кормление,
     * при [cryingNow] — плач (он будит: сна нет).
     */
    private fun sampleDiary(feedingNow: Boolean = false, cryingNow: Boolean = false) = FakeDiaryRepository(
        listOf(Child(1, "Маша", at("2026-05-20T00:00:00Z"), Sex.GIRL), Child(2, "Петя", null, Sex.BOY)),
    ).apply {
        events.value = listOf<BabyEvent>(
            Height(id = 9, childId = 1, start = at("2026-10-01T09:00:00Z"), millimeters = 625),
            Weight(id = 1, childId = 1, start = at("2026-10-15T06:10:00Z"), grams = 6350),
            Feeding(id = 2, childId = 1, start = at("2026-10-15T06:30:00Z"), end = at("2026-10-15T06:45:00Z"), breast = Breast.LEFT),
            Diaper(id = 3, childId = 1, start = at("2026-10-15T07:05:00Z"), kind = DiaperKind.MIXED),
            Medicine(id = 4, childId = 1, start = at("2026-10-15T07:20:00Z"), name = "Витамин D", note = "1 капля"),
            Sleep(id = 5, childId = 1, start = at("2026-10-15T07:40:00Z"), end = at("2026-10-15T08:55:00Z")),
            Feeding(
                id = 6, childId = 1, start = at("2026-10-15T09:00:00Z"), end = at("2026-10-15T09:35:00Z"),
                breast = Breast.RIGHT, note = "долго",
            ),
        ) + listOfNotNull(
            Sleep(id = 7, childId = 1, start = at("2026-10-15T09:20:00Z")).takeIf { !feedingNow && !cryingNow },
            Feeding(id = 8, childId = 1, start = at("2026-10-15T09:48:00Z"), breast = Breast.LEFT).takeIf { feedingNow },
            Crying(id = 10, childId = 1, start = at("2026-10-15T09:52:00Z")).takeIf { cryingNow },
        )
    }

    private fun at(iso: String): Long = Instant.parse(iso).toEpochMilli()
}
