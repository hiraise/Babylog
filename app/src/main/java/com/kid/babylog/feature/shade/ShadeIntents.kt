package com.kid.babylog.feature.shade

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Нажатие кнопки уведомления: что сделать, с каким ребёнком и с какой записью. */
data class ShadePress(val action: ShadeAction, val childId: Long, val eventId: Long)

/**
 * Намерения уведомления в шторке (ADR-0042). Кнопки идут в [receiver] — он не экспортирован, поэтому
 * нажать их может только система по нашему [PendingIntent]; «Отметить» и касание уведомления открывают
 * приложение.
 */
class ShadeIntents(private val context: Context, private val receiver: Class<out BroadcastReceiver>) {

    fun action(action: ShadeAction, childId: Long, eventId: Long = 0): PendingIntent {
        val intent = Intent(context, receiver)
            .setAction(PREFIX + action.name)
            .putExtra(EXTRA_CHILD, childId)
            .putExtra(EXTRA_EVENT, eventId)
        return PendingIntent.getBroadcast(context, action.ordinal, intent, FLAGS)
    }

    /** Открыть приложение; [cryingId] — сразу с листом «Плач закончился» для этого плача. */
    fun openApp(cryingId: Long? = null): PendingIntent {
        val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(Intent.ACTION_MAIN).setPackage(context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (cryingId != null) intent.putExtra(EXTRA_CRYING_DETAILS, cryingId)
        return PendingIntent.getActivity(context, if (cryingId != null) 1 else 0, intent, FLAGS)
    }

    companion object {
        private const val PREFIX = "babylog.shade."
        private const val EXTRA_CHILD = "child_id"
        private const val EXTRA_EVENT = "event_id"
        private const val EXTRA_CRYING_DETAILS = "babylog.shade.crying_details"
        private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        fun parse(intent: Intent): ShadePress? {
            val name = intent.action?.removePrefix(PREFIX) ?: return null
            val action = ShadeAction.entries.firstOrNull { it.name == name } ?: return null
            return ShadePress(action, intent.getLongExtra(EXTRA_CHILD, 0), intent.getLongExtra(EXTRA_EVENT, 0))
        }

        /** Плач, о котором «Отметить» просит открыть лист с причинами; null — приложение открыли не так. */
        fun cryingDetails(intent: Intent?): Long? =
            intent?.takeIf { it.hasExtra(EXTRA_CRYING_DETAILS) }?.getLongExtra(EXTRA_CRYING_DETAILS, 0)

        fun clearCryingDetails(intent: Intent) = intent.removeExtra(EXTRA_CRYING_DETAILS)
    }
}
