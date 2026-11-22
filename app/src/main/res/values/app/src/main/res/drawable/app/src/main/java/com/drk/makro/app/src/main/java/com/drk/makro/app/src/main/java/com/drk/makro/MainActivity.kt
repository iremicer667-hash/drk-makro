package com.drk.makro

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.roundToLong

class MainActivity : Activity() {

    private lateinit var msInput: EditText
    private lateinit var cpsInput: EditText
    private lateinit var statusText: TextView
    private var syncing = false // ms <-> cps döngüsünü önler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun px(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24), px(48), px(24), px(24))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0F1115"))
            isFillViewport = true
            addView(root)
        }
        setContentView(scroll)

        fun label(text: String, size: Float, color: String, bold: Boolean = false) =
            TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(Color.parseColor(color))
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, px(12), 0, px(4))
            }

        fun numberField(hintText: String) = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            hint = hintText
            setSingleLine()
        }

        fun button(text: String, color: String, action: () -> Unit) = Button(this).apply {
            this.text = text
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = px(12).toFloat()
                setColor(Color.parseColor(color))
            }
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(52)
            ).apply { topMargin = px(12) }
        }
                root.addView(label("DRK MAKRO", 32f, "#FF6A00", bold = true))
        root.addView(label("Hızlı dokunuş makrosu", 14f, "#9AA0AA"))

        statusText = label("", 14f, "#FFFFFF", bold = true)
        root.addView(statusText)

        root.addView(label("Tıklama aralığı (ms)", 14f, "#9AA0AA"))
        msInput = numberField("100")
        root.addView(msInput)

        root.addView(label("CPS (saniyede tıklama)", 14f, "#9AA0AA"))
        cpsInput = numberField("10")
        root.addView(cpsInput)

        // ms yazınca CPS, CPS yazınca ms otomatik hesaplanır
        msInput.addTextChangedListener(watcher { text ->
            if (syncing || !msInput.hasFocus()) return@watcher
            val ms = text.toDoubleOrNull() ?: return@watcher
            if (ms <= 0) return@watcher
            syncing = true
            cpsInput.setText(String.format(Locale.US, "%.1f", 1000.0 / ms))
            syncing = false
        })
        cpsInput.addTextChangedListener(watcher { text ->
            if (syncing || !cpsInput.hasFocus()) return@watcher
            val cps = text.toDoubleOrNull() ?: return@watcher
            if (cps <= 0) return@watcher
            syncing = true
            msInput.setText((1000.0 / cps).roundToLong().toString())
            syncing = false
        })

        root.addView(button("Ayarları Kaydet", "#3A4157") { saveSettings() })
        root.addView(button("1) Erişilebilirlik Ayarlarını Aç", "#3A4157") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(button("2) Paneli Göster", "#1E9E4A") {
            saveSettings()
            val service = ClickService.instance
            if (service == null) {
                toast("Önce erişilebilirlikten DRK Makro'yu aç")
            } else {
                service.showPanel()
                toast("Panel açıldı: halkayı hedefe, butonu istediğin yere sürükle")
            }
        })
        root.addView(button("Paneli Gizle", "#CC2B2B") { ClickService.instance?.hidePanel() })

        root.addView(
            label(
                "Nasıl kullanılır: Paneli göster, turuncu halkayı tıklanacak noktaya sürükle, " +
                    "yeşil BAŞLAT butonunu rahat bir yere koy ve dokun. Makro çalışırken oyunda " +
                    "normal hareket edebilirsin. Tekrar dokununca durur.",
                13f, "#9AA0AA"
            )
        )
    }
        override fun onResume() {
        super.onResume()
        val ms = Prefs.intervalMs(this)
        showValues(ms)
        val active = ClickService.instance != null
        statusText.text = if (active) "Servis: AKTİF" else "Servis: KAPALI (erişilebilirlikten aç)"
        statusText.setTextColor(Color.parseColor(if (active) "#3DDC84" else "#FF5252"))
    }

    private fun saveSettings() {
        val typed = msInput.text.toString().toDoubleOrNull() ?: 100.0
        val clamped = typed.roundToLong().coerceIn(Prefs.MIN_INTERVAL_MS, Prefs.MAX_INTERVAL_MS)
        Prefs.setIntervalMs(this, clamped)
        showValues(clamped)
        toast("Kaydedildi: $clamped ms")
    }

    private fun showValues(ms: Long) {
        syncing = true
        msInput.setText(ms.toString())
        cpsInput.setText(String.format(Locale.US, "%.1f", 1000.0 / ms))
        syncing = false
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun watcher(onChange: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) = onChange(s?.toString().orEmpty())
    }
}
