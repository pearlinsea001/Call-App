package com.example.fakecall

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class VolumeKeyService : AccessibilityService() {
    private var up = false
    private var down = false
    private var combo = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onKeyEvent(ev: KeyEvent): Boolean {
        val code = ev.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        val isDown = ev.action == KeyEvent.ACTION_DOWN
        if (code == KeyEvent.KEYCODE_VOLUME_UP) up = isDown else down = isDown

        if (!CallScheduler.prefs(this).getBoolean("volumeTrigger", true)) return false

        if (up && down) {
            if (!combo) {
                combo = true
                CallScheduler.arm(this)
            }
            return true
        }
        if (combo) {
            if (!up && !down) combo = false
            return true
        }
        return false
    }
}
