package com.example.fakecall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock

/**
 * Lets Volume Up + Down trigger the fake call while the screen is OFF.
 * It keeps a silent audio stream + media session alive so Android still delivers
 * the volume keys to this app when the screen is off.
 */
class ScreenOffService : Service() {
    private var session: MediaSession? = null
    private var track: AudioTrack? = null
    @Volatile private var running = false
    private var lastUp = 0L
    private var lastDown = 0L

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(2002, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(2002, n)
        }
        if (session == null) setup()
        return START_STICKY
    }

    private fun setup() {
        val s = MediaSession(this, "FakeCallKeys")
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(0L)
                .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                .build()
        )
        s.setPlaybackToRemote(object : VolumeProvider(VolumeProvider.VOLUME_CONTROL_RELATIVE, 100, 50) {
            override fun onAdjustVolume(direction: Int) {
                handleKey(direction)
            }
        })
        s.isActive = true
        session = s
        startSilence()
    }

    private fun handleKey(dir: Int) {
        val now = SystemClock.elapsedRealtime()
        if (dir > 0) lastUp = now else if (dir < 0) lastDown = now

        val enabled = CallScheduler.prefs(this).getBoolean("volumeTrigger", true)
        if (enabled && lastUp > 0 && lastDown > 0 && Math.abs(lastUp - lastDown) < 450) {
            lastUp = 0
            lastDown = 0
            CallScheduler.arm(this)
            return
        }
        // Screen on: behave like normal volume buttons
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isInteractive && dir != 0) {
            getSystemService(AudioManager::class.java).adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (dir > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
        }
    }

    private fun startSilence() {
        val rate = 8000
        val minBuf = AudioTrack.getMinBufferSize(
            rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        t.play()
        running = true
        Thread {
            val buf = ShortArray(minBuf)
            while (running) {
                try {
                    t.write(buf, 0, buf.size)
                } catch (_: Exception) {
                    break
                }
            }
        }.start()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel("screenoff") == null) {
            nm.createNotificationChannel(
                NotificationChannel("screenoff", "Volume trigger", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "screenoff")
        else Notification.Builder(this)
        return b.setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("Fake Call volume trigger is on")
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        running = false
        try { track?.stop(); track?.release() } catch (_: Exception) {}
        try { session?.release() } catch (_: Exception) {}
        track = null
        session = null
        super.onDestroy()
    }
}
