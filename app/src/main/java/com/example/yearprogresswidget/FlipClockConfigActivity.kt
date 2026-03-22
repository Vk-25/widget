package com.example.yearprogresswidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class FlipClockConfigActivity : AppCompatActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setResult(RESULT_CANCELED)

        val dm  = resources.displayMetrics
        val scw = dm.widthPixels
        fun px(d: Int) = (d * dm.density).toInt()
        fun pxf(d: Float) = d * dm.density

        // ── Root — deep black bg ──────────────────────────────────────────
        val scroll = ScrollView(this)
        val root   = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#08080C"))
            setPadding(px(20), px(56), px(20), px(56))
        }
        scroll.setBackgroundColor(Color.parseColor("#08080C"))
        scroll.addView(root)

        // ── Title ──────────────────────────────────────────────────────────
        root.addView(makeLabel("Flip Clock", 32f, Color.WHITE, bold = true))
        root.addView(makeLabel("Choose your theme", 15f,
            Color.parseColor("#7A7A82"), bpad = px(44)))

        // ── Card size for preview ─────────────────────────────────────────
        val cardContW = ((scw - px(40)) * 0.46f).toInt()
        val dCw = (cardContW * 0.26f).toInt().coerceIn(40, 120)
        val dCh = (dCw * 1.36f).toInt()
        val dSep= (dCw * 0.30f).toInt().coerceAtLeast(10)

        // ── Theme cards ───────────────────────────────────────────────────
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER
            setPadding(0, 0, 0, px(40))
        }

        for (dark in listOf(true, false)) {
            // Glass card container
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity     = Gravity.CENTER
                setPadding(px(14), px(20), px(14), px(20))
                background  = glassCard(dark)
                layoutParams = LinearLayout.LayoutParams(cardContW, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.setMargins(px(8), 0, px(8), 0) }
            }

            // Mini clock preview — digits rendered at dCw/dCh
            val dRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER
                setPadding(0, 0, 0, px(14))
            }
            for (item in listOf("1","2","sep","3","0")) {
                if (item == "sep") {
                    val raw = FlipClockWidget.sep(dark, dCw, dCh)
                    dRow.addView(ImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(dSep, dCh)
                            .also { it.setMargins(px(2),0,px(2),0) }
                        scaleType = ImageView.ScaleType.FIT_XY
                        setImageBitmap(raw)
                    })
                } else {
                    val raw = FlipClockWidget.card(item, dark, dCw, dCh)
                    dRow.addView(ImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(dCw, dCh)
                            .also { it.setMargins(px(2),0,px(2),0) }
                        scaleType = ImageView.ScaleType.FIT_XY
                        setImageBitmap(raw)
                    })
                }
            }
            col.addView(dRow)

            // Label
            col.addView(makeLabel(
                if (dark) "Dark" else "Light", 13f,
                if (dark) Color.parseColor("#A0A0A8") else Color.parseColor("#6A7A8A"),
                bpad = px(10)
            ))

            // Button
            val isDk = dark
            col.addView(Button(this).apply {
                text          = if (dark) "Select Dark" else "Select Light"
                textSize      = 14f
                setTextColor(Color.WHITE)
                typeface      = Typeface.create("sans-serif", Typeface.BOLD)
                isAllCaps     = false
                letterSpacing = 0.01f
                background    = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.parseColor(if (dark) "#4FC3F7" else "#007AFF"))
                    cornerRadius = pxf(22f)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, px(44))
                setOnClickListener { saveAndFinish(isDk) }
            })

            row.addView(col)
        }
        root.addView(row)

        // Tip
        root.addView(makeLabel(
            "Theme is set once per widget instance.",
            12f, Color.parseColor("#484850")
        ))

        setContentView(scroll)
    }

    private fun makeLabel(
        text: String, size: Float, color: Int,
        bold: Boolean = false, bpad: Int = 0
    ) = TextView(this).apply {
        this.text = text
        textSize  = size
        setTextColor(color)
        typeface  = if (bold) Typeface.create("sans-serif-thin", Typeface.BOLD)
        else      Typeface.create("sans-serif-light", Typeface.NORMAL)
        gravity   = Gravity.CENTER
        letterSpacing = if (bold) -0.02f else 0.03f
        setPadding(0, 0, 0, bpad)
    }

    private fun glassCard(dark: Boolean): android.graphics.drawable.Drawable {
        val dm = resources.displayMetrics
        return GradientDrawable().apply {
            shape        = GradientDrawable.RECTANGLE
            setColor(if (dark) Color.parseColor("#1E1E26") else Color.parseColor("#EEF3FA"))
            cornerRadius = 24f * dm.density
            setStroke(
                (1.2f * dm.density).toInt(),
                if (dark) Color.parseColor("#32FFFFFF") else Color.parseColor("#65FFFFFF")
            )
        }
    }

    private fun saveAndFinish(isDark: Boolean) {
        getSharedPreferences("flip_prefs", MODE_PRIVATE)
            .edit().putBoolean("dark_$widgetId", isDark).apply()
        val mgr = AppWidgetManager.getInstance(this)
        FlipClockWidget.pushFrame(this, mgr, widgetId)
        FlipClockWidget.startTicker(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}