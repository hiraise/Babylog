package com.kid.babylog.feature.editor

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
import com.kid.babylog.core.ui.photo.toJpeg
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.testing.SCREENSHOT_OPTIONS
import com.kid.babylog.testing.testBitmap
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Фото в форме подгузника (ADR-0024): без фото — только «добавить»; два фото и «добавить»; шесть
 * фото — «добавить» больше нет. Светлая и тёмная тема, на фоне листа. Обработку фото (`processing`)
 * не снимаем: бесконечная анимация индикатора не даёт Roborazzi дождаться простоя, тест виснет.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class PhotoStripScreenshotTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `photo strip states`() = captureRoboImage("src/test/screenshots/diaper_photos.png", roborazziOptions = SCREENSHOT_OPTIONS) {
        val landscape = testBitmap(width = 400, height = 300).toJpeg(85)
        val portrait = testBitmap(width = 300, height = 400).toJpeg(85)
        val two = listOf(FormPhoto.Saved(1), FormPhoto.Saved(2))
        val six = (1L..6L).map { FormPhoto.Saved(it) }
        val jpegOf = { photo: FormPhoto -> if ((photo as FormPhoto.Saved).id % 2 == 0L) portrait else landscape }
        Row {
            for (dark in listOf(false, true)) {
                BabyLogTheme(clock = clock, darkTheme = dark) {
                    val spacing = BabyLogTheme.spacing
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.width(360.dp).padding(spacing.l), verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                            PhotoStrip(emptyList(), jpegOf, canAdd = true, processing = false, onAdd = {}, onOpen = {})
                            PhotoStrip(two, jpegOf, canAdd = true, processing = false, onAdd = {}, onOpen = {})
                            PhotoStrip(six, jpegOf, canAdd = false, processing = false, onAdd = {}, onOpen = {})
                        }
                    }
                }
            }
        }
    }
}
