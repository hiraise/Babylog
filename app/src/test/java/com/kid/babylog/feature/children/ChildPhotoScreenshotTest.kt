package com.kid.babylog.feature.children

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.ui.components.Avatar
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.testing.SCREENSHOT_OPTIONS
import com.kid.babylog.testing.testBitmap
import com.kid.babylog.testing.testPhoto
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Фото ребёнка (ADR-0022): аватар с фото в шапке и крупно, кнопка фото в листе ребёнка без фото и
 * с ним, окно кадрирования с кадром по центру и увеличенным — светлая и тёмная тема.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ChildPhotoScreenshotTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `photo avatar, photo button and crop area`() = captureRoboImage(
        "src/test/screenshots/child_photo.png",
        roborazziOptions = SCREENSHOT_OPTIONS,
    ) {
        val photo = testPhoto()
        val image = testBitmap(width = 400, height = 300).asImageBitmap()
        Row {
            for (dark in listOf(false, true)) {
                BabyLogTheme(clock = clock, darkTheme = dark) {
                    val spacing = BabyLogTheme.spacing
                    // Фон листа: на нём лежат кнопка фото и окно кадрирования
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.padding(spacing.l), verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(spacing.m), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(Sex.GIRL, photo)
                                PhotoButton(Sex.GIRL, photo = null, onClick = {})
                                PhotoButton(Sex.GIRL, photo, onClick = {})
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                                Box(Modifier.size(140.dp)) { CropArea(image, frame = { CropFrame() }, onFrameChange = {}) }
                                Box(Modifier.size(140.dp)) {
                                    CropArea(image, frame = { CropFrame(zoom = 2f, centerX = 0.4f, centerY = 0.4f) }, onFrameChange = {})
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
