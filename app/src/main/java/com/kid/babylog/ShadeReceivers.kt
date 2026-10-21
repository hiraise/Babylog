package com.kid.babylog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Кнопки уведомления в шторке и его перерисовка по будильнику (ADR-0042). В манифесте не экспортирован:
 * чужое приложение не может начать или закончить таймер.
 */
class ShadeActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        (context.applicationContext as App).container.shade.handle(intent) { pending.finish() }
    }
}

/**
 * После перезагрузки телефона и обновления приложения система убирает уведомления; это возвращает
 * уведомление в шторке, если оно включено (ADR-0042). Слушает только системные события, которые
 * другие приложения послать не могут.
 */
class ShadeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        (context.applicationContext as App).container.shade.refresh { pending.finish() }
    }
}
