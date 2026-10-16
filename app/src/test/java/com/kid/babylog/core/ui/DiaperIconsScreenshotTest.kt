package com.kid.babylog.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.ChoiceChip
import com.kid.babylog.core.ui.components.ItemRow
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
 * Свои иконки вида подгузника (ADR-0014) в строке истории и в чипах листа, светлая и тёмная тема.
 * На снимке главного экрана запись подгузника ниже края, поэтому иконки сверяются отдельно.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class DiaperIconsScreenshotTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun `diaper kinds`() = captureRoboImage("src/test/screenshots/diaper_kinds.png", roborazziOptions = SCREENSHOT_OPTIONS) {
        Column {
            for (dark in listOf(false, true)) {
                BabyLogTheme(clock = clock, darkTheme = dark) {
                    val spacing = BabyLogTheme.spacing
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(spacing.l),
                            verticalArrangement = Arrangement.spacedBy(spacing.m),
                        ) {
                            DiaperKind.entries.forEach { kind ->
                                val event = Diaper(childId = 1, start = 0, kind = kind)
                                AppCard(Modifier.fillMaxWidth()) {
                                    ItemRow(icon = eventIcon(event), title = eventTitle(event, sex = null))
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                                DiaperKind.entries.forEach { kind ->
                                    ChoiceChip(
                                        selected = kind == DiaperKind.WET,
                                        onClick = {},
                                        label = diaperLabel(kind, sex = null),
                                        icon = diaperIcon(kind),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
