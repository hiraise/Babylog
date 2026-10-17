package com.kid.babylog

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
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
 * Значок приложения (`mipmap-anydpi-v26/ic_launcher.xml`) на светлом и тёмном фоне: передний слой
 * на `ic_launcher_background` в масках лаунчера (круг, сквиркл, скруглённый квадрат) и тематическая
 * иконка Android 13+, где тот же слой закрашен одним цветом. Compose не рисует `adaptive-icon`,
 * а маску выбирает лаунчер, поэтому слои собираются здесь.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class LauncherIconScreenshotTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `launcher icon`() = captureRoboImage("src/test/screenshots/launcher_icon.png", roborazziOptions = SCREENSHOT_OPTIONS) {
        Column {
            for (dark in listOf(false, true)) {
                BabyLogTheme(clock = clock, darkTheme = dark) {
                    val spacing = BabyLogTheme.spacing
                    val colors = MaterialTheme.colorScheme
                    Surface(color = colors.background) {
                        Row(
                            modifier = Modifier.padding(spacing.l),
                            horizontalArrangement = Arrangement.spacedBy(spacing.l),
                        ) {
                            val background = colorResource(R.color.ic_launcher_background)
                            LauncherIcon(CircleShape, background)
                            LauncherIcon(RoundedCornerShape(percent = 32), background)
                            LauncherIcon(RoundedCornerShape(percent = 18), background)
                            if (dark) {
                                LauncherIcon(CircleShape, colors.surfaceContainerHigh, tint = colors.primary)
                            } else {
                                LauncherIcon(CircleShape, colors.primaryContainer, tint = colors.onPrimaryContainer)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Видимая часть адаптивной иконки — середина 72 из 108 dp холста слоя. */
@Composable
private fun LauncherIcon(shape: Shape, background: Color, tint: Color? = null) {
    Box(
        modifier = Modifier.size(72.dp).clip(shape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(108.dp),
            colorFilter = tint?.let(ColorFilter::tint),
        )
    }
}
