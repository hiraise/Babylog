package com.kid.babylog.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.kid.babylog.R
import com.kid.babylog.core.model.Milk
import com.kid.babylog.core.ui.components.AppIcon
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
 * Бутылочка (ADR-0025): своя иконка в 24 и 48 dp (ADR-0014); поля листа — объём не введён;
 * 120 мл, смесь; 600 мл — ошибка. Светлая и тёмная тема, на фоне листа.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class BottleFieldsScreenshotTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `bottle icon and fields`() = captureRoboImage("src/test/screenshots/bottle_fields.png", roborazziOptions = SCREENSHOT_OPTIONS) {
        Row {
            for (dark in listOf(false, true)) {
                BabyLogTheme(clock = clock, darkTheme = dark) {
                    val spacing = BabyLogTheme.spacing
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.width(360.dp).padding(spacing.l), verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                                AppIcon(R.drawable.ic_bottle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                AppIcon(R.drawable.ic_bottle, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            BottleFields(volume = "", onVolume = {}, milk = null, onMilk = {})
                            BottleFields(volume = "120", onVolume = {}, milk = Milk.FORMULA, onMilk = {})
                            BottleFields(volume = "600", onVolume = {}, milk = null, onMilk = {})
                        }
                    }
                }
            }
        }
    }
}
