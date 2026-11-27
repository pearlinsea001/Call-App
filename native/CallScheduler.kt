package com.example.fakecall

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings

object CallScheduler {
    private const val NOTIF_ID = 1001

    fun prefs(c: Context) = c.getSharedPreferences("fakecall_native", Context.MODE_PRIVATE)

    fun sync(c: Context, m: Map<*, *>) {
        prefs(c).edit()
            .putString("names", m["names"] as? String ?: "Mom")
            .putString("selected", m["selected"] as? String ?: "")
            .putInt("delay", (m["delay"] as? Number)?.toInt() ?: 10)
            .putBoolean("ring", m["ring"] as? Boolean ?: true)
            .putBoolean("vibrate", m["vibrate"] as? Boolean ?: true)
            .putBoolean("volumeTrigger", m["volumeTrigger"] as? Boolean ?: true)
            .apply()
    }

    fun activeEndAt(c: Context): Long {
        val e = prefs(c).getLong("endAt", 0L)
        return if (e > System.currentTimeMillis()) e else 0L
    }

    private fun alarmIntent(c: Context): PendingIntent =
        PendingIntent.getBroadcast(
            c, 0, Intent(c, CallReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun arm(c: Context): Long {
        val existing = activeEndAt(c)
        if (existing > 0) return existing
        val at = System.currentTimeMillis() + prefs(c).getInt("delay", 10) * 1000L
        val show = PendingIntent.getActivity(
            c, 2, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val am = c.getSystemService(AlarmManager::class.java)
val pi = alarmIntent(c)
try {
    am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
} catch (e: SecurityException) {
    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
}
        prefs(c).edit().putLong("endAt", at).apply()
        buzz(c)
        return at
    }

    fun cancel(c: Context) {
        c.getSystemService(AlarmManager::class.java).cancel(alarmIntent(c))
        prefs(c).edit().putLong("endAt", 0L).apply()
    }

    fun clear(c: Context) {
        prefs(c).edit().putLong("endAt", 0L).apply()
    }

    private fun buzz(c: Context) {
        val v = c.getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") v.vibrate(80)
        }
    }

    fun pickName(c: Context): String {
        val p = prefs(c)
        val sel = p.getString("selected", "") ?: ""
        if (sel.isNotBlank()) return sel
        val names = (p.getString("names", "Mom") ?: "Mom").split("\n").filter { it.isNotBlank() }
        return if (names.isEmpty()) "Unknown" else names.random()
    }

    fun launchIntent(c: Context, name: String): Intent =
        Intent(c, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("fake_call_name", name)
        }

    fun showCall(c: Context, name: String) {
        val p = prefs(c)
        val ring = p.getBoolean("ring", true)
        val vib = p.getBoolean("vibrate", true)
        val chId = "call_r${if (ring) 1 else 0}v${if (vib) 1 else 0}"
        val tone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val pattern = longArrayOf(0, 800, 800, 800)
        val nm = c.getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(chId) == null) {
            val ch = NotificationChannel(chId, "Incoming fake call", NotificationManager.IMPORTANCE_HIGH)
            if (ring) {
                ch.setSound(
                    tone,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            } else {
                ch.setSound(null, null)
            }
            ch.enableVibration(vib)
            if (vib) ch.vibrationPattern = pattern
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }

        val pi = PendingIntent.getActivity(
            c, 1, launchIntent(c, name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, chId) else Notification.Builder(c)
        b.setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(name)
            .setContentText("Incoming call")
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
        if (Build.VERSION.SDK_INT >= 26) b.setTimeoutAfter(60_000)
        if (Build.VERSION.SDK_INT < 26) {
            @Suppress("DEPRECATION") b.setPriority(Notification.PRIORITY_MAX)
            if (ring) @Suppress("DEPRECATION") b.setSound(tone)
            if (vib) @Suppress("DEPRECATION") b.setVibrate(pattern)
        }
        val n = b.build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        nm.notify(NOTIF_ID, n)
    }

    fun stopAlert(c: Context) {
        c.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }

    fun a11yEnabled(c: Context): Boolean {
        val s = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return s.contains(c.packageName + "/")
    }
}
