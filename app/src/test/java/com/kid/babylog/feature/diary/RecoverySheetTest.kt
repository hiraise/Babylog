package com.kid.babylog.feature.diary

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kid.babylog.core.data.DatabaseRecovery
import com.kid.babylog.core.data.ThemeChoice
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.feature.analytics.TALL_PIXEL_5
import com.kid.babylog.testing.ComponentActivityRule
import com.kid.babylog.testing.FakeDiaryRepository
import com.kid.babylog.testing.FakeDocumentStore
import com.kid.babylog.testing.FakeSelectedChildStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone

/** Лист после восстановления повреждённой базы (ADR-0033): говорит, из какой копии, и закрывается «Понятно». */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PIXEL_5)
class RecoverySheetTest {

    @get:Rule(order = 0)
    val activity = ComponentActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC)
    private lateinit var systemZone: TimeZone
    private var seen = 0

    @Before
    fun useUtc() {
        systemZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    private fun show(recovery: DatabaseRecovery) {
        val viewModel = DiaryViewModel(FakeDiaryRepository(), FakeSelectedChildStore(), FakeDocumentStore(), clock)
        compose.setContent {
            BabyLogTheme(clock = clock, darkTheme = false) {
                DiaryScreen(viewModel, ThemeChoice(), onThemeChange = {}, recovery = recovery, onRecoverySeen = { seen++ })
            }
        }
    }

    @Test
    fun `restored diary names the time of the backup`() {
        show(DatabaseRecovery(at = clock.millis(), backupTime = Instant.parse("2026-10-18T21:40:00Z").toEpochMilli()))
        compose.onNodeWithText("Дневник восстановлен").assertIsDisplayed()
        compose.onNode(hasText("из копии, которую приложение сделало 18 октября 2026 в 21:40", substring = true)).assertIsDisplayed()
        compose.onNodeWithText("Понятно").performClick()
        compose.waitForIdle()
        assertEquals(1, seen)
    }

    @Test
    fun `without a backup the diary is new and import is offered`() {
        show(DatabaseRecovery(at = clock.millis(), backupTime = null))
        compose.onNodeWithText("Дневник начат заново").assertIsDisplayed()
        compose.onNodeWithText("Импорт из CSV").assertIsDisplayed()
    }
}
