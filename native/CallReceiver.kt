package com.example.fakecall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val name = CallScheduler.pickName(c)
        CallScheduler.clear(c)
        CallScheduler.showCall(c, name)
        if (MainActivity.visible) {
            c.startActivity(CallScheduler.launchIntent(c, name))
        }
    }
}
