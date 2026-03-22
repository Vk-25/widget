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
    private var isDark      = true

    private lateinit var clockIvs : List<ImageView>
    private lateinit var sepIvs   : List<ImageView>
    private lateinit var dateLbl  : TextView
    private var cardW = 0; private var cardH = 0; private var sepW = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dm  = resources.displayMetrics
        val scw = dm.widthPixels
        isDark  = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        fun dp(v: Float)  = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,  v, dm).toInt()
        fun sp(v: Float)  = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,   v, dm)

        val hPad = dp(20f)

        // ── Dynamic card sizing — mirrors pushFrame formula ─────────────────
        // Same formula as FlipClockWidget.pushFrame:
        //   cw = ((screenDp - 42) * density / 6.56)
        // Here screenDp approximated from px
        val screenDp = (scw / dm.density).roundToInt()
        cardW = ((screenDp - 42) * dm.density / 6.56f).roundToInt().coerceIn(36, 180)
        cardH = (cardW * 1.36f).roundToInt()
        sepW  = (cardW * 0.28f).roundToInt().coerceAtLeast(9)

        val scroll = ScrollView(this)
        val root   = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(hPad, dp(52f), hPad, dp(52f))
        }
        scroll.addView(root)

        // ── Header ───────────────────────────────────────────────────────────
        root.addView(lbl("Widget Studio", sp(28f), tag = "t1", bold = true, bpad = dp(4f)))
        root.addView(lbl("Live preview  ·  home screen widgets", sp(13f), tag = "t2", bpad = dp(30f)))

        // ── Theme toggle ─────────────────────────────────────────────────────
        val thRow = hRow(bpad = dp(22f))
        val thLbl = lbl("Theme", sp(13f), tag = "t2").also {
            it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val isNight = isDark
        val bSys   = pill("System"); val bDark = pill("Dark"); val bLight = pill("Light")
        bSys.setOnClickListener   { isDark = isNight; refresh(root, scroll, bSys, bDark, bLight, 0) }
        bDark.setOnClickListener  { isDark = true;    refresh(root, scroll, bSys, bDark, bLight, 1) }
        bLight.setOnClickListener { isDark = false;   refresh(root, scroll, bSys, bDark, bLight, 2) }
        thRow.addView(thLbl)
        thRow.addView(bSys);  thRow.addView(gap(dp(6f), 1))
        thRow.addView(bDark); thRow.addView(gap(dp(6f), 1))
        thRow.addView(bLight)
        root.addView(thRow)

        // ── Flip Clock card ──────────────────────────────────────────────────
        root.addView(sHead("Flip Clock", sp(15f), dp(10f)))
        val clockCard = glassCard()

        // Digit row
        val cRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER
        }
        val h1=iv(cardW,cardH); val h2=iv(cardW,cardH)
        val s1=iv(sepW, cardH)
        val m1=iv(cardW,cardH); val m2=iv(cardW,cardH)
        val s2=iv(sepW, cardH)
        val sc1=iv(cardW,cardH); val sc2=iv(cardW,cardH)
        clockIvs = listOf(h1,h2,m1,m2,sc1,sc2); sepIvs = listOf(s1,s2)
        val g = dp(3f)
        listOf(h1,gap(g,cardH),h2,gap(g,cardH),s1,gap(g,cardH),
            m1,gap(g,cardH),m2,gap(g,cardH),s2,gap(g,cardH),
            sc1,gap(g,cardH),sc2).forEach { cRow.addView(it) }
        clockCard.addView(cRow)

        // Date label — bottom, left-aligned, just below the digits
        dateLbl = TextView(this).apply {
            textSize   = 9.5f
            setTextColor(if (isDark) Color.parseColor("#D0C8C8D0") else Color.parseColor("#E03A4A5A"))
            typeface   = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity    = Gravity.START
            letterSpacing = 0.04f
            tag        = "t2"
            setPadding(dp(2f), dp(4f), 0, 0)
        }
        clockCard.addView(dateLbl)

        clockCard.addView(lbl("Updates every second", sp(11f), tag = "t2", tpad = dp(6f))
            .also { it.gravity = Gravity.CENTER_HORIZONTAL })
        root.addView(clockCard)
        root.addView(gap(1, dp(24f)))

        // ── Year Progress card ───────────────────────────────────────────────
        root.addView(sHead("Year Progress", sp(15f), dp(10f)))
        val ypCard = glassCard()
        val cal   = Calendar.getInstance()
        val day   = cal.get(Calendar.DAY_OF_YEAR)
        val yr    = cal.get(Calendar.YEAR)
        val total = if ((yr%4==0&&yr%100!=0)||(yr%400==0)) 366 else 365
        val pct   = "%.1f".format(day.toFloat()/total*100)
        ypCard.addView(lbl("$yr  ·  Day $day of $total  ·  $pct%", sp(13f), tag = "t2", bpad = dp(12f)))
        val dotW = scw - hPad * 2 - dp(32f)
        val dotH = (dotW * 0.52f).toInt()
        val dotIv = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dotW.toInt(), dotH)
            scaleType    = ImageView.ScaleType.FIT_XY
        }
        ypCard.addView(dotIv)
        root.addView(ypCard)
        root.addView(gap(1, dp(28f)))

        // ── Steps card ───────────────────────────────────────────────────────
        root.addView(sHead("Add to Home Screen", sp(15f), dp(10f)))
        val stCard = glassCard()
        listOf(
            "Long press an empty area on your home screen",
            "Tap 'Widgets'",
            "Search 'Flip Clock Widget' or 'Year Progress Widget'",
            "Drag it onto your home screen",
            "Flip Clock: choose Dark or Light theme when prompted"
        ).forEachIndexed { i, step ->
            val r = hRow(bpad = dp(10f))
            r.addView(TextView(this).apply {
                text = "${i+1}"; textSize = 12f; setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#4FC3F7")) }
                val sz = dp(22f); layoutParams = LinearLayout.LayoutParams(sz,sz).also { it.setMargins(0,0,dp(10f),0) }
            })
            r.addView(lbl(step, sp(13f), tag = "t2"))
            stCard.addView(r)
        }
        root.addView(stCard)
        root.addView(gap(1, dp(24f)))

        // ── Fix card ─────────────────────────────────────────────────────────
        root.addView(sHead("Widget Not Updating?", sp(15f), dp(10f)))
        val permCard = glassCard(); permCard.tag = "card"
        permCard.addView(lbl("If the clock stops, tap below to open this app's settings.", sp(13f), tag = "t2", bpad = dp(14f)))
        permCard.addView(Button(this).apply {
            text = "Open App Settings"; textSize = 14f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD); isAllCaps = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#4FC3F7"))
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
        permCard.addView(lbl("Permissions → Notifications\nBattery → No restrictions\nOther permissions → Alarms → Allow",
            sp(12f), tag = "t2"))
        root.addView(permCard)

        setContentView(scroll)
        applyTheme(root, scroll)
        hilite(bSys, bDark, bLight, 0)

        executor.submit {
            val bmp = YearProgressWidget.buildPreviewBitmap(day, total, dotW.toInt())
            mainHandler.post { dotIv.setImageBitmap(bmp) }
        }

        FlipClockWidget.startTicker(this)
        startTick()
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    private fun startTick() {
        ticking = true
        fun tick() {
            if (!ticking) return
            executor.submit {
                val c    = Calendar.getInstance()
                val h    = c.get(Calendar.HOUR_OF_DAY)
                val m    = c.get(Calendar.MINUTE)
                val s    = c.get(Calendar.SECOND)
                val digs = intArrayOf(h/10,h%10,m/10,m%10,s/10,s%10)
                val bmps = digs.map { FlipClockWidget.card(it.toString(), isDark, cardW, cardH) }
                val seps = List(2) { FlipClockWidget.sep(isDark, cardW, cardH) }
                val D = arrayOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")
                val M = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
                val ds = "${D[c.get(Calendar.DAY_OF_WEEK)-1]}, ${c.get(Calendar.DAY_OF_MONTH)} ${M[c.get(Calendar.MONTH)]} ${c.get(Calendar.YEAR)}"
                mainHandler.post {
                    if (!ticking) return@post
                    clockIvs.forEachIndexed { i, iv -> iv.setImageBitmap(bmps[i]) }
                    sepIvs.forEachIndexed   { i, iv -> iv.setImageBitmap(seps[i]) }
                    dateLbl.text = ds
                    mainHandler.postDelayed(::tick, 1000)
                }
            }
        }
        mainHandler.postDelayed(::tick, 200)
    }

    override fun onDestroy() { ticking = false; executor.shutdown(); super.onDestroy() }

    // ── Theme ─────────────────────────────────────────────────────────────────

    private fun refresh(root: LinearLayout, scroll: View, s: TextView, d: TextView, l: TextView, a: Int) {
        hilite(s, d, l, a); applyTheme(root, scroll)
    }

    private fun applyTheme(root: LinearLayout, scroll: View) {
        val bg  = if (isDark) Color.parseColor("#08080C") else Color.parseColor("#F2F4F8")
        val crd = if (isDark) Color.parseColor("#1A1A22") else Color.WHITE
        val t1  = if (isDark) Color.WHITE                else Color.parseColor("#0D0D0D")
        val t2  = if (isDark) Color.parseColor("#A0A0AA") else Color.parseColor("#4A5A6A")
        scroll.setBackgroundColor(bg); root.setBackgroundColor(bg)
        fun walk(v: View) {
            when (v.tag) {
                "t1"   -> (v as? TextView)?.setTextColor(t1)
                "t2"   -> (v as? TextView)?.setTextColor(t2)
                "card" -> v.background = cardDrw(crd)
            }
            if (v is LinearLayout) repeat(v.childCount) { walk(v.getChildAt(it)) }
        }
        walk(root)
    }

    private fun cardDrw(bg: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; setColor(bg)
        cornerRadius = resources.displayMetrics.density * 24f
        setStroke((resources.displayMetrics.density * 1f).toInt(),
            if (isDark) Color.parseColor("#28FFFFFF") else Color.parseColor("#50FFFFFF"))
    }

    private fun hilite(s: TextView, d: TextView, l: TextView, a: Int) {
        s.alpha = if(a==0) 1f else 0.35f; d.alpha = if(a==1) 1f else 0.35f; l.alpha = if(a==2) 1f else 0.35f
    }

    // ── Factories ────────────────────────────────────────────────────────────

    private fun pill(label: String) = TextView(this).apply {
        text = label; textSize = 12.5f; gravity = Gravity.CENTER; setPadding(26,9,26,9)
        setTextColor(if (isDark) Color.WHITE else Color.parseColor("#1A2530"))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(if (isDark) Color.parseColor("#252530") else Color.parseColor("#E0E8EE"))
            cornerRadius = 40f * resources.displayMetrics.density
        }
    }

    private fun lbl(text: String, spSz: Float, tag: String, bold: Boolean = false, bpad: Int = 0, tpad: Int = 0) =
        TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_PX, spSz); this.tag = tag
            typeface = if (bold) Typeface.create("sans-serif-thin", Typeface.BOLD)
            else      Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(0, tpad, 0, bpad)
        }

    private fun sHead(text: String, spSz: Float, bpad: Int) = TextView(this).apply {
        this.text = text; setTextSize(TypedValue.COMPLEX_UNIT_PX, spSz)
        typeface = Typeface.create("sans-serif", Typeface.BOLD); letterSpacing = 0.02f
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
}