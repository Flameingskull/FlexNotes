package com.fitlens.companion.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.fitlens.companion.R
import com.fitlens.companion.data.AsNeeded
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.InjectionSites
import com.fitlens.companion.data.LogKind
import com.fitlens.companion.data.LogReminders
import com.fitlens.companion.data.MedExtras
import com.fitlens.companion.data.MedReminders
import com.fitlens.companion.data.MedSchedules
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.TodayPlan
import com.fitlens.companion.data.fmtNum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The home-screen widget (#220): today's doses still to take, each with **Taken**, the as-needed medications with
 * **Take** (the last amount again), and today's workout. Built from plain `RemoteViews`, black glass with a gold
 * hairline. It is drawn again after every change to the data ([Store.refresh] calls [refreshSoon]), when a button is
 * used, and at least every half hour so a new day shows. While Lock Medications is on it names no medication.
 */
object TodayWidgets {
    const val ACTION_TAKE = "com.fitlens.companion.WIDGET_TAKE"
    const val ACTION_PRN = "com.fitlens.companion.WIDGET_PRN"
    private const val EXTRA_AT = "com.fitlens.companion.WIDGET_AT"
    private const val EXTRA_DOSE = "com.fitlens.companion.WIDGET_DOSE"
    private const val EXTRA_MEDICATION = "com.fitlens.companion.WIDGET_MEDICATION"
    /** Rows that fit a 4 × 2 widget; the rest are counted under them. */
    private const val MAX_ROWS = 4
    private val HHMMSS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    private var app: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    /** Draws every placed widget again, a moment after the latest change so a burst of writes draws it once. */
    fun refreshSoon() {
        val context = app ?: return
        pending?.cancel()
        pending = scope.launch {
            delay(500)
            runCatching { Store.snapshot.value?.let { update(context, it) } }
        }
    }

    private fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, TodayWidget::class.java))

    /** Draws [ids] (every placed widget by default) from [snap]. */
    fun update(context: Context, snap: Snapshot, ids: IntArray = ids(context)) {
        if (ids.isEmpty()) return
        val views = render(context, snap)
        AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
    }

    /** One line on the widget: its text and what its button does, or no button. */
    private class Line(val text: String, val button: String?, val action: PendingIntent?)

    private fun render(context: Context, snap: Snapshot): RemoteViews {
        val res = context.resources
        val today = Dates.today()
        val device = Settings.current()
        val locked = device.medLock
        val plan = snap.medPlan
        val records = snap.medicationView.records
        val lines = ArrayList<Line>()
        // Doses still to take today, in time order. Taken and skipped ones leave the widget.
        MedSchedules.dueOn(plan, today)
            .filter { MedSchedules.takenRecord(records, it) == null && !plan.isSkipped(it.dose.id, it.due) }
            .sortedBy { it.due }
            .forEachIndexed { i, d ->
                val what = if (locked) res.getString(R.string.tw_dose_hidden)
                    else "${d.dose.medication} ${fmtNum(d.amount)} ${d.dose.unit}".trim()
                val take = Intent(context, TodayWidgetActions::class.java).setAction(ACTION_TAKE)
                    .putExtra(EXTRA_AT, d.due).putExtra(EXTRA_DOSE, d.dose.id)
                lines += Line(
                    res.getString(R.string.tw_dose_line, d.time, what),
                    res.getString(R.string.md_take),
                    PendingIntent.getBroadcast(context, 9100 + i, take, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                )
            }
        // As-needed medications (#233): Take repeats the last amount; the first one, or one past the day's limit,
        // opens Medications instead, where the amount is chosen or the limit confirmed.
        if (!locked) {
            AsNeeded.medications(plan, snap.medications.map { it.name }).forEachIndexed { i, info ->
                val state = AsNeeded.state(info, records, today)
                val count = state.maxPerDay?.let { res.getString(R.string.prn_count_of, state.today, it) }
                    ?: res.getString(R.string.prn_count, state.today)
                val action = if (state.last == null || state.atLimit) openApp(context, 9300 + i) { putExtra(MedReminders.EXTRA_OPEN_MEDICATIONS, true) }
                    else PendingIntent.getBroadcast(
                        context, 9300 + i,
                        Intent(context, TodayWidgetActions::class.java).setAction(ACTION_PRN).putExtra(EXTRA_MEDICATION, info.medication),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                // When it was last taken today, so a second tap is an informed one.
                val lastToday = state.last?.takeIf { it.date == today }?.time?.take(5)
                val text = if (lastToday != null) res.getString(R.string.tw_prn_line_last, info.medication, count, lastToday)
                    else res.getString(R.string.tw_prn_line, info.medication, count)
                lines += Line(text, res.getString(R.string.tw_take), action)
            }
        }

        val views = RemoteViews(context.packageName, R.layout.widget_today)
        val gold = Brand.Gold.toArgb()
        val ivory = Brand.Ivory.toArgb()
        val muted = Brand.Muted.toArgb()
        val date = Dates.day(Dates.today())
        views.setTextViewText(R.id.widget_title, res.getString(R.string.tw_title, date).uppercase(Locale.getDefault()))
        views.setTextColor(R.id.widget_title, gold)
        views.setOnClickPendingIntent(R.id.widget_title, openApp(context, 9000) { })

        views.removeAllViews(R.id.widget_doses)
        lines.take(MAX_ROWS).forEach { line ->
            val row = RemoteViews(context.packageName, R.layout.widget_row)
            row.setTextViewText(R.id.widget_row_text, line.text)
            row.setTextColor(R.id.widget_row_text, ivory)
            row.setTextViewText(R.id.widget_row_button, line.button.orEmpty())
            row.setTextColor(R.id.widget_row_button, gold)
            line.action?.let { row.setOnClickPendingIntent(R.id.widget_row_button, it) }
            views.addView(R.id.widget_doses, row)
        }
        val more = lines.size - MAX_ROWS
        val medsTap = openApp(context, 9001) { putExtra(MedReminders.EXTRA_OPEN_MEDICATIONS, true) }
        when {
            more > 0 -> views.setTextViewText(R.id.widget_more, res.getQuantityString(R.plurals.tw_more, more, more))
            lines.isEmpty() -> views.setTextViewText(
                R.id.widget_more,
                res.getString(if (plan.activations.isEmpty()) R.string.tw_no_medications else R.string.tw_all_done)
            )
            else -> views.setTextViewText(R.id.widget_more, "")
        }
        views.setViewVisibility(R.id.widget_more, if (more > 0 || lines.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE)
        views.setTextColor(R.id.widget_more, muted)
        views.setOnClickPendingIntent(R.id.widget_more, medsTap)

        // Today's workout: the day logged today, or the current workout's next day, with its training block (#218).
        val workout = TodayPlan.workout(snap, Settings.currentPortable(), today)
        val workoutText = when {
            workout == null -> res.getString(R.string.tw_no_workout)
            workout.logged -> res.getString(R.string.tw_workout_logged, workout.day.name, workout.routine.name)
            else -> listOfNotNull(
                res.getString(R.string.tw_workout_next, workout.day.name, workout.routine.name),
                workout.block?.let { blockStateText(res, it) }
            ).joinToString(" · ")
        }
        views.setTextViewText(R.id.widget_workout_label, res.getString(R.string.tw_workout_label).uppercase(Locale.getDefault()))
        views.setTextColor(R.id.widget_workout_label, gold)
        views.setTextViewText(R.id.widget_workout, workoutText)
        views.setTextColor(R.id.widget_workout, ivory)
        views.setOnClickPendingIntent(R.id.widget_workout, openApp(context, 9002) { putExtra(LogReminders.EXTRA_OPEN_LOG, LogKind.WORKOUT.key) })
        return views
    }

    private fun openApp(context: Context, code: Int, extras: Intent.() -> Unit): PendingIntent = PendingIntent.getActivity(
        context, code,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            extras()
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Says what the widget just logged and when ("Logged Metformin 500 mg at 08:05"); no name when medication names are locked. */
    private fun confirm(context: Context, what: String, time: String) {
        val text = if (Settings.current().medLock) context.getString(R.string.tw_logged_hidden, time)
            else context.getString(R.string.tw_logged, what, time)
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    }

    /** Taken on a scheduled dose, or Take on an as-needed medication, with no screen open. */
    internal suspend fun handle(context: Context, intent: Intent) {
        val snap = MedReminders.ensureLoaded() ?: return
        val records = snap.medicationView.records
        val today = Dates.today()
        val now = LocalTime.now().format(HHMMSS)
        when (intent.action) {
            ACTION_TAKE -> {
                val key = intent.getStringExtra(EXTRA_AT) ?: return
                val id = intent.getLongExtra(EXTRA_DOSE, -1L)
                val d = MedReminders.openAt(snap.medPlan, records, key).firstOrNull { it.dose.id == id }
                if (d != null) {
                    // An injected dose goes to the suggested next site (#224), as from the reminder.
                    val site = MedReminders.sitesFor(snap.medPlan, records, listOf(d)).first()
                    MedSchedules.take(d, if (d.date == today) now else d.time + ":00", site = site)
                    confirm(context, "${d.dose.medication} ${fmtNum(d.amount)} ${d.dose.unit}".trim(), if (d.date == today) now.take(5) else d.time)
                }
            }
            ACTION_PRN -> {
                val name = intent.getStringExtra(EXTRA_MEDICATION) ?: return
                val info = snap.medPlan.infoOf(name).takeIf { it.asNeeded } ?: return
                val state = AsNeeded.state(info, records, today)
                val last = state.last
                if (last != null && !state.atLimit) {
                    val site = if (info.injected) InjectionSites.suggest(records, name).key else null
                    MedExtras.takeAsNeeded(name, last.unit, today, now, last.value, site)
                    confirm(context, "$name ${fmtNum(last.value)} ${last.unit}".trim(), now.take(5))
                }
            }
        }
        // The write refreshed the data; draw from it straight away rather than after the debounce.
        Store.snapshot.value?.let { update(context, it) }
        MedReminders.arm(context)
    }
}

/** The widget itself (#220): Android asks it to draw when it's placed, resized, and every half hour. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(25_000) {
                    TodayWidgets.init(context)
                    MedReminders.ensureLoaded()?.let { TodayWidgets.update(context, it, appWidgetIds) }
                }
            } finally {
                result.finish()
            }
        }
    }
}

/** The widget's Taken and Take buttons (#220). Explicit intents only, so it isn't exported. */
class TodayWidgetActions : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TodayWidgets.ACTION_TAKE && intent.action != TodayWidgets.ACTION_PRN) return
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(25_000) { TodayWidgets.handle(context, intent) }
            } finally {
                result.finish()
            }
        }
    }
}
