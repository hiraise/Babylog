package com.kid.babylog.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.kid.babylog.core.model.Sex
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
 * Аватары без фото по полу ([avatarIcon]): пол не указан, мальчик, девочка — в размере шапки и крупно,
 * светлая и тёмная тема. На снимке главного экрана аватар слишком мал для порога сверки, поэтому
 * иконки сверяются отдельно.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class AvatarScreenshotTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `avatars by sex`() = captureRoboImage("src/test/screenshots/avatars.png", roborazziOptions = SCREENSHOT_OPTIONS) {
        Row {
            for (dark in listOf(false, true)) {
                BabyLogTheme(clock = clock, darkTheme = dark) {
                    val spacing = BabyLogTheme.spacing
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.padding(spacing.l), verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                            for (size in listOf(40.dp, 96.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(spacing.m),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    listOf(null, Sex.BOY, Sex.GIRL).forEach { Avatar(it, size = size) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
