package com.example.fakecall

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private var channel: MethodChannel? = null
    private var pendingName: String? = null
    private var up = false
    private var down = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
        pendingName?.let {
            channel?.invokeMethod("incoming", it)
            pendingName = null
        }
    }

    private fun handleIntent(i: Intent?) {
        val n = i?.getStringExtra("fake_call_name") ?: return
        pendingName = n
        i.removeExtra("fake_call_name")
        // Show over the lock screen and switch the screen on
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val ch = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "fakecall/native")
        channel = ch
        ch.setMethodCallHandler { call, result ->
            when (call.method) {
                "sync" -> {
                    CallScheduler.sync(this, call.arguments as Map<*, *>)
                    CallScheduler.updateScreenOff(this)
                    result.success(null)
                }
                "arm" -> result.success(CallScheduler.arm(this))
                "cancel" -> { CallScheduler.cancel(this); result.success(null) }
                "status" -> result.success(CallScheduler.activeEndAt(this))
                "stopAlert" -> { CallScheduler.stopAlert(this); result.success(null) }
                "pendingCall" -> { result.success(pendingName); pendingName = null }
                "a11yEnabled" -> result.success(CallScheduler.a11yEnabled(this))
                "setMute" -> {
                    val mute = call.arguments as? Boolean ?: false
                    getSystemService(AudioManager::class.java).adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                        0
                    )
                    result.success(null)
                }
                "getMute" -> {
                    val m = if (Build.VERSION.SDK_INT >= 23)
                        getSystemService(AudioManager::class.java).isStreamMute(AudioManager.STREAM_MUSIC)
                    else false
                    result.success(m)
                }
                "callEnded" -> {
                    if (Build.VERSION.SDK_INT >= 27) {
                        setShowWhenLocked(false)
                        setTurnScreenOn(false)
                    }
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    result.success(null)
                }
                "openA11y" -> {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    result.success(null)
                }
                "openOverlay" -> {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    )
                    result.success(null)
                }
                "openAppInfo" -> {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                    )
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }

    // Fallback while the app is open and the accessibility service is not enabled
    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        val isDown = ev.action == KeyEvent.ACTION_DOWN
        when (ev.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> up = isDown
            KeyEvent.KEYCODE_VOLUME_DOWN -> down = isDown
        }
        if (up && down && isDown && CallScheduler.prefs(this).getBoolean("volumeTrigger", true)) {
            CallScheduler.arm(this)
            return true
        }
        return super.dispatchKeyEvent(ev)
    }
}
