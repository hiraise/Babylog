package com.kid.babylog

import android.app.UiModeManager
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kid.babylog.core.data.ThemeChoice
import com.kid.babylog.core.data.ThemeMode
import com.kid.babylog.core.data.isDark
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.feature.diary.DiaryScreen
import com.kid.babylog.feature.help.HELP_TOPICS
import com.kid.babylog.feature.help.HelpPointerCard
import com.kid.babylog.feature.help.helpBadge
import com.kid.babylog.feature.shade.ShadeIntents
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Единственный экран приложения: дневник, а вместо него, пока открыта, — справка ([HelpHost], ADR-0043).
 * Смена темы окно не пересоздаёт (ADR-0017): в манифесте `configChanges="uiMode"`, Compose
 * перерисовывается с новой темой за один кадр, а фон окна и системные панели перекрашивает [paintWindow].
 */
class MainActivity : ComponentActivity() {

    private val container get() = (application as App).container

    /** Плач, о котором «Отметить» в уведомлении в шторке просит открыть лист с причинами (ADR-0042). */
    private val cryingDetails = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        syncSystemNightMode(container.themeMode.choice.value.mode)
        // Пока экран открыт, уведомление в шторке следует за дневником и выбором ребёнка
        container.shade.start()
        // После поворота Activity создаётся с тем же намерением — лист уже открывали
        if (savedInstanceState == null) readShadeIntent(intent)
        setContent {
            val theme by container.themeMode.choice.collectAsStateWithLifecycle()
            val recovery by container.recoveryNotices.pending.collectAsStateWithLifecycle()
            val shadeEnabled by container.shadeStore.enabled.collectAsStateWithLifecycle()
            val cryingDetailsId by cryingDetails.collectAsStateWithLifecycle()
            val helpProgress by container.help.progress.collectAsStateWithLifecycle()
            val guideSessions: GuideSessions = viewModel(factory = container.guideSessionsFactory)
            // Справка (ADR-0043) — вместо дневника; дневник помнит раздел и листы, пока она открыта
            var help by rememberSaveable(stateSaver = HelpRouteSaver) { mutableStateOf<HelpRoute?>(null) }
            val diaryState = rememberSaveableStateHolder()
            val openHelp = {
                container.help.dismissPointer()
                help = HelpRoute.Topics
            }
            // «Отметить» в шторке открывает лист плача в дневнике — из справки возвращаемся туда
            LaunchedEffect(cryingDetailsId) {
                if (cryingDetailsId != null && help != null) {
                    guideSessions.close()
                    help = null
                }
            }
            // На API 31+ конфигурация уже несёт выбранную тему (setApplicationNightMode), до 31 — системную
            val dark = theme.mode.isDark(systemDark = isSystemInDarkTheme())
            BabyLogTheme(clock = container.clock, darkTheme = dark, darkPalette = theme.darkPalette) {
                val background = MaterialTheme.colorScheme.background.toArgb()
                SideEffect { paintWindow(background, dark) }
                when (val route = help) {
                    null -> diaryState.SaveableStateProvider("diary") {
                        DiaryScreen(
                            viewModel = viewModel(factory = container.diaryViewModelFactory),
                            theme = theme,
                            onThemeChange = ::changeTheme,
                            development = viewModel(factory = container.developmentViewModelFactory),
                            recovery = recovery,
                            onRecoverySeen = container.recoveryNotices::dismiss,
                            shadeEnabled = shadeEnabled,
                            onShadeChange = container.shadeStore::setEnabled,
                            cryingDetailsId = cryingDetailsId,
                            onCryingDetailsShown = { cryingDetails.value = null },
                            onOpenHelp = openHelp,
                            helpBadge = helpBadge(HELP_TOPICS, helpProgress),
                            helpPointer = if (helpProgress.pointerDone) null else {
                                { HelpPointerCard(onOpen = openHelp, onDismiss = container.help::dismissPointer) }
                            },
                        )
                    }
                    else -> HelpHost(
                        route = route,
                        onRoute = { help = it },
                        topics = HELP_TOPICS,
                        progress = helpProgress,
                        onTopicOpened = { container.help.markOpened(it.id, container.appVersion) },
                        onTopicCompleted = { container.help.markCompleted(it.id) },
                        sessions = guideSessions,
                        theme = theme,
                        onThemeChange = ::changeTheme,
                        shadeEnabled = shadeEnabled,
                        onShadeChange = container.shadeStore::setEnabled,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readShadeIntent(intent)
    }

    /** Вернулись в приложение: в настройках телефона могли включить уведомления — показать его, если включено в меню. */
    override fun onResume() {
        super.onResume()
        container.shade.refresh()
    }

    private fun readShadeIntent(intent: Intent?) {
        val cryingId = ShadeIntents.cryingDetails(intent) ?: return
        intent?.let(ShadeIntents::clearCryingDetails)
        cryingDetails.value = cryingId
        container.shade.cryingDetailsOpened()
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
