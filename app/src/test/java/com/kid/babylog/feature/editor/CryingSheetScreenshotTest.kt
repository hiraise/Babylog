package com.kid.babylog.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.ui.theme.BabyLogTheme
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
 * Лист «Плач закончился» (ADR-0030) на фоне листа: «Что было до» с «обычно …» у долгого
 * бодрствования, выбранные причины — стандартная и своя метка, — и «что помогло».
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class CryingSheetScreenshotTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-15T18:00:00Z"), ZoneOffset.UTC)
    private val minute = 60_000L
    private lateinit var systemZone: TimeZone

    @Before
    fun fixTimeZone() {
        systemZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    @Test
    fun `crying done, light`() = capture("crying_done_light", dark = false)

    @Test
    fun `crying done, dark`() = capture("crying_done_dark", dark = true)

    private fun capture(name: String, dark: Boolean) =
        captureRoboImage("src/test/screenshots/$name.png", roborazziOptions = SCREENSHOT_OPTIONS) {
            BabyLogTheme(clock = clock, darkTheme = dark) {
                val spacing = BabyLogTheme.spacing
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.width(393.dp).padding(spacing.l), verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                        val crying = Crying(1, 1, at("2026-10-15T17:32:00Z"), end = at("2026-10-15T17:56:00Z"))
                        CryingDoneFields(
                            crying = crying,
                            // Начался на 5 минут раньше, чем нажали
                            start = crying.start - 5 * minute,
                            earlier = 5,
                            onEarlier = {},
                            context = CryingContext(
                                awakeMillis = 135 * minute,
                                usualAwakeMillis = 90 * minute,
                                sinceFeedingMillis = 70 * minute,
                                feeding = "левая, 20 мин",
                                feedingByBottle = false,
                                sinceDiaperMillis = 40 * minute,
                                diaper = DiaperKind.WET,
                            ),
                            choices = cryingChoices(emptyList()),
                            sex = Sex.GIRL,
                            reasons = listOf("колики", "срыгивание"),
                            onReasons = {},
                            helped = listOf("укачивание"),
                            onHelped = {},
                            note = "",
                            onNote = {},
                        )
                    }
                }
            }
        }

    private fun at(iso: String): Long = Instant.parse(iso).toEpochMilli()
}
