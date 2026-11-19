package com.uriya.medreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.LocalDateTime

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Repo.init(ctx)
        Notifier.createChannels(ctx)
        val medId = intent.getLongExtra(Scheduler.EXTRA_MED, -1L)
        val date = intent.getStringExtra(Scheduler.EXTRA_DATE)
        val time = intent.getStringExtra(Scheduler.EXTRA_TIME)

        when (intent.action) {
            Scheduler.ACTION_FIRE -> {
                val now = LocalDateTime.now()
                val at = intent.getStringExtra(Scheduler.EXTRA_AT)
                    ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: now
                val from = at.minusMinutes(1)
                val to = now.plusMinutes(1)
                setOf(at.toLocalDate(), now.toLocalDate()).forEach { day ->
                    Repo.dosesFor(day, now)
                        .filter { !it.logged && !it.dateTime.isBefore(from) && !it.dateTime.isAfter(to) }
                        .forEach { Notifier.dose(ctx, it) }
                }
                Scheduler.reschedule(ctx)
            }

            Scheduler.ACTION_SNOOZE_FIRE -> {
                if (date == null || time == null) return
                val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return
                Repo.dosesFor(d)
                    .firstOrNull { it.med.id == medId && it.time == time && !it.logged }
                    ?.let { Notifier.dose(ctx, it) }
            }

            Scheduler.ACTION_TAKE -> if (date != null && time != null) {
                Repo.setStatus(ctx, medId, date, time, DoseStatus.TAKEN)
            }

            Scheduler.ACTION_SKIP -> if (date != null && time != null) {
                Repo.setStatus(ctx, medId, date, time, DoseStatus.SKIPPED)
            }

            Scheduler.ACTION_SNOOZE -> if (date != null && time != null) {
                Notifier.cancel(ctx, doseKey(medId, date, time))
                Scheduler.snooze(ctx, medId, date, time)
            }
        }
    }
}

/** Re-arms the alarm after reboot, app update or clock/time-zone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Repo.init(ctx)
        Notifier.createChannels(ctx)
        Scheduler.reschedule(ctx)
    }
}
