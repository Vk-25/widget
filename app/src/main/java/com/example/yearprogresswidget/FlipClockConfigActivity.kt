package com.example.yearprogresswidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * FlipClockConfigActivity — one-time setup shown when a new widget is placed.
 *
 * Steps:
 *   1. Theme    — Dark (charcoal-slate) | Light (cream-white)
 *   2. Format   — 24-hour | 12-hour (AM/PM shown as superscript in tile)
 *   3. Seconds  — Show | Hide
 */
class FlipClockConfigActivity : AppCompatActivity() {

    private var widgetId      = AppWidgetManager.INVALID_APPWIDGET_ID
    private var selectedDark  = true
    private var use24h        = true
    private var showSeconds   = true

    private lateinit var cardDark : LinearLayout
    private lateinit var cardLight: LinearLayout

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

        // ── Root ───────────────────────────────────────────────────────────
        val scroll = ScrollView(this)
        val root   = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#09090D"))
            setPadding(px(22), px(58), px(22), px(60))
        }
        scroll.setBackgroundColor(Color.parseColor("#09090D"))
        scroll.addView(root)

        // ── Header ─────────────────────────────────────────────────────────
        root.addView(makeLabel("Flip Clock", 30f, Color.WHITE, bold = true))
        root.addView(makeLabel("Choose your style", 14f, Color.parseColor("#606070"), bpad = px(40)))

        // ── THEME ──────────────────────────────────────────────────────────
        root.addView(sectionLbl("THEME", px(14)))

        val availW = scw - px(44)
        val cardW  = (availW - px(12)) / 2
        val dCw    = (cardW * 0.24f).toInt().coerceIn(38, 110)
        val dCh    = (dCw * 1.36f).toInt()
        val dSep   = (dCw * 0.30f).toInt().coerceAtLeast(9)

        val themeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER
            setPadding(0, 0, 0, px(34))
        }

        cardDark  = buildThemeCard(true,  cardW, dCw, dCh, dSep, px(6))
        cardLight = buildThemeCard(false, cardW, dCw, dCh, dSep, px(6))
        cardDark.setOnClickListener  { selectedDark = true;  highlightTheme() }
        cardLight.setOnClickListener { selectedDark = false; highlightTheme() }
        themeRow.addView(cardDark); themeRow.addView(cardLight)
        root.addView(themeRow)
        highlightTheme()

        // ── FORMAT ─────────────────────────────────────────────────────────
        root.addView(sectionLbl("TIME FORMAT", px(14)))
        val b24 = toggleBtn("24-hour"); val b12 = toggleBtn("12-hour (AM/PM)")
        fun setFmt(is24: Boolean) {
            use24h = is24
            activateToggle(b24, is24); activateToggle(b12, !is24)
        }
        b24.setOnClickListener { setFmt(true) }; b12.setOnClickListener { setFmt(false) }
        setFmt(true)
        val fRow = hRow(px(26)); fRow.addView(b24); fRow.addView(spacer(px(10))); fRow.addView(b12)
        root.addView(fRow)

        // ── SECONDS ────────────────────────────────────────────────────────
        root.addView(sectionLbl("SECONDS", px(14)))
        val bShow = toggleBtn("Show"); val bHide = toggleBtn("Hide")
        fun setSec(show: Boolean) {
            showSeconds = show
            activateToggle(bShow, show); activateToggle(bHide, !show)
        }
        bShow.setOnClickListener { setSec(true) }; bHide.setOnClickListener { setSec(false) }
        setSec(true)
        val sRow = hRow(px(40)); sRow.addView(bShow); sRow.addView(spacer(px(10))); sRow.addView(bHide)
        root.addView(sRow)

        // ── Add button ─────────────────────────────────────────────────────
        root.addView(Button(this).apply {
            text = "Add Widget"; textSize = 16f; setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD); isAllCaps = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#4FC3F7"))
                cornerRadius = pxf(26f)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(52)).also { it.bottomMargin = px(14) }
            setOnClickListener { saveAndFinish() }
        })
        root.addView(makeLabel("Settings are saved per widget instance.", 11f, Color.parseColor("#404050")))

        setContentView(scroll)
    }

    // ── Theme card ────────────────────────────────────────────────────────

    private fun buildThemeCard(
        isDark: Boolean, cardW: Int,
        dCw: Int, dCh: Int, dSep: Int, margin: Int
    ): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER
            val v = (resources.displayMetrics.density * 16f).toInt()
            setPadding(0, v, 0, v)
            layoutParams = LinearLayout.LayoutParams(cardW, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.setMargins(margin, 0, margin, 0) }
        }
        val dRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER
            setPadding(0, 0, 0, (resources.displayMetrics.density * 12f).toInt())
        }
        for (item in listOf("1","2","sep","3","0")) {
            if (item == "sep") {
                dRow.addView(ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dSep, dCh).also { it.setMargins(2,0,2,0) }
                    scaleType = ImageView.ScaleType.FIT_XY
                    setImageBitmap(FlipClockWidget.sep(isDark, dCw, dCh))
                })
            } else {
                dRow.addView(ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dCw, dCh).also { it.setMargins(2,0,2,0) }
                    scaleType = ImageView.ScaleType.FIT_XY
                    setImageBitmap(FlipClockWidget.card(item, isDark, dCw, dCh, ""))
                })
            }
        }
        col.addView(dRow)
        col.addView(makeLabel(if (isDark) "Dark" else "Light", 13f,
            if (isDark) Color.parseColor("#A0A0B0") else Color.parseColor("#6070808")))
        return col
    }

    private fun highlightTheme() {
        val dm = resources.displayMetrics
        fun active() = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#1C1C26"))
            cornerRadius = 22f * dm.density
            setStroke((2f * dm.density).toInt(), Color.parseColor("#4FC3F7"))
        }
        fun idle(dark: Boolean) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(if (dark) Color.parseColor("#131318") else Color.parseColor("#EDF2FA"))
            cornerRadius = 22f * dm.density
            setStroke((1f * dm.density).toInt(),
                if (dark) Color.parseColor("#20FFFFFF") else Color.parseColor("#40FFFFFF"))
        }
        cardDark.background  = if (selectedDark) active() else idle(true)
        cardLight.background = if (!selectedDark) active() else idle(false)
    }

    // ── Toggle helpers ────────────────────────────────────────────────────

    private fun toggleBtn(label: String) = Button(this).apply {
        text = label; textSize = 13.5f; isAllCaps = false
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        setTextColor(Color.parseColor("#60606A"))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#1A1A24"))
            cornerRadius = resources.displayMetrics.density * 22f
            setStroke((1f * resources.displayMetrics.density).toInt(), Color.parseColor("#25FFFFFF"))
        }
        layoutParams = LinearLayout.LayoutParams(0, (44f * resources.displayMetrics.density).toInt(), 1f)
    }

    private fun activateToggle(b: Button, active: Boolean) {
        (b.background as? GradientDrawable)?.setColor(
            Color.parseColor(if (active) "#4FC3F7" else "#1A1A24"))
        b.setTextColor(if (active) Color.WHITE else Color.parseColor("#60606A"))
    }

    // ── Misc builders ─────────────────────────────────────────────────────

    private fun sectionLbl(text: String, bpad: Int) = TextView(this).apply {
        this.text = text; textSize = 10.5f
        setTextColor(Color.parseColor("#484858"))
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD); letterSpacing = 0.14f
        setPadding(0, 0, 0, bpad)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun makeLabel(text: String, size: Float, color: Int,
                          bold: Boolean = false, bpad: Int = 0) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        typeface = if (bold) Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        else      Typeface.create("sans-serif-light", Typeface.NORMAL)
        gravity = Gravity.CENTER; letterSpacing = if (bold) -0.02f else 0.02f; setPadding(0, 0, 0, bpad)
    }

    private fun hRow(bpad: Int = 0) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0,0,0,bpad)
    }

    private fun spacer(w: Int) = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(w, 1)
    }

    // ── Save ──────────────────────────────────────────────────────────────

    private fun saveAndFinish() {
        getSharedPreferences("flip_prefs", MODE_PRIVATE).edit().apply {
            putBoolean("dark_$widgetId",          selectedDark)
            putBoolean("use_24h_$widgetId",       use24h)
            putBoolean("show_seconds_$widgetId",  showSeconds)
            apply()
        }
        val mgr = AppWidgetManager.getInstance(this)
        FlipClockWidget.pushFrame(this, mgr, widgetId)
        FlipClockWidget.startTicker(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}