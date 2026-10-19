package com.kid.babylog.feature.diary

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.kid.babylog.core.data.DarkPalette
import com.kid.babylog.core.data.ThemeChoice
import com.kid.babylog.core.data.ThemeMode
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.testing.SCREENSHOT_OPTIONS
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Лист «Тема» на фоне листа (ADR-0032): в светлой теме — с подсказкой, что палитры видны в тёмной,
 * в тёмной — с выбранным «Ночником» в его же цветах.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ThemeSheetScreenshotTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `theme sheet, light`() = capture("theme_sheet_light", ThemeChoice(), dark = false)

    @Test
    fun `theme sheet, dark`() =
        capture("theme_sheet_dark", ThemeChoice(mode = ThemeMode.DARK, darkPalette = DarkPalette.NIGHTLIGHT), dark = true)

    private fun capture(name: String, theme: ThemeChoice, dark: Boolean) =
        captureRoboImage("src/test/screenshots/$name.png", roborazziOptions = SCREENSHOT_OPTIONS) {
            BabyLogTheme(clock = clock, darkTheme = dark, darkPalette = theme.darkPalette) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Box(Modifier.width(393.dp).padding(vertical = BabyLogTheme.spacing.l)) {
                        ThemeOptions(theme, onSelect = {})
                    }
                }
            }
        }
}
