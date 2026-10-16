package com.kid.babylog.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kid.babylog.core.ui.theme.BabyLogTheme
import java.time.Clock

/**
 * Обёртка для `@Preview` компонентов: одно и то же содержимое в светлой и тёмной теме
 * [BabyLogTheme] рядом. Фон — `background`, отступ от края — `spacing.l`, шаг между
 * элементами — `spacing.m`, как на экране. Часы системные: превью не зависит от `AppContainer`.
 *
 * Только для превью; ширину задавай в `@Preview(widthDp = 720)` — по 360 dp на тему, как у телефона.
 */
@Composable
internal fun LightDarkPreview(content: @Composable ColumnScope.() -> Unit) {
    Row {
        for (dark in listOf(false, true)) {
            BabyLogTheme(clock = Clock.systemDefaultZone(), darkTheme = dark) {
                Surface(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.padding(BabyLogTheme.spacing.l),
                        verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.m),
                        content = content,
                    )
                }
            }
        }
    }
}
