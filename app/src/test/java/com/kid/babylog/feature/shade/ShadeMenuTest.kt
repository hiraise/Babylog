package com.kid.babylog.feature.shade

import android.app.NotificationManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.testing.ComponentActivityRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Пункт меню «Кнопки в шторке» (ADR-0042): переключает вся строка, выключенные уведомления видны в нём. */
@RunWith(RobolectricTestRunner::class)
class ShadeMenuTest {

    @get:Rule(order = 0)
    val activity = ComponentActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC)
    private var enabled by mutableStateOf(false)

    private fun showItem() = compose.setContent {
        BabyLogTheme(clock = clock, darkTheme = false) {
            ShadeMenuItem(rememberShadeToggle(enabled, onChange = { enabled = it }))
        }
    }

    @Test
    fun `the row switches the notification on and off`() {
        showItem()
        compose.onNodeWithText("Кнопки в шторке").performClick()
        assertTrue(enabled)
        compose.onNodeWithText("Кнопки в шторке").assertIsOn()
        compose.onNodeWithText("Кнопки в шторке").performClick()
        assertFalse(enabled)
        compose.onNodeWithText("Кнопки в шторке").assertIsOff()
    }

    @Test
    fun `notifications switched off in the phone settings are pointed out`() {
        val manager = ApplicationProvider.getApplicationContext<Context>().getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(false)
        enabled = true
        showItem()
        compose.onNodeWithText("Уведомления выключены в настройках телефона — открыть").assertExists()
    }
}
