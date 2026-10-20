package com.kid.babylog.feature.analytics

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

/** Справка «?» карточки «Сон» на фоне листа (ADR-0036). Обновить — `scripts/dev.sh goldens`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class SleepHelpScreenshotTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-18T08:20:00Z"), ZoneOffset.UTC)

    @Test
    fun `sleep help`() = captureRoboImage("src/test/screenshots/sleep_help.png", roborazziOptions = SCREENSHOT_OPTIONS) {
        BabyLogTheme(clock = clock, darkTheme = false) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                val spacing = BabyLogTheme.spacing
                Column(Modifier.width(393.dp).padding(spacing.l), verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                    SleepHelp()
                }
            }
        }
    }
}
