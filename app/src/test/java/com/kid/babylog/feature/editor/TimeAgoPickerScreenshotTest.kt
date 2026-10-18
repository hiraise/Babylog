package com.kid.babylog.feature.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.testing.ComponentActivityRule
import com.kid.babylog.testing.SCREENSHOT_OPTIONS
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * «Когда» на ширине листа телефона: чип «Другое» стал полем, набрано 90 — «−90 мин», под чипами время
 * и «1 ч 30 мин назад». Поле в том же ряду, что и чипы, и той же высоты. Светлая и тёмная тема.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class TimeAgoPickerScreenshotTest {

    @get:Rule(order = 0)
    val activity = ComponentActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `custom minutes in the chip row`() {
        compose.setContent {
            Row {
                for (dark in listOf(false, true)) {
                    BabyLogTheme(clock = clock, darkTheme = dark) {
                        // Фон — как у листа (AppSheet)
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            var minutesAgo by remember { mutableIntStateOf(0) }
                            Box(Modifier.width(360.dp).padding(BabyLogTheme.spacing.l)) {
                                TimeAgoPicker(minutesAgo, { minutesAgo = it })
                            }
                        }
                    }
                }
            }
        }
        for (i in 0..1) {
            compose.onAllNodesWithText("Другое")[0].performClick()
            compose.onAllNodesWithContentDescription("Минут назад")[i].performTextInput("90")
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/time_ago_custom.png", roborazziOptions = SCREENSHOT_OPTIONS)
    }
}
