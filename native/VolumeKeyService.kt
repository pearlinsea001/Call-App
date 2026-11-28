package com.example.fakecall

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class VolumeKeyService : AccessibilityService() {
    private var up = false
    private var down = false
    private var upAt = 0L
    private var downAt = 0L
    private var combo = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        CallScheduler.updateScreenOff(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onKeyEvent(ev: KeyEvent): Boolean {
        val code = ev.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        val isDown = ev.action == KeyEvent.ACTION_DOWN

        // Key releases are ALWAYS passed to the system so no key ever stays "pressed".
        if (!isDown) {
            if (code == KeyEvent.KEYCODE_VOLUME_UP) up = false else down = false
            if (!up && !down) combo = false
            return false
        }

        if (ev.repeatCount == 0) {
            if (code == KeyEvent.KEYCODE_VOLUME_UP) { up = true; upAt = ev.eventTime }
            else { down = true; downAt = ev.eventTime }
        }

        if (!CallScheduler.prefs(this).getBoolean("volumeTrigger", true)) return false

        val together = up && down && Math.abs(upAt - downAt) < 1500
        if (together) {
            if (!combo) {
                combo = true
                CallScheduler.arm(this)
            }
            return true
        }
        // swallow auto-repeat of the keys while the combo is held
        if (combo) return true
        return false
    }
}
