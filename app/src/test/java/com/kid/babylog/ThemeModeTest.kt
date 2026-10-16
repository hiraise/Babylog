package com.kid.babylog

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import androidx.compose.ui.graphics.toArgb
import androidx.test.platform.app.InstrumentationRegistry
import com.kid.babylog.core.data.PrefsThemeModeStore
import com.kid.babylog.core.data.ThemeMode
import com.kid.babylog.core.data.isDark
import com.kid.babylog.core.ui.theme.DarkColors
import com.kid.babylog.core.ui.theme.LightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Тема, выбранная в приложении, перекрывает системную и меняется без пересоздания окна (ADR-0017). */
@RunWith(RobolectricTestRunner::class)
class ThemeModeTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Настройка из контейнера приложения — та же, что читает `MainActivity`. */
    private val store get() = (context.applicationContext as App).container.themeMode

    private fun MainActivity.windowColor() = (window.decorView.background as ColorDrawable).color

    @Test
    fun `chosen theme overrides the system one`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
    }

    @Test
    fun `window opens in the chosen theme`() {
        store.select(ThemeMode.DARK)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals(DarkColors.background.toArgb(), activity.windowColor())
    }

    @Test
    @Config(qualifiers = "night")
    fun `system theme follows the system`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals(DarkColors.background.toArgb(), activity.windowColor())
    }

    @Test
    fun `switching theme repaints the same window instead of recreating it`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertEquals(LightColors.background.toArgb(), activity.windowColor())
        store.select(ThemeMode.DARK)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertSame(activity, controller.get())
        assertFalse(activity.isFinishing)
        assertEquals(DarkColors.background.toArgb(), activity.windowColor())
    }

    @Test
    fun `activity handles theme changes itself`() {
        // Без configChanges="uiMode" система пересоздаёт окно при смене темы — экран моргает
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertTrue(info.configChanges and ActivityInfo.CONFIG_UI_MODE != 0)
    }

    @Test
    fun `choice survives restart`() {
        PrefsThemeModeStore(context).select(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, PrefsThemeModeStore(context).mode.value)
    }
}
