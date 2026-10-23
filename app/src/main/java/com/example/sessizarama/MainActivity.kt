package com.example.sessizarama

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val reqContact = 10
    private val reqTone = 11
    private val reqRole = 12
    private val reqPerms = 13

    private val matchParent = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrapContent = ViewGroup.LayoutParams.WRAP_CONTENT

    private var draft: String? = null
    private var scroll: ScrollView? = null

    // Renk paleti (açık / koyu tema)
    private var dark = false
    private var cBg = 0
    private var cCard = 0
    private var cText = 0
    private var cText2 = 0
    private var cPrimary = 0
    private var cAccent = 0
    private var cOk = 0
    private var cOkBg = 0
    private var cBad = 0
    private var cBadBg = 0
    private var cWarn = 0
    private var cWarnBg = 0
    private var cLine = 0
    private var cInputBg = 0
    private var cHeadA = 0
    private var cHeadB = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
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
        render()
    }

    // ---------------------------------------------------------------- veri

    private fun prefs() = getSharedPreferences("p", MODE_PRIVATE)

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
                    draft = null
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Kişi okunamadı: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveManual(number: String) {
        prefs().edit()
            .putString("num", number.trim())
            .putString("name", "")
            .apply()
        draft = null
        Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun askPermissions() {
        requestPermissions(
            arrayOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_PHONE_STATE
            ),
            reqPerms
        )
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

    // ---------------------------------------------------------------- ekran

    private fun render() {
        initPalette()
        val savedY = scroll?.scrollY ?: 0

        val rm = getSystemService(RoleManager::class.java)
        val roleOk = rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        val phoneOk = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        val contactsOk = checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        val num = prefs().getString("num", "") ?: ""
        val name = prefs().getString("name", "") ?: ""
        val last = prefs().getString("last", null)
        val hasContact = num.isNotBlank()
        val missing = listOf(contactsOk, roleOk, hasContact).count { !it }
        val ready = missing == 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(28))
        }
        root.addView(buildHeader(ready, missing))
        root.addView(buildSetupCard(contactsOk, roleOk, phoneOk, hasContact))
        root.addView(buildContactCard(num, name))
        root.addView(buildSoundCard())
        root.addView(buildLastCallCard(last))
        root.addView(
            tv("Sessiz Arama  •  v1.0", 12f, cText2).apply {
                gravity = Gravity.CENTER
                layoutParams = lp(matchParent, wrapContent, 16, 22, 16, 0)
            }
        )

        val sv = ScrollView(this).apply {
            setBackgroundColor(cBg)
            isFillViewport = true
            addView(root)
        }
        scroll = sv

        applySystemBars()
        window.setBackgroundDrawable(ColorDrawable(cBg))
        setContentView(sv)
        sv.post { sv.scrollTo(0, savedY) }
    }

    private fun buildHeader(ready: Boolean, missing: Int): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(cHeadA, cHeadB)
            ).apply {
                val r = dpf(28)
                setCornerRadii(floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r))
            }
            setPadding(dp(20), dp(22), dp(20), dp(26))
            layoutParams = lp(matchParent, wrapContent)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            layoutParams = lp(dp(52), dp(52))
        }
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = lp(0, wrapContent, 14, 0, 0, 0, 1f)
        }
        titles.addView(tv("Sessiz Arama", 24f, Color.WHITE, true))
        titles.addView(
            tv(
                "Seçtiğin kişi aradığında telefon sessizde bile çalar",
                13f,
                withAlpha(Color.WHITE, 215)
            ).apply { setPadding(0, dp(2), 0, 0) }
        )
        top.addView(icon)
        top.addView(titles)
        header.addView(top)

        val msg = if (ready) "Hazır — seçtiğin kişi aradığında ses çalar"
        else "Kurulum eksik ($missing adım kaldı)"
        val dotColor = if (ready) Color.parseColor("#69DB7C") else Color.parseColor("#FFD43B")
        val spannable = SpannableString("●  $msg")
        spannable.setSpan(ForegroundColorSpan(dotColor), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val pill = TextView(this).apply {
            text = spannable
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = rounded(withAlpha(Color.WHITE, 46), 50)
            layoutParams = lp(wrapContent, wrapContent, 0, 18, 0, 0)
        }
        header.addView(pill)
        return header
    }

    private fun buildSetupCard(
        contactsOk: Boolean, roleOk: Boolean, phoneOk: Boolean, hasContact: Boolean
    ): View {
        val c = card("Kurulum")
        c.addView(
            checkRow(
                "Rehber izni",
                if (contactsOk) "Verildi" else "Kayıtlı kişilerin aramalarını görmek için gerekli",
                contactsOk, false, "Ver"
            ) { askPermissions() }
        )
        c.addView(divider())
        c.addView(
            checkRow(
                "Arama filtresi rolü",
                if (roleOk) "Verildi" else "Gelen aramayı tanımak için gerekli",
                roleOk, false, "Ver"
            ) { requestRole() }
        )
        c.addView(divider())
        c.addView(
            checkRow(
                "Telefon durumu izni",
                if (phoneOk) "Verildi" else "İsteğe bağlı: arama bitince sesi hemen durdurur",
                phoneOk, true, "Ver"
            ) { askPermissions() }
        )
        c.addView(divider())
        c.addView(
            checkRow(
                "Kişi seçimi",
                if (hasContact) "Seçildi" else "Aşağıdan bir kişi seç",
                hasContact, false, "Seç"
            ) { pickContact() }
        )
        return c
    }

    private fun buildContactCard(num: String, name: String): View {
        val c = card("Kişi")
        val has = num.isNotBlank()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString()
            ?: if (has) "#" else "?"
        val avatar = TextView(this).apply {
            text = initial
            gravity = Gravity.CENTER
            setTextColor(cAccent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(withAlpha(cPrimary, 40))
            }
            layoutParams = lp(dp(52), dp(52))
        }
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = lp(0, wrapContent, 14, 0, 0, 0, 1f)
        }
        val title = when {
            name.isNotBlank() -> name
            has -> num
            else -> "Henüz kişi seçilmedi"
        }
        val sub = when {
            name.isNotBlank() -> num
            has -> "Elle girilen numara"
            else -> "Rehberden seç veya numarayı yaz"
        }
        info.addView(tv(title, 17f, cText, true))
        info.addView(tv(sub, 13.5f, cText2).apply { setPadding(0, dp(2), 0, 0) })
        row.addView(avatar)
        row.addView(info)
        c.addView(row)

        c.addView(
            makeButton("Rehberden kişi seç", true) { pickContact() }
                .apply { layoutParams = lp(matchParent, wrapContent, 0, 16, 0, 0) }
        )

        c.addView(tv("veya numarayı elle yaz", 12.5f, cText2).apply {
            layoutParams = lp(matchParent, wrapContent, 2, 16, 0, 6)
        })

        val input = EditText(this).apply {
            hint = "+905551234567"
            inputType = InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
            setText(draft ?: num)
            setTextColor(cText)
            setHintTextColor(withAlpha(cText2, 150))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            background = rounded(cInputBg, 12, cLine, 1)
            setPadding(dp(14), dp(11), dp(14), dp(11))
            layoutParams = lp(0, wrapContent, 0, 0, 10, 0, 1f)
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                draft = s?.toString()
            }

            override fun beforeTextChanged(s: CharSequence?, st: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, before: Int, count: Int) {}
        })
        val saveBtn = makeButton("Kaydet", false) { saveManual(input.text.toString()) }
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        inputRow.addView(input)
        inputRow.addView(saveBtn)
        c.addView(inputRow)
        return c
    }

    private fun buildSoundCard(): View {
        val c = card("Ses")
        val playing = Ringer.isPlaying()

        c.addView(tv("Çalacak ses", 12.5f, cText2))
        c.addView(tv(toneName(), 16f, cText, true).apply { setPadding(0, dp(2), 0, 0) })
        c.addView(
            tv(
                "Arama gelince alarm sesi otomatik en yükseğe alınır, sonra eski haline döner.",
                12.5f,
                cText2
            ).apply { setPadding(0, dp(6), 0, 0) }
        )

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = lp(matchParent, wrapContent, 0, 16, 0, 0)
        }
        val pickBtn = makeButton("Ses seç", false) { pickTone() }
            .apply { layoutParams = lp(0, wrapContent, 0, 0, 6, 0, 1f) }
        val testBtn = makeButton(if (playing) "Durdur" else "Test et", true) {
            if (Ringer.isPlaying()) {
                Ringer.stop()
                window.decorView.postDelayed({ render() }, 300)
            } else {
                Ringer.start(this, false)
                render()
            }
        }.apply { layoutParams = lp(0, wrapContent, 6, 0, 0, 0, 1f) }
        row.addView(pickBtn)
        row.addView(testBtn)
        c.addView(row)
        return c
    }

    private fun buildLastCallCard(last: String?): View {
        val c = card("Son gelen arama")
        if (last == null) {
            c.addView(tv("Henüz arama algılanmadı", 15f, cText, true))
            c.addView(
                tv(
                    "Kurulum bitince bir arama geldiğinde burada görünür. Eşleşme çalışıyor mu buradan anlarsın.",
                    12.5f,
                    cText2
                ).apply { setPadding(0, dp(4), 0, 0) }
            )
            return c
        }
        val parts = last.split(" | ")
        if (parts.size == 3) {
            val matched = parts[2] == "EŞLEŞTİ"
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = lp(0, wrapContent, 0, 0, 10, 0, 1f)
            }
            col.addView(tv(parts[1], 16f, cText, true))
            col.addView(tv(parts[0], 12.5f, cText2).apply { setPadding(0, dp(2), 0, 0) })
            row.addView(col)
            row.addView(
                if (matched) chip("Eşleşti", cOk, cOkBg)
                else chip("Eşleşmedi", cText2, withAlpha(cText2, 35))
            )
            c.addView(row)
        } else {
            c.addView(tv(last, 14f, cText))
        }
        return c
    }

    // ---------------------------------------------------------------- bileşenler

    private fun card(title: String): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cCard, 20, cLine, 1)
            elevation = if (dark) 0f else dpf(2)
            setPadding(dp(18), dp(16), dp(18), dp(18))
            layoutParams = lp(matchParent, wrapContent, 16, 14, 16, 0)
        }
        c.addView(
            tv(title, 13f, cAccent, true).apply {
                letterSpacing = 0.04f
                setPadding(0, 0, 0, dp(10))
            }
        )
        return c
    }

    private fun checkRow(
        title: String,
        sub: String,
        ok: Boolean,
        optional: Boolean,
        actionLabel: String,
        action: (() -> Unit)? = null
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val fg = if (ok) cOk else if (optional) cWarn else cBad
        val bg = if (ok) cOkBg else if (optional) cWarnBg else cBadBg
        val badge = TextView(this).apply {
            text = if (ok) "✓" else "!"
            gravity = Gravity.CENTER
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bg)
            }
            layoutParams = lp(dp(28), dp(28))
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = lp(0, wrapContent, 12, 0, 8, 0, 1f)
        }
        col.addView(tv(title, 15f, cText, true))
        col.addView(tv(sub, 12.5f, cText2).apply { setPadding(0, dp(1), 0, 0) })
        row.addView(badge)
        row.addView(col)
        if (!ok && action != null) {
            row.addView(makeButton(actionLabel, true, true) { action() })
        }
        return row
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(cLine)
        layoutParams = lp(matchParent, dp(1), 40, 0, 0, 0)
    }

    private fun chip(t: String, fg: Int, bg: Int): TextView = TextView(this).apply {
        text = t
        setTextColor(fg)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(12), dp(5), dp(12), dp(5))
        background = rounded(bg, 50)
    }

    private fun tv(t: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = t
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun makeButton(
        t: String,
        filled: Boolean,
        small: Boolean = false,
        onClick: () -> Unit
    ): Button {
        val fg = if (filled) Color.WHITE else cAccent
        val shape = if (filled) rounded(cPrimary, 14) else rounded(Color.TRANSPARENT, 14, cAccent, 1)
        val rippleColor = if (filled) withAlpha(Color.WHITE, 60) else withAlpha(cAccent, 50)
        return Button(this).apply {
            text = t
            isAllCaps = false
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (small) 13f else 15f)
            typeface = Typeface.DEFAULT_BOLD
            stateListAnimator = null
            elevation = 0f
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            val h = dp(if (small) 16 else 20)
            val v = dp(if (small) 8 else 13)
            setPadding(h, v, h, v)
            background = RippleDrawable(
                ColorStateList.valueOf(rippleColor),
                shape,
                rounded(Color.WHITE, 14)
            )
            setOnClickListener { onClick() }
        }
    }

    // ---------------------------------------------------------------- yardımcılar

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun dpf(v: Int): Float = v * resources.displayMetrics.density

    private fun withAlpha(c: Int, a: Int): Int =
        Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

    private fun rounded(
        fill: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpf(radiusDp)
        setColor(fill)
        if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
    }

    // w ve h piksel (veya MATCH/WRAP), kenar boşlukları dp
    private fun lp(
        w: Int, h: Int,
        l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0,
        weight: Float = 0f
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h, weight).apply {
            setMargins(dp(l), dp(t), dp(r), dp(b))
        }

    @Suppress("DEPRECATION")
    private fun applySystemBars() {
        window.statusBarColor = cHeadA
        window.navigationBarColor = cBg
        var flags = window.decorView.systemUiVisibility
        flags = if (dark) {
            flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        } else {
            flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags
    }

    private fun initPalette() {
        dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        if (dark) {
            cBg = Color.parseColor("#0E1220")
            cCard = Color.parseColor("#171C2E")
            cText = Color.parseColor("#EEF1F9")
            cText2 = Color.parseColor("#9AA4BD")
            cPrimary = Color.parseColor("#4C6EF5")
            cAccent = Color.parseColor("#91A7FF")
            cOk = Color.parseColor("#69DB7C")
            cOkBg = Color.parseColor("#17301F")
            cBad = Color.parseColor("#FF8787")
            cBadBg = Color.parseColor("#3A1C22")
            cWarn = Color.parseColor("#FFC078")
            cWarnBg = Color.parseColor("#3A2D14")
            cLine = Color.parseColor("#262D45")
            cInputBg = Color.parseColor("#121728")
            cHeadA = Color.parseColor("#1E2A78")
            cHeadB = Color.parseColor("#4A2C9B")
        } else {
            cBg = Color.parseColor("#F3F5FA")
            cCard = Color.parseColor("#FFFFFF")
            cText = Color.parseColor("#18202F")
            cText2 = Color.parseColor("#66708A")
            cPrimary = Color.parseColor("#3B5BDB")
            cAccent = Color.parseColor("#3B5BDB")
            cOk = Color.parseColor("#2B8A3E")
            cOkBg = Color.parseColor("#E3F6E8")
            cBad = Color.parseColor("#E03131")
            cBadBg = Color.parseColor("#FDEAEA")
            cWarn = Color.parseColor("#E67700")
            cWarnBg = Color.parseColor("#FFF1DB")
            cLine = Color.parseColor("#E4E8F1")
            cInputBg = Color.parseColor("#F7F8FC")
            cHeadA = Color.parseColor("#3B5BDB")
            cHeadB = Color.parseColor("#6741D9")
        }
    }
}
