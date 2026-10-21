package com.kid.babylog.feature.shade

import android.Manifest
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kid.babylog.R
import java.time.Clock

/**
 * Показывает уведомление в шторке (ADR-0042) или убирает его. Одно уведомление на приложение — для
 * выбранного ребёнка; оно постоянное (`ongoing`), без звука и вибрации и видно на экране блокировки.
 */
class ShadeNotifier(
    private val context: Context,
    receiver: Class<out BroadcastReceiver>,
    private val clock: Clock,
) {
    private val manager = NotificationManagerCompat.from(context)
    private val intents = ShadeIntents(context, receiver)
    private val views = ShadeViews(context, intents, clock)

    /** [content] = null — уведомление выключено или ребёнка нет: убрать. */
    fun show(content: ShadeContent?) {
        if (content == null) {
            manager.cancel(NOTIFICATION_ID)
            scheduleRefresh(null)
            return
        }
        manager.createNotificationChannel(channel())
        // Без разрешения (Android 13+) система уведомление не покажет; меню подскажет, где его дать
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        manager.notify(NOTIFICATION_ID, build(content))
        scheduleRefresh(content.refreshAt)
    }

    private fun build(content: ShadeContent) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(
            when {
                content.crying != null -> R.drawable.ic_crying
                content.sleep != null -> R.drawable.ic_sleep
                content.feeding != null -> R.drawable.ic_feeding
                else -> R.drawable.ic_notification
            }
        )
        // Значок в шапке — цвет значка приложения в любой теме: шапку перекрашивает система, а цвет
        // задаётся один раз, при показе, и после смены темы телефона остался бы от прежней
        .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
        // Заголовок и текст — для озвучки и устройств, которые не показывают своё оформление
        .setContentTitle(listOfNotNull(content.sleep, content.feeding, content.crying).joinToString { it.title }.ifEmpty { content.idleTitle })
        .setContentText(listOfNotNull(content.sleep, content.feeding, content.crying).joinToString { it.text }.ifEmpty { content.idleText.orEmpty() })
        .setSubText(content.childName)
        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        .setCustomContentView(views.collapsed(content))
        .setCustomBigContentView(views.expanded(content))
        .setContentIntent(intents.openApp())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setShowWhen(false)
        .setLocalOnly(true)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setCategory(if (content.isIdle) NotificationCompat.CATEGORY_STATUS else NotificationCompat.CATEGORY_STOPWATCH)
        .build()

    /**
     * «Отменить» и «Отметить» исчезают сами: в [at] уведомление перерисует кнопка `REFRESH`. Будильник
     * неточный и не будит телефон — разрешений не нужно, а с выключенным экраном строку всё равно не видно.
     */
    private fun scheduleRefresh(at: Long?) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val refresh = intents.action(ShadeAction.REFRESH, childId = 0)
        if (at == null) {
            alarms.cancel(refresh)
            return
        }
        val delay = (at - clock.millis()).coerceAtLeast(0) + REFRESH_SLACK_MS
        alarms.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + delay, refresh)
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "shade"
        private const val REFRESH_SLACK_MS = 500L

        /**
         * Канал «Кнопки в шторке»: обычная важность, чтобы уведомление было на экране блокировки (тихие
         * многие телефоны там прячут), но без звука и вибрации.
         */
        private fun channel() = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName("Кнопки в шторке")
            .setDescription("Сон, кормление и плач из шторки и с экрана блокировки, без открытия приложения")
            .setSound(null, null)
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()

        /** Уведомления приложения или этот канал выключены в настройках телефона, или нет разрешения. */
        fun isBlocked(context: Context): Boolean {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return true
            return manager.getNotificationChannelCompat(CHANNEL_ID)?.importance == NotificationManagerCompat.IMPORTANCE_NONE
        }
    }
}
