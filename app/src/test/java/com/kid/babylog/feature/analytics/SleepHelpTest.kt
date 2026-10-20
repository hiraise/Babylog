package com.kid.babylog.feature.analytics

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.testing.ComponentActivityRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private const val TAP_INSET = 10f

/** Источники в справке «?» о сне (ADR-0036) открываются по нажатию, а без браузера приложение не падает. */
@RunWith(RobolectricTestRunner::class)
class SleepHelpTest {

    @get:Rule(order = 0)
    val activity = ComponentActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-18T08:20:00Z"), ZoneOffset.UTC)

    private fun show(handler: UriHandler) = compose.setContent {
        CompositionLocalProvider(LocalUriHandler provides handler) {
            BabyLogTheme(clock = clock, darkTheme = false) { Column { SleepHelp() } }
        }
    }

    /** Касание в начале первой строки: длинный источник переносится, и центр блока бывает мимо текста. */
    private fun SemanticsNodeInteraction.tapStart() = performTouchInput { click(Offset(TAP_INSET, TAP_INSET)) }

    @Test
    fun `source opens its doi link`() {
        val opened = mutableListOf<String>()
        show(object : UriHandler {
            override fun openUri(uri: String) {
                opened += uri
            }
        })
        compose.onNodeWithText("Pediatrics, 2004", substring = true).tapStart()
        compose.onNodeWithText("Sleep Medicine, 2010", substring = true).tapStart()
        assertEquals(SLEEP_SOURCES.map { it.second }, opened)
    }

    @Test
    fun `no browser — nothing happens`() {
        show(object : UriHandler {
            override fun openUri(uri: String) = throw IllegalArgumentException("Can't open $uri.")
        })
        compose.onNodeWithText("Pediatrics, 2004", substring = true).tapStart()
        compose.onNodeWithText("Сон, который переходит", substring = true).assertExists()
    }
}
