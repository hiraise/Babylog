package com.kid.babylog.feature.shade

import android.content.Context
import android.content.res.Configuration
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.kid.babylog.ShadeActionReceiver
import com.kid.babylog.core.model.Breast
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
 * Разметка уведомления в шторке (ADR-0042) в светлой и тёмной теме системы. Карточку уведомления
 * и шапку рисует система — здесь только наше содержимое на похожем фоне.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ShadeScreenshotTest {

    private val now = Instant.parse("2026-10-21T10:00:00Z").toEpochMilli()
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)

    private val idle = ShadeContent(
        childId = 1,
        childName = "Маша",
        idleTitle = "Не спит с 09:20",
        idleText = "Кормление в 08:00, правая",
        idleShort = "Не спит" to "с 09:20",
        nextBreast = Breast.LEFT,
        cryingDone = ShadeRow("Плакала 6 мин", "Отметить", 8),
    )

    private val running = idle.copy(
        feeding = ShadeTimer("Кормление", "левая с 09:48", now - 12 * 60_000, ShadeLink.SwitchBreast),
        crying = ShadeTimer("Плач", "с 09:57", now - 3 * 60_000),
        cryingDone = null,
    )

    @Test
    fun `shade notification`() = captureRoboImage("src/test/screenshots/shade_notification.png", roborazziOptions = SCREENSHOT_OPTIONS) {
        val base = LocalContext.current
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (night in listOf(false, true)) {
                val context = base.withNight(night)
                val views = ShadeViews(context, ShadeIntents(context, ShadeActionReceiver::class.java), clock)
                for (remote in listOf(views.collapsed(idle), views.expanded(idle), views.collapsed(running), views.expanded(running))) {
                    Card(context, remote, night)
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Card(context: Context, remote: RemoteViews, night: Boolean) {
        // Похожие на фон карточки уведомления Android 13 в светлой и тёмной теме
        val background = if (night) Color(0xFF2B2C31) else Color(0xFFF6F2F9)
        AndroidView(
            factory = { FrameLayout(context).apply { addView(remote.apply(context, this)) } },
            modifier = Modifier
                .fillMaxWidth()
                .background(background)
                .padding(16.dp),
        )
    }

    private fun Context.withNight(night: Boolean): Context = createConfigurationContext(
        Configuration(resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        }
    )
}
