package com.kid.babylog.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

/**
 * Плитки быстрых записей на ширине телефона 360 dp: подгузник меняли 25 мин назад; 3 ч 20 мин назад —
 * подпись выделена; столько же, но малыш спит — не выделена; не меняли — без подписи. Под весом и
 * ростом — дата замера: вчера и две недели назад, сегодня и в прошлом году (в две строки), не мерили.
 * Светлая и тёмная тема. Плитки ряда одной высоты при любой подписи.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class QuickActionsScreenshotTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private val now = clock.millis()
    private val minute = 60_000L
    private val day = 24 * 60 * minute

    @Test
    fun `diaper caption, overdue and asleep, measurement dates`() = captureRoboImage(
        "src/test/screenshots/quick_actions.png",
        roborazziOptions = SCREENSHOT_OPTIONS,
    ) {
        Row {
            for (dark in listOf(false, true)) {
                BabyLogTheme(clock = clock, darkTheme = dark) {
                    val spacing = BabyLogTheme.spacing
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Column(
                            Modifier.width(360.dp).padding(spacing.l),
                            verticalArrangement = Arrangement.spacedBy(spacing.m),
                        ) {
                            QuickActions(now - 25 * minute, lastWeight = now - day, lastHeight = now - 14 * day, sleeping = false, now = now, onAdd = {})
                            QuickActions(now - 200 * minute, lastWeight = now, lastHeight = now - 400 * day, sleeping = false, now = now, onAdd = {})
                            QuickActions(now - 200 * minute, lastWeight = null, lastHeight = null, sleeping = true, now = now, onAdd = {})
                            QuickActions(lastDiaper = null, lastWeight = null, lastHeight = null, sleeping = false, now = now, onAdd = {})
                        }
                    }
                }
            }
        }
    }
}
