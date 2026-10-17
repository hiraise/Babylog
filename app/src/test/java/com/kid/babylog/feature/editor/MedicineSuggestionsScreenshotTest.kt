package com.kid.babylog.feature.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
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

/** Поле названия лекарства с подсказками из прежних записей на фоне листа, светлая и тёмная тема. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class MedicineSuggestionsScreenshotTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `medicine suggestions`() =
        captureRoboImage("src/test/screenshots/medicine_suggestions.png", roborazziOptions = SCREENSHOT_OPTIONS) {
            val names = listOf("Витамин D", "Виферон", "Нурофен", "Вибуркол свечи детские гомеопатические")
            Column {
                for (dark in listOf(false, true)) {
                    BabyLogTheme(clock = clock, darkTheme = dark) {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Column(Modifier.fillMaxWidth().padding(BabyLogTheme.spacing.l)) {
                                MedicineNameField(TextFieldValue("Ви"), {}, medicineSuggestions(names, "Ви"))
                            }
                        }
                    }
                }
            }
        }
}
