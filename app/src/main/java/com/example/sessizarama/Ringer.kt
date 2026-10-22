package com.example.sessizarama

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager

object Ringer {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var appCtx: Context? = null
    private var oldVolume = -1
    private var callback: TelephonyCallback? = null
    private var seenRinging = false
    private val timeout = Runnable { stop() }

    fun isPlaying(): Boolean = player != null

    fun start(ctx: Context, watchCall: Boolean) {
        val app = ctx.applicationContext
        handler.post {
            if (player != null) return@post
            try {
                appCtx = app
                val am = app.getSystemService(AudioManager::class.java)
                oldVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
                am.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    am.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                    0
                )
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                val mp = MediaPlayer()
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIC)
                        .build()
                )
                mp.setDataSource(app, uri)
                mp.isLooping = true
                mp.prepare()
                mp.start()
                player = mp
                handler.postDelayed(timeout, 60_000)
                if (watchCall) watchCallState(app)
            } catch (e: Exception) {
                stop()
            }
        }
    }

    private fun watchCallState(app: Context) {
        if (Build.VERSION.SDK_INT < 31) return
        if (app.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            seenRinging = false
            val tm = app.getSystemService(TelephonyManager::class.java)
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    when (state) {
                        TelephonyManager.CALL_STATE_RINGING -> seenRinging = true
                        TelephonyManager.CALL_STATE_OFFHOOK -> stop()
                        TelephonyManager.CALL_STATE_IDLE -> if (seenRinging) stop()
                    }
                }
            }
            tm.registerTelephonyCallback(app.mainExecutor, cb)
            callback = cb
        } catch (e: Exception) {
            // izin yoksa 60 sn zaman aşımı devreye girer
        }
    }

    fun stop() {
        handler.post {
            handler.removeCallbacks(timeout)
            player?.let {
                try { it.stop() } catch (e: Exception) { }
                it.release()
            }
            player = null
            val app = appCtx
            if (app != null) {
                try {
                    if (oldVolume >= 0) {
                        app.getSystemService(AudioManager::class.java)
                            .setStreamVolume(AudioManager.STREAM_ALARM, oldVolume, 0)
                    }
                    val cb = callback
                    if (cb != null && Build.VERSION.SDK_INT >= 31) {
                        app.getSystemService(TelephonyManager::class.java)
                            .unregisterTelephonyCallback(cb)
                    }
                } catch (e: Exception) { }
            }
            callback = null
            oldVolume = -1
        }
    }
}
