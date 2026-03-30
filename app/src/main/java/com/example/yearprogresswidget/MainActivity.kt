package com.example.yearprogresswidget

import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private val executor    = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var ticking     = false

    // Preview state
    private var isDark      = true
    private var use24h      = true
    private var showSeconds = true

    // Clock preview views
    private lateinit var clockIvs : List<ImageView>
    private lateinit var sepIv1   : ImageView
    private lateinit var sepIv2   : ImageView
    private lateinit var dateIv   : ImageView
    private lateinit var dotIv    : ImageView

    private var cardW = 0; private var cardH = 0; private var sepW = 0; private var dotW = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dm  = resources.displayMetrics
        val scw = dm.widthPixels
        isDark  = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, dm).toInt()
        fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,  v, dm)
        val hPad = dp(20f)

        // Card sizing — matches pushFrame formula (with seconds)
        val screenDp = (scw / dm.density).roundToInt()
        cardW = ((screenDp - 42) * dm.density / 6.56f).roundToInt().coerceIn(36, 160)
        cardH = (cardW * 1.36f).roundToInt()
        sepW  = (cardW * 0.30f).roundToInt().coerceAtLeast(9)
        dotW  = scw - hPad * 2 - dp(32f)

        val scroll = ScrollView(this)
        val root   = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(hPad, dp(52f), hPad, dp(52f))
        }
        scroll.addView(root)

        // ── Header ───────────────────────────────────────────────────────
        root.addView(lbl("Widget Studio", sp(28f), "t1", bold = true, bpad = dp(4f)))
        root.addView(lbl("Live preview  ·  home screen widgets", sp(13f), "t2", bpad = dp(28f)))

        // ── Theme row ─────────────────────────────────────────────────────
        val thRow = hRow(bpad = dp(10f))
        val thLbl = lbl("Theme", sp(13f), "t2").also {
            it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val bDark = pill("Dark"); val bLight = pill("Light")
        val isSystemDark = isDark
        bDark.setOnClickListener  { isDark = true;          setThemePills(bDark, bLight, true);  applyTheme(root, scroll) }
        bLight.setOnClickListener { isDark = false;         setThemePills(bDark, bLight, false); applyTheme(root, scroll) }
        thRow.addView(thLbl); thRow.addView(bDark); thRow.addView(gap(dp(8f),1)); thRow.addView(bLight)
        root.addView(thRow)

        // ── Format row ────────────────────────────────────────────────────
        val fmtRow = hRow(bpad = dp(10f))
        val fmtLbl = lbl("Format", sp(13f), "t2").also {
            it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val b24 = pill("24h"); val b12 = pill("12h")
        b24.setOnClickListener { use24h = true;  setThemePills(b24, b12, true)  }
        b12.setOnClickListener { use24h = false; setThemePills(b24, b12, false) }
        fmtRow.addView(fmtLbl); fmtRow.addView(b24); fmtRow.addView(gap(dp(8f),1)); fmtRow.addView(b12)
        root.addView(fmtRow)

        // ── Seconds row ───────────────────────────────────────────────────
        val secRow = hRow(bpad = dp(22f))
        val secLbl = lbl("Seconds", sp(13f), "t2").also {
            it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val bShow = pill("Show"); val bHide = pill("Hide")
        bShow.setOnClickListener { showSeconds = true;  setThemePills(bShow, bHide, true);  refreshSecVis() }
        bHide.setOnClickListener { showSeconds = false; setThemePills(bShow, bHide, false); refreshSecVis() }
        secRow.addView(secLbl); secRow.addView(bShow); secRow.addView(gap(dp(8f),1)); secRow.addView(bHide)
        root.addView(secRow)

        // ── Flip Clock card ───────────────────────────────────────────────
        root.addView(sHead("Flip Clock", sp(15f), dp(10f)))
        val clockCard = glassCard()

        val cRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        }
        val g = dp(2f)
        val h1=iv(cardW,cardH); val h2=iv(cardW,cardH)
        val sep1V=iv(sepW,cardH)
        val m1=iv(cardW,cardH); val m2=iv(cardW,cardH)
        val sep2V=iv(sepW,cardH)
        val sc1=iv(cardW,cardH); val sc2=iv(cardW,cardH)
        clockIvs = listOf(h1,h2,m1,m2,sc1,sc2)
        sepIv1 = sep1V; sepIv2 = sep2V

        listOf(h1,gap(g,cardH),h2,gap(g,cardH),sep1V,gap(g,cardH),
            m1,gap(g,cardH),m2,gap(g,cardH),sep2V,gap(g,cardH),
            sc1,gap(g,cardH),sc2).forEach { cRow.addView(it) }
        clockCard.addView(cRow)

        // Date preview
        dateIv = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.topMargin = dp(6f) }
            scaleType = ImageView.ScaleType.FIT_START
            adjustViewBounds = true
        }
        clockCard.addView(dateIv)

        clockCard.addView(
            lbl("Updates every second", sp(11f), "t2", tpad = dp(7f)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL })
        root.addView(clockCard)
        root.addView(gap(1, dp(24f)))

        // ── Year Progress card ────────────────────────────────────────────
        root.addView(sHead("Year Progress", sp(15f), dp(10f)))
        val ypCard = glassCard()
        val cal   = Calendar.getInstance()
        val day   = cal.get(Calendar.DAY_OF_YEAR)
        val yr    = cal.get(Calendar.YEAR)
        val total = if ((yr%4==0&&yr%100!=0)||(yr%400==0)) 366 else 365
        val pct   = "%.1f".format(day.toFloat()/total*100)
        val left  = total - day
        ypCard.addView(lbl("$yr  ·  Day $day of $total  ·  $pct%  ·  $left days left", sp(12f), "t2", bpad = dp(12f)))

        dotIv = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dotW, (dotW * 0.55f).toInt())
            scaleType    = ImageView.ScaleType.FIT_XY
        }
        ypCard.addView(dotIv)
        root.addView(ypCard)
        root.addView(gap(1, dp(28f)))

        // ── Steps card ────────────────────────────────────────────────────
        root.addView(sHead("Add to Home Screen", sp(15f), dp(10f)))
        val stCard = glassCard()
        listOf(
            "Long-press an empty area on your home screen",
            "Tap  'Widgets'",
            "Search 'Flip Clock' or 'Year Progress'",
            "Long-press the widget and drag to place it",
            "Flip Clock: choose theme, format & seconds when prompted",
            "Resize by long-pressing the widget → drag handles"
        ).forEachIndexed { i, step ->
            val r = hRow(bpad = dp(10f))
            r.addView(TextView(this).apply {
                text = "${i+1}"; textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#4FC3F7")) }
                val sz = dp(22f); layoutParams = LinearLayout.LayoutParams(sz,sz).also { it.setMargins(0,0,dp(10f),0) }
            })
            r.addView(lbl(step, sp(13f), "t2"))
            stCard.addView(r)
        }
        root.addView(stCard)
        root.addView(gap(1, dp(24f)))

        // ── Troubleshoot card ─────────────────────────────────────────────
        root.addView(sHead("Widget Not Updating?", sp(15f), dp(10f)))
        val permCard = glassCard(); permCard.tag = "card"
        permCard.addView(lbl(
            "If the clock stops, open App Settings and allow background activity.",
            sp(13f), "t2", bpad = dp(14f)))
        permCard.addView(Button(this).apply {
            text = "Open App Settings"; textSize = 14f; setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD); isAllCaps = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; setColor(Color.parseColor("#4FC3F7"))
                cornerRadius = dp(22f).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48f))
                .also { it.bottomMargin = dp(12f) }
            setOnClickListener {
                try { startActivity(android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", packageName, null)))
                } catch (_: Exception) {}
            }
        })
        permCard.addView(lbl(
            "Battery → Unrestricted\nPermissions → Notifications → Allow\nOther → Alarms & reminders → Allow",
            sp(12f), "t2"))
        root.addView(permCard)

        setContentView(scroll)

        // Initial pill states
        setThemePills(bDark, bLight, isDark)
        setThemePills(b24, b12, true)
        setThemePills(bShow, bHide, true)
        applyTheme(root, scroll)

        // Load year progress async
        executor.submit {
            val dots = YearProgressWidget.buildPreviewBitmap(day, total, dotW)
            mainHandler.post { dotIv.setImageBitmap(dots) }
        }

        FlipClockWidget.startTicker(this)
        startTick()
    }

    // ── Live tick ─────────────────────────────────────────────────────────

    private fun startTick() {
        ticking = true
        fun tick() {
            if (!ticking) return
            executor.submit {
                val dm  = resources.displayMetrics
                val c   = Calendar.getInstance()
                val rawH = c.get(Calendar.HOUR_OF_DAY)
                val m    = c.get(Calendar.MINUTE)
                val s    = c.get(Calendar.SECOND)
                val isAm = rawH < 12
                val dispH = if (use24h) rawH else { val h12 = rawH % 12; if (h12 == 0) 12 else h12 }
                val digs  = intArrayOf(dispH/10, dispH%10, m/10, m%10, s/10, s%10)
                val amPm  = if (!use24h) (if (isAm) "AM" else "PM") else ""

                val bmps = listOf(
                    FlipClockWidget.card(digs[0].toString(), isDark, cardW, cardH, ""),
                    FlipClockWidget.card(digs[1].toString(), isDark, cardW, cardH, amPm),
                    FlipClockWidget.card(digs[2].toString(), isDark, cardW, cardH, ""),
                    FlipClockWidget.card(digs[3].toString(), isDark, cardW, cardH, ""),
                    FlipClockWidget.card(digs[4].toString(), isDark, cardW, cardH, ""),
                    FlipClockWidget.card(digs[5].toString(), isDark, cardW, cardH, "")
                )
                val sep1bmp = FlipClockWidget.sep(isDark, cardW, cardH)
                val sep2bmp = FlipClockWidget.sep(isDark, cardW, cardH)

                val dayNames = arrayOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")
                val monNames = arrayOf("January","February","March","April","May","June",
                    "July","August","September","October","November","December")
                val totalW   = ((dotW + dp(32f)) * dm.density / dm.density).roundToInt()
                val dateBmp  = FlipClockWidget.buildDateBitmap(
                    dayNames[c.get(Calendar.DAY_OF_WEEK)-1],
                    c.get(Calendar.DAY_OF_MONTH),
                    monNames[c.get(Calendar.MONTH)],
                    c.get(Calendar.YEAR),
                    isDark,
                    (dotW + resources.displayMetrics.density * 32).roundToInt(),
                    dm.density
                )

                mainHandler.post {
                    if (!ticking) return@post
                    clockIvs.forEachIndexed { i, iv -> iv.setImageBitmap(bmps[i]) }
                    sepIv1.setImageBitmap(sep1bmp); sepIv2.setImageBitmap(sep2bmp)
                    dateIv.setImageBitmap(dateBmp)
                    mainHandler.postDelayed(::tick, 1000)
                }
            }
        }
        mainHandler.postDelayed(::tick, 150)
    }

    private fun refreshSecVis() {
        val vis = if (showSeconds) View.VISIBLE else View.GONE
        clockIvs[4].visibility = vis; clockIvs[5].visibility = vis; sepIv2.visibility = vis
    }

    override fun onDestroy() { ticking = false; executor.shutdown(); super.onDestroy() }

    // ── Theme helpers ─────────────────────────────────────────────────────

    private fun applyTheme(root: LinearLayout, scroll: View) {
        val bg  = if (isDark) Color.parseColor("#08080C") else Color.parseColor("#F2F4F8")
        val crd = if (isDark) Color.parseColor("#1A1A22") else Color.WHITE
        val t1  = if (isDark) Color.WHITE                else Color.parseColor("#0D0D0D")
        val t2  = if (isDark) Color.parseColor("#A0A0AA") else Color.parseColor("#4A5A6A")
        scroll.setBackgroundColor(bg); root.setBackgroundColor(bg)
        fun walk(v: View) {
            when (v.tag) { "t1" -> (v as? TextView)?.setTextColor(t1)
                "t2" -> (v as? TextView)?.setTextColor(t2)
                "card" -> v.background = cardDrw(crd) }
            if (v is LinearLayout) repeat(v.childCount) { walk(v.getChildAt(it)) }
        }
        walk(root)
    }

    private fun setThemePills(a: TextView, b: TextView, aActive: Boolean) {
        a.alpha = if (aActive) 1f else 0.32f; b.alpha = if (!aActive) 1f else 0.32f
    }

    private fun cardDrw(bg: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; setColor(bg)
        cornerRadius = resources.displayMetrics.density * 24f
        setStroke((resources.displayMetrics.density * 1f).toInt(),
            if (isDark) Color.parseColor("#28FFFFFF") else Color.parseColor("#50000020"))
    }

    // ── View factories ────────────────────────────────────────────────────

    private fun pill(label: String) = TextView(this).apply {
        text = label; textSize = 12.5f; gravity = Gravity.CENTER; setPadding(26,9,26,9)
        setTextColor(if (isDark) Color.WHITE else Color.parseColor("#1A2530"))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(if (isDark) Color.parseColor("#252530") else Color.parseColor("#E0E8EE"))
            cornerRadius = 40f * resources.displayMetrics.density
        }
    }

    private fun lbl(text: String, spSz: Float, tag: String, bold: Boolean = false,
                    bpad: Int = 0, tpad: Int = 0) = TextView(this).apply {
        this.text = text; setTextSize(TypedValue.COMPLEX_UNIT_PX, spSz); this.tag = tag
        typeface = if (bold) Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        else      Typeface.create("sans-serif-light", Typeface.NORMAL)
        setPadding(0, tpad, 0, bpad)
    }

    private fun sHead(text: String, spSz: Float, bpad: Int) = TextView(this).apply {
        this.text = text; setTextSize(TypedValue.COMPLEX_UNIT_PX, spSz)
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD); letterSpacing = 0.02f
        setPadding(0, 0, 0, bpad); tag = "t1"
    }

    private fun glassCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; tag = "card"
        val p = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18f, resources.displayMetrics).toInt()
        setPadding(p, p, p, p)
        background = cardDrw(if (isDark) Color.parseColor("#1A1A22") else Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt() }
    }

    private fun hRow(bpad: Int = 0) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0,0,0,bpad)
    }

    private fun iv(w: Int, h: Int) = ImageView(this).apply {
        layoutParams = LinearLayout.LayoutParams(w, h); scaleType = ImageView.ScaleType.FIT_XY
    }

    private fun gap(w: Int, h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(w, h) }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()
}