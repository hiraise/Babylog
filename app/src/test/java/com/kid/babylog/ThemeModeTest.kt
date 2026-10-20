package com.kid.babylog

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kid.babylog.core.data.DarkPalette
import com.kid.babylog.core.data.PrefsThemeModeStore
import com.kid.babylog.core.data.ThemeChoice
import com.kid.babylog.core.data.ThemeMode
import com.kid.babylog.core.data.isDark
import com.kid.babylog.core.ui.theme.LightColors
import com.kid.babylog.core.ui.theme.darkColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тема, выбранная в приложении, перекрывает системную и меняется без пересоздания окна (ADR-0017),
 * как и палитра тёмной темы (ADR-0032).
 */
@RunWith(RobolectricTestRunner::class)
class ThemeModeTest {

    /**
     * Окно перекомпоновывает рекомпозер этого правила, и `waitForIdle` доводит смену темы до окна. Общий
     * диспетчер Compose ненадёжен: если тест до этого закончился с отложенной задачей главного потока,
     * Robolectric её выбрасывает, и диспетчер стоит до конца JVM — смена темы не дошла бы до окна.
     */
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Настройка из контейнера приложения — та же, что читает `MainActivity`. */
    private val store get() = (context.applicationContext as App).container.themeMode

    private fun MainActivity.windowColor() = (window.decorView.background as ColorDrawable).color

    private fun background(palette: DarkPalette) = darkColors(palette).background.toArgb()

    @Test
    fun `chosen theme overrides the system one`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
    }

    @Test
    fun `window opens in the chosen theme`() {
        store.select(ThemeChoice(mode = ThemeMode.DARK))
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals(background(DarkPalette.DEFAULT), activity.windowColor())
    }

    @Test
    fun `window opens in the chosen dark palette`() {
        store.select(ThemeChoice(mode = ThemeMode.DARK, darkPalette = DarkPalette.CLASSIC))
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals(background(DarkPalette.CLASSIC), activity.windowColor())
    }

    @Test
    @Config(qualifiers = "night")
    fun `system theme follows the system`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals(background(DarkPalette.DEFAULT), activity.windowColor())
    }

    @Test
    fun `switching theme repaints the same window instead of recreating it`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertEquals(LightColors.background.toArgb(), activity.windowColor())
        store.select(ThemeChoice(mode = ThemeMode.DARK))
        compose.waitForIdle()
        assertSame(activity, controller.get())
        assertFalse(activity.isFinishing)
        assertEquals(background(DarkPalette.DEFAULT), activity.windowColor())
    }

    @Test
    fun `switching dark palette repaints the same window`() {
        store.select(ThemeChoice(mode = ThemeMode.DARK))
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        store.select(ThemeChoice(mode = ThemeMode.DARK, darkPalette = DarkPalette.NIGHTLIGHT))
        compose.waitForIdle()
        assertSame(activity, controller.get())
        assertFalse(activity.isFinishing)
        assertEquals(background(DarkPalette.NIGHTLIGHT), activity.windowColor())
    }

    @Test
    fun `activity handles theme changes itself`() {
        // Без configChanges="uiMode" система пересоздаёт окно при смене темы — экран моргает
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertTrue(info.configChanges and ActivityInfo.CONFIG_UI_MODE != 0)
    }

    @Test
    fun `choice survives restart`() {
        val choice = ThemeChoice(mode = ThemeMode.DARK, darkPalette = DarkPalette.SAGE)
        PrefsThemeModeStore(context).select(choice)
        assertEquals(choice, PrefsThemeModeStore(context).choice.value)
    }
}
