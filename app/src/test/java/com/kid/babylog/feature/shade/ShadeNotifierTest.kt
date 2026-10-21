package com.kid.babylog.feature.shade

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.test.core.app.ApplicationProvider
import com.kid.babylog.R
import com.kid.babylog.ShadeActionReceiver
import com.kid.babylog.core.model.Breast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Уведомление в шторке (ADR-0042) так, как его получит система: канал, флаги и разметка `RemoteViews`. */
@RunWith(RobolectricTestRunner::class)
class ShadeNotifierTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-10-21T10:00:00Z").toEpochMilli()
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifier = ShadeNotifier(context, ShadeActionReceiver::class.java, clock)
    private val views = ShadeViews(context, ShadeIntents(context, ShadeActionReceiver::class.java), clock)

    private val idle = ShadeContent(
        childId = 1,
        childName = "Маша",
        idleTitle = "Не спит с 09:20",
        idleText = "Кормление в 08:00, правая",
        idleShort = "Не спит" to "с 09:20",
        nextBreast = Breast.LEFT,
    )

    @Before
    fun grantPermission() = shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

    @Test
    fun `posts one ongoing silent notification visible on the lock screen`() {
        notifier.show(idle)
        val notification = shadowOf(manager).allNotifications.single()
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
        val channel = manager.getNotificationChannel(ShadeNotifier.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertNull(channel.sound)
    }

    @Test
    fun `switched off notification is removed`() {
        notifier.show(idle)
        notifier.show(null)
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test
    fun `idle shows the status and three start buttons, feeding with the next breast`() {
        val view = views.expanded(idle).apply(context, FrameLayout(context))
        assertVisible(view, R.id.idle)
        assertEquals("Не спит с 09:20", view.text(R.id.idle_title))
        assertGone(view, R.id.main_row, R.id.crying_row, R.id.stopped_row, R.id.done_row)
        assertVisible(view, R.id.sleep_start, R.id.feeding_start, R.id.crying_start)
        assertGone(view, R.id.sleep_stop, R.id.feeding_stop, R.id.crying_stop)
        assertEquals("Кормление\nлевая", view.text(R.id.feeding_start_label))
    }

    @Test
    fun `running feeding and crying fill their buttons and rows`() {
        val content = idle.copy(
            feeding = ShadeTimer("Кормление", "левая с 09:48", now - 12 * 60_000, ShadeLink.SwitchBreast),
            crying = ShadeTimer("Плач", "с 09:57", now - 3 * 60_000),
            stopped = ShadeRow("Сон: 1 ч 12 мин", "Отменить", 5),
        )
        val view = views.expanded(content).apply(context, FrameLayout(context))
        assertGone(view, R.id.idle, R.id.crying_link, R.id.done_row)
        assertEquals("Кормление", view.text(R.id.main_title))
        assertEquals("Сменить грудь", view.text(R.id.main_link))
        assertEquals("Плач", view.text(R.id.crying_title))
        assertEquals("Сон: 1 ч 12 мин", view.text(R.id.stopped_text))
        assertVisible(view, R.id.sleep_start, R.id.feeding_stop, R.id.crying_stop)
        assertGone(view, R.id.sleep_stop, R.id.feeding_start, R.id.crying_start)

        val collapsed = views.collapsed(content).apply(context, FrameLayout(context))
        assertEquals("Кормление", collapsed.text(R.id.collapsed_title))
        assertVisible(collapsed, R.id.collapsed_clock, R.id.c_feeding_stop, R.id.c_crying_stop)
        assertGone(collapsed, R.id.collapsed_text, R.id.c_feeding_start)
    }

    private fun View.text(@IdRes id: Int) = findViewById<TextView>(id).text.toString()

    private fun assertVisible(view: View, @IdRes vararg ids: Int) =
        ids.forEach { assertEquals(context.resources.getResourceEntryName(it), View.VISIBLE, view.findViewById<View>(it).visibility) }

    private fun assertGone(view: View, @IdRes vararg ids: Int) =
        ids.forEach { assertEquals(context.resources.getResourceEntryName(it), View.GONE, view.findViewById<View>(it).visibility) }
}
