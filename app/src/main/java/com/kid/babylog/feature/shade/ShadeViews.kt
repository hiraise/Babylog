package com.kid.babylog.feature.shade

import android.app.PendingIntent
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.kid.babylog.R
import com.kid.babylog.core.ui.breastLabel
import java.time.Clock

/**
 * Содержимое уведомления в шторке (ADR-0042) — `layout/shade_expanded.xml` и `layout/shade_collapsed.xml`.
 * Три кнопки стоят на постоянных местах (сон, кормление, плач), их можно нажимать не глядя: у идущего
 * таймера кнопка залита и заканчивает его. Часы идущих таймеров ведёт система ([RemoteViews.setChronometer]).
 */
class ShadeViews(private val context: Context, private val intents: ShadeIntents, private val clock: Clock) {

    fun expanded(content: ShadeContent): RemoteViews = RemoteViews(context.packageName, R.layout.shade_expanded).apply {
        show(R.id.idle, content.isIdle)
        setTextViewText(R.id.idle_title, content.idleTitle)
        text(R.id.idle_text, content.idleText)
        timer(content, content.sleep ?: content.feeding, TimerIds(R.id.main_row, R.id.main_title, R.id.main_text, R.id.main_link, R.id.main_clock))
        timer(content, content.crying, TimerIds(R.id.crying_row, R.id.crying_title, R.id.crying_text, R.id.crying_link, R.id.crying_clock))
        row(content.stopped, R.id.stopped_row, R.id.stopped_text, R.id.stopped_button) {
            intents.action(ShadeAction.RESUME, content.childId, it.eventId)
        }
        row(content.cryingDone, R.id.done_row, R.id.done_text, R.id.done_button) { intents.openApp(cryingId = it.eventId) }
        buttons(content).forEach { button ->
            show(button.startId, !button.running)
            show(button.stopId, button.running)
            setTextViewText(button.startLabelId, button.startLabel)
            setTextViewText(button.stopLabelId, "Закончить")
            setContentDescription(button.startId, button.startDescription)
            setContentDescription(button.stopId, button.stopDescription)
            setOnClickPendingIntent(button.startId, button.start)
            setOnClickPendingIntent(button.stopId, button.stop)
        }
    }

    fun collapsed(content: ShadeContent): RemoteViews = RemoteViews(context.packageName, R.layout.shade_collapsed).apply {
        // Две короткие строки: рядом три кнопки, а на экране блокировки уведомление обычно свёрнуто
        val timer = content.sleep ?: content.feeding ?: content.crying
        setTextViewText(R.id.collapsed_title, timer?.title ?: content.idleShort.first)
        show(R.id.collapsed_clock, timer != null)
        if (timer != null) setChronometer(R.id.collapsed_clock, base(timer.start), null, true)
        text(R.id.collapsed_text, content.idleShort.second.takeIf { timer == null })
        buttons(content).forEach { button ->
            show(button.collapsedStartId, !button.running)
            show(button.collapsedStopId, button.running)
            setContentDescription(button.collapsedStartId, button.startDescription)
            setContentDescription(button.collapsedStopId, button.stopDescription)
            setOnClickPendingIntent(button.collapsedStartId, button.start)
            setOnClickPendingIntent(button.collapsedStopId, button.stop)
        }
    }

    private class TimerIds(val row: Int, val title: Int, val text: Int, val link: Int, val clock: Int)

    private fun RemoteViews.timer(content: ShadeContent, timer: ShadeTimer?, ids: TimerIds) {
        show(ids.row, timer != null)
        if (timer == null) return
        setTextViewText(ids.title, timer.title)
        setTextViewText(ids.text, timer.text)
        setChronometer(ids.clock, base(timer.start), null, true)
        val link = timer.link
        show(ids.link, link != null)
        if (link != null) {
            setTextViewText(ids.link, link.label)
            setOnClickPendingIntent(
                ids.link,
                when (link) {
                    ShadeLink.SwitchBreast -> intents.action(ShadeAction.SWITCH_BREAST, content.childId)
                    is ShadeLink.CancelCrying -> intents.action(ShadeAction.CANCEL_CRYING, content.childId, link.cryingId)
                },
            )
        }
    }

    private fun RemoteViews.row(row: ShadeRow?, rowId: Int, textId: Int, buttonId: Int, intent: (ShadeRow) -> PendingIntent) {
        show(rowId, row != null)
        if (row == null) return
        setTextViewText(textId, row.text)
        setTextViewText(buttonId, row.button)
        setOnClickPendingIntent(buttonId, intent(row))
    }

    private class Button(
        val running: Boolean,
        val startId: Int,
        val stopId: Int,
        val startLabelId: Int,
        val stopLabelId: Int,
        val collapsedStartId: Int,
        val collapsedStopId: Int,
        val startLabel: String,
        val startDescription: String,
        val stopDescription: String,
        val start: PendingIntent,
        val stop: PendingIntent,
    )

    private fun buttons(content: ShadeContent): List<Button> {
        val breast = breastLabel(content.nextBreast)
        return listOf(
            Button(
                running = content.sleep != null,
                startId = R.id.sleep_start, stopId = R.id.sleep_stop,
                startLabelId = R.id.sleep_start_label, stopLabelId = R.id.sleep_stop_label,
                collapsedStartId = R.id.c_sleep_start, collapsedStopId = R.id.c_sleep_stop,
                startLabel = "Сон", startDescription = "Начать сон", stopDescription = "Закончить сон",
                start = intents.action(ShadeAction.START_SLEEP, content.childId),
                stop = intents.action(ShadeAction.END_SLEEP, content.childId),
            ),
            Button(
                running = content.feeding != null,
                startId = R.id.feeding_start, stopId = R.id.feeding_stop,
                startLabelId = R.id.feeding_start_label, stopLabelId = R.id.feeding_stop_label,
                collapsedStartId = R.id.c_feeding_start, collapsedStopId = R.id.c_feeding_stop,
                // Грудь — на кнопке: она начнёт кормление сразу, без выбора
                startLabel = "Кормление\n$breast", startDescription = "Начать кормление, $breast грудь",
                stopDescription = "Закончить кормление",
                start = intents.action(ShadeAction.START_FEEDING, content.childId),
                stop = intents.action(ShadeAction.END_FEEDING, content.childId),
            ),
            Button(
                running = content.crying != null,
                startId = R.id.crying_start, stopId = R.id.crying_stop,
                startLabelId = R.id.crying_start_label, stopLabelId = R.id.crying_stop_label,
                collapsedStartId = R.id.c_crying_start, collapsedStopId = R.id.c_crying_stop,
                startLabel = "Плач", startDescription = "Начать плач", stopDescription = "Закончить плач",
                start = intents.action(ShadeAction.START_CRYING, content.childId),
                stop = intents.action(ShadeAction.END_CRYING, content.childId),
            ),
        )
    }

    /** Начало таймера в часах [SystemClock.elapsedRealtime], по которым идёт [android.widget.Chronometer]. */
    private fun base(start: Long): Long = SystemClock.elapsedRealtime() - (clock.millis() - start)

    private fun RemoteViews.show(id: Int, visible: Boolean) = setViewVisibility(id, if (visible) View.VISIBLE else View.GONE)

    private fun RemoteViews.text(id: Int, text: String?) {
        show(id, text != null)
        if (text != null) setTextViewText(id, text)
    }
}
