package com.example.sessizarama

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var input: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        input = EditText(this).apply {
            hint = "Numara (örn. +905551234567)"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(prefs().getString("num", ""))
        }
        status = TextView(this).apply { setPadding(0, pad, 0, pad) }

        root.addView(input)
        root.addView(button("Numarayı kaydet") {
            prefs().edit().putString("num", input.text.toString().trim()).apply()
            Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show()
            refresh()
        })
        root.addView(button("1) Arama filtresi rolünü ver") { requestRole() })
        root.addView(button("2) Telefon durumu iznini ver") {
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), 1)
        })
        root.addView(button("Sesi test et") { Ringer.start(this, false); refresh() })
        root.addView(button("Sesi durdur") { Ringer.stop(); refresh() })
        root.addView(status)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        refresh()
    }

    private fun prefs() = getSharedPreferences("p", MODE_PRIVATE)

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { action() }
    }

    private fun requestRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
            !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        ) {
            @Suppress("DEPRECATION")
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 2)
        }
    }

    private fun refresh() {
        val rm = getSystemService(RoleManager::class.java)
        val roleOk = rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        val phoneOk = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        val am = getSystemService(AudioManager::class.java)
        val alarmVol = am.getStreamVolume(AudioManager.STREAM_ALARM)
        val num = prefs().getString("num", "") ?: ""
        status.text = buildString {
            appendLine("Kayıtlı numara: ${if (num.isBlank()) "YOK" else num}")
            appendLine("Arama filtresi rolü: ${if (roleOk) "VAR" else "YOK (zorunlu)"}")
            appendLine("Telefon durumu izni: ${if (phoneOk) "VAR" else "YOK (ses arama bitince durmaz, 60 sn sonra durur)"}")
            appendLine("Alarm ses seviyesi: $alarmVol")
            appendLine("Ses şu an: ${if (Ringer.isPlaying()) "ÇALIYOR" else "kapalı"}")
        }
    }
}
