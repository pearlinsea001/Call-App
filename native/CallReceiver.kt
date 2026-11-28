package com.example.fakecall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val name = CallScheduler.pickName(c)
        CallScheduler.clear(c)
        wakeScreen(c)
        CallScheduler.showCall(c, name)
        // Open the call screen directly (needs "Display over other apps" when started from background)
        try {
            c.startActivity(CallScheduler.launchIntent(c, name))
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen(c: Context) {
        try {
            val pm = c.getSystemService(PowerManager::class.java)
            pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "fakecall:wake"
            ).acquire(10_000)
        } catch (_: Exception) {
        }
    }
}
