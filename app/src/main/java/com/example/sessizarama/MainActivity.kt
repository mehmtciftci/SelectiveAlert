package com.example.sessizarama

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
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

    private val reqContact = 10
    private val reqTone = 11
    private val reqRole = 12
    private val reqPerms = 13

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

        root.addView(button("1) İzinleri ver (rehber + telefon durumu)") {
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_PHONE_STATE
                ),
                reqPerms
            )
        })
        root.addView(button("2) Arama filtresi rolünü ver") { requestRole() })
        root.addView(button("3) Rehberden kişi seç") { pickContact() })
        root.addView(input)
        root.addView(button("Numarayı elle kaydet") {
            prefs().edit()
                .putString("num", input.text.toString().trim())
                .putString("name", "")
                .apply()
            Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show()
            refresh()
        })
        root.addView(button("Ses seç") { pickTone() })
        root.addView(button("Sesi test et") {
            Ringer.start(this, false)
            refresh()
        })
        root.addView(button("Sesi durdur") {
            Ringer.stop()
            refresh()
        })
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

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            when (requestCode) {
                reqContact -> saveContact(data.data)
                reqTone -> {
                    val uri = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                    val e = prefs().edit()
                    if (uri != null) e.putString("tone", uri.toString()) else e.remove("tone")
                    e.apply()
                }
            }
        }
        refresh()
    }

    private fun saveContact(uri: Uri?) {
        if (uri == null) return
        try {
            val cols = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
            contentResolver.query(uri, cols, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val number = c.getString(0) ?: ""
                    val name = c.getString(1) ?: ""
                    prefs().edit().putString("num", number).putString("name", name).apply()
                    input.setText(number)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Kişi okunamadı: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun prefs() = getSharedPreferences("p", MODE_PRIVATE)

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { action() }
    }

    private fun pickContact() {
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
                reqContact
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Rehber açılamadı, numarayı elle yaz", Toast.LENGTH_LONG).show()
        }
    }

    private fun pickTone() {
        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_RINGTONE or RingtoneManager.TYPE_ALARM
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Ses seç")
            prefs().getString("tone", null)?.let {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
            }
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, reqTone)
        } catch (e: Exception) {
            Toast.makeText(this, "Ses seçici açılamadı", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
            !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        ) {
            @Suppress("DEPRECATION")
            startActivityForResult(
                rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),
                reqRole
            )
        }
    }

    private fun toneName(): String {
        val t = prefs().getString("tone", null) ?: return "Varsayılan zil sesi"
        return try {
            RingtoneManager.getRingtone(this, Uri.parse(t))?.getTitle(this) ?: "Seçili ses"
        } catch (e: Exception) {
            "Seçili ses"
        }
    }

    private fun refresh() {
        val rm = getSystemService(RoleManager::class.java)
        val roleOk = rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        val phoneOk = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        val contactsOk = checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        val am = getSystemService(AudioManager::class.java)
        val alarmVol = am.getStreamVolume(AudioManager.STREAM_ALARM)
        val num = prefs().getString("num", "") ?: ""
        val name = prefs().getString("name", "") ?: ""
        val last = prefs().getString("last", null)

        status.text = buildString {
            appendLine("Kişi: ${if (num.isBlank()) "SEÇİLMEDİ" else (if (name.isNotBlank()) "$name - " else "") + num}")
            appendLine("Rehber izni: ${if (contactsOk) "VAR" else "YOK (kayıtlı kişide ZORUNLU)"}")
            appendLine("Arama filtresi rolü: ${if (roleOk) "VAR" else "YOK (zorunlu)"}")
            appendLine("Telefon durumu izni: ${if (phoneOk) "VAR" else "YOK (ses arama bitince durmaz, 45 sn sonra durur)"}")
            appendLine("Ses: ${toneName()}")
            appendLine("Alarm ses seviyesi: $alarmVol")
            appendLine("Ses şu an: ${if (Ringer.isPlaying()) "ÇALIYOR" else "kapalı"}")
            appendLine("Son gelen arama: ${last ?: "henüz yok"}")
        }
    }
}
