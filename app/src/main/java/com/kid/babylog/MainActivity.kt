package com.kid.babylog

import android.app.UiModeManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kid.babylog.core.data.ThemeChoice
import com.kid.babylog.core.data.ThemeMode
import com.kid.babylog.core.data.isDark
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.feature.diary.DiaryScreen

/**
 * Единственный экран приложения. Смена темы окно не пересоздаёт (ADR-0017): в манифесте
 * `configChanges="uiMode"`, Compose перерисовывается с новой темой за один кадр, а фон окна
 * и системные панели перекрашивает [paintWindow].
 */
class MainActivity : ComponentActivity() {

    private val container get() = (application as App).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        syncSystemNightMode(container.themeMode.choice.value.mode)
        setContent {
            val theme by container.themeMode.choice.collectAsStateWithLifecycle()
            // На API 31+ конфигурация уже несёт выбранную тему (setApplicationNightMode), до 31 — системную
            val dark = theme.mode.isDark(systemDark = isSystemInDarkTheme())
            BabyLogTheme(clock = container.clock, darkTheme = dark, darkPalette = theme.darkPalette) {
                val background = MaterialTheme.colorScheme.background.toArgb()
                SideEffect { paintWindow(background, dark) }
                DiaryScreen(
                    viewModel = viewModel(factory = container.diaryViewModelFactory),
                    theme = theme,
                    onThemeChange = ::changeTheme,
                )
            }
        }
    }

    private fun changeTheme(choice: ThemeChoice) {
        container.themeMode.select(choice)
        syncSystemNightMode(choice.mode)
    }

    /**
     * Окно и системные панели — цвета фона темы, значки под него. Тема окна из ресурсов
     * (`values-night`) применяется только при создании окна, а пересоздавать его нельзя — моргнёт.
     */
    // statusBarColor и navigationBarColor устарели только для edge-to-edge (targetSdk 35+), у нас 34
    @Suppress("DEPRECATION")
    private fun paintWindow(background: Int, dark: Boolean) {
        window.setBackgroundDrawable(ColorDrawable(background))
        window.statusBarColor = background
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        bars.isAppearanceLightStatusBars = !dark
        // Значки навигационной панели под светлый фон — только с API 27, как в values-v27/themes.xml
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.navigationBarColor = background
            bars.isAppearanceLightNavigationBars = !dark
        }
    }

    /**
     * На API 31+ сообщаем выбор системе: тогда заставку при холодном запуске (её рисует система до
     * нашего окна) и тему окна из ресурсов она берёт в выбранной теме. Окно при этом не
     * пересоздаётся — смену конфигурации принимает сама Activity.
     */
    private fun syncSystemNightMode(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val night = when (mode) {
            ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
        }
        getSystemService(UiModeManager::class.java).setApplicationNightMode(night)
    }
}
