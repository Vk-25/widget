package com.example.yearprogresswidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.RemoteViews
import java.util.Calendar
import kotlin.math.min
import kotlin.math.roundToInt

class FlipClockWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) pushFrame(ctx, mgr, id)
        startTicker(ctx)
    }
    override fun onEnabled(ctx: Context)  { startTicker(ctx) }
    override fun onDisabled(ctx: Context) { ctx.stopService(Intent(ctx, TickerService::class.java)) }
    override fun onAppWidgetOptionsChanged(
        ctx: Context, mgr: AppWidgetManager, id: Int, opts: android.os.Bundle
    ) { pushFrame(ctx, mgr, id) }

    companion object {

        const val CW = 120
        const val CH = 158

        fun startTicker(ctx: Context) {
            try { ctx.startService(Intent(ctx, TickerService::class.java)) } catch (_: Exception) {}
        }

        // ─────────────────────────────────────────────────────────────────────
        //  pushFrame — reads prefs, sizes cards dynamically, pushes RemoteViews
        // ─────────────────────────────────────────────────────────────────────
        fun pushFrame(ctx: Context, mgr: AppWidgetManager, id: Int) {
            try {
                val prefs    = ctx.getSharedPreferences("flip_prefs", Context.MODE_PRIVATE)
                val isDark   = prefs.getBoolean("dark_$id", true)
                val use24h   = prefs.getBoolean("use_24h_$id", true)
                val showSecs = prefs.getBoolean("show_seconds_$id", true)

                val cal  = Calendar.getInstance()
                val rawH = cal.get(Calendar.HOUR_OF_DAY)
                val m    = cal.get(Calendar.MINUTE)
                val s    = cal.get(Calendar.SECOND)
                val isAm = rawH < 12

                // Display hour
                val dispH = if (use24h) rawH else {
                    val h12 = rawH % 12; if (h12 == 0) 12 else h12
                }

                // ── Dynamic card sizing ──────────────────────────────────────
                // Layout: H1 H2 SEP M1 M2 [SEP S1 S2]
                // With seconds:    6 cards + 2 seps + 13 gaps(2dp) + 16dp pads = 6.56×cw + 42dp
                // Without seconds: 4 cards + 1 sep  +  8 gaps(2dp) +  8dp pads = 4.28×cw + 24dp
                val opts = mgr.getAppWidgetOptions(id)
                val wDp  = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280)
                val dens = ctx.resources.displayMetrics.density

                val cw = if (showSecs)
                    ((wDp - 42) * dens / 6.56f).roundToInt().coerceIn(38, 220)
                else
                    ((wDp - 24) * dens / 4.28f).roundToInt().coerceIn(50, 220)
                val ch = (cw * 1.36f).roundToInt()

                val digits = if (showSecs)
                    intArrayOf(dispH/10, dispH%10, m/10, m%10, s/10, s%10)
                else
                    intArrayOf(dispH/10, dispH%10, m/10, m%10, -1, -1)

                val vids = intArrayOf(R.id.iv_h1, R.id.iv_h2, R.id.iv_m1, R.id.iv_m2, R.id.iv_s1, R.id.iv_s2)

                val views = RemoteViews(ctx.packageName, R.layout.widget_flip_clock)

                // Render cards — pass am/pm hint only to the last-of-hours card (iv_h2)
                // so the superscript sits in the upper-right of that tile
                val amPmTag = if (!use24h) (if (isAm) "AM" else "PM") else ""

                views.setImageViewBitmap(vids[0], card(digits[0].toString(), isDark, cw, ch, ""))
                views.setImageViewBitmap(vids[1], card(digits[1].toString(), isDark, cw, ch, amPmTag))
                views.setImageViewBitmap(vids[2], card(digits[2].toString(), isDark, cw, ch, ""))
                views.setImageViewBitmap(vids[3], card(digits[3].toString(), isDark, cw, ch, ""))

                views.setImageViewBitmap(R.id.iv_sep1, sep(isDark, cw, ch))

                if (showSecs) {
                    views.setImageViewBitmap(vids[4], card(digits[4].toString(), isDark, cw, ch, ""))
                    views.setImageViewBitmap(vids[5], card(digits[5].toString(), isDark, cw, ch, ""))
                    views.setImageViewBitmap(R.id.iv_sep2, sep(isDark, cw, ch))
                    views.setViewVisibility(R.id.iv_s1,   android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.iv_s2,   android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.iv_sep2, android.view.View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.iv_s1,   android.view.View.GONE)
                    views.setViewVisibility(R.id.iv_s2,   android.view.View.GONE)
                    views.setViewVisibility(R.id.iv_sep2, android.view.View.GONE)
                }

                // ── Date strip bitmap ────────────────────────────────────────
                val dayNames = arrayOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")
                val monNames = arrayOf("January","February","March","April","May","June",
                    "July","August","September","October","November","December")
                val dayName  = dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1]
                val monName  = monNames[cal.get(Calendar.MONTH)]
                val dayNum   = cal.get(Calendar.DAY_OF_MONTH)
                val yearNum  = cal.get(Calendar.YEAR)

                // Date label — rendered as a wide bitmap for max control over size/style
                val totalWidthPx = ((wDp) * dens).roundToInt().coerceAtLeast(200)
                views.setImageViewBitmap(R.id.iv_date,
                    buildDateBitmap(dayName, dayNum, monName, yearNum, isDark, totalWidthPx, dens))

                mgr.updateAppWidget(id, views)
            } catch (_: Exception) {}
        }

        // ═════════════════════════════════════════════════════════════════════
        //  CARD — Classic flip-clock tile
        //
        //  Design: dark slate / light cream glass, BOLD digit (not thin),
        //  clean horizontal fold crease, subtle bevel + rim.
        //  AM/PM superscript sits top-right of the tile if amPm != "".
        //
        //  Layer stack (back → front):
        //   1. Body fill   — upper/lower halves with tonal split
        //   2. Top shimmer — surface reflection
        //   3. Left bevel  — edge refraction
        //   4. Digit       — BOLD, classic, large (56% of ch)
        //   5. AM/PM super — small bold label top-right corner
        //   6. Fold crease — shadow below + specular above mid
        //   7. Rim         — 1 px specular outer ring
        // ═════════════════════════════════════════════════════════════════════
        fun card(
            digit: String,
            isDark: Boolean,
            cw: Int = CW, ch: Int = CH,
            amPm: String = ""
        ): Bitmap {
            val bmp = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
            val cv  = Canvas(bmp)
            val W   = cw.toFloat(); val H = ch.toFloat()
            // Classic flip: slightly less rounding — looks like real flip-clock tiles
            val r   = min(W * 0.14f, H * 0.10f)
            val mid = H / 2f
            val full = RectF(0f, 0f, W, H)
            val p   = Paint(Paint.ANTI_ALIAS_FLAG)

            // ── 1. Body fill — upper/lower tonal split ───────────────────────
            // Dark: deep charcoal-blue slate
            // Light: warm off-white cream, slightly cooler on bottom
            if (isDark) {
                p.color = Color.parseColor("#EE1A1C24")   // charcoal-blue upper
                cv.save(); cv.clipRect(0f, 0f, W, mid); cv.drawRoundRect(full, r, r, p); cv.restore()
                p.color = Color.parseColor("#F2141620")   // deeper charcoal lower
                cv.save(); cv.clipRect(0f, mid, W, H);  cv.drawRoundRect(full, r, r, p); cv.restore()
            } else {
                p.color = Color.parseColor("#F0F5F7FA")   // cool white upper
                cv.save(); cv.clipRect(0f, 0f, W, mid); cv.drawRoundRect(full, r, r, p); cv.restore()
                p.color = Color.parseColor("#E8E8F0F8")   // slightly blue-grey lower
                cv.save(); cv.clipRect(0f, mid, W, H);  cv.drawRoundRect(full, r, r, p); cv.restore()
            }

            // ── 2. Top surface shimmer ───────────────────────────────────────
            p.shader = LinearGradient(0f, 0f, 0f, mid * 0.65f,
                intArrayOf(
                    if (isDark) Color.parseColor("#30FFFFFF") else Color.parseColor("#70FFFFFF"),
                    Color.TRANSPARENT
                ), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            cv.save(); cv.clipRect(0f, 0f, W, mid); cv.drawRoundRect(full, r, r, p); cv.restore()
            p.shader = null

            // ── 3. Left bevel ────────────────────────────────────────────────
            p.shader = LinearGradient(0f, 0f, W * 0.20f, 0f,
                intArrayOf(
                    if (isDark) Color.parseColor("#20FFFFFF") else Color.parseColor("#30FFFFFF"),
                    Color.TRANSPARENT
                ), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            cv.drawRoundRect(full, r, r, p); p.shader = null

            // ── 4. Digit — BOLD classic font, large ──────────────────────────
            // Typeface: "sans-serif" BOLD — strong, legible, classic flip-clock feel
            // Size: 56% of ch for main digit
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = if (isDark) Color.parseColor("#F0F2F5") else Color.parseColor("#101820")
                textSize  = H * 0.56f
                typeface  = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                // Strong drop-shadow — critical for classic flip-clock depth
                setShadowLayer(
                    H * 0.045f, 0f, H * 0.028f,
                    if (isDark) Color.parseColor("#88000000") else Color.parseColor("#30000000")
                )
            }
            val tb = Rect(); tp.getTextBounds(digit, 0, digit.length, tb)
            // Vertical centre — shift up slightly so digit sits in visual centre of tile
            cv.drawText(digit, W / 2f, mid + tb.height() / 2f - tb.bottom * 0.08f, tp)

            // ── 5. AM/PM superscript — top-right, inside tile ────────────────
            if (amPm.isNotEmpty()) {
                val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color    = if (isDark) Color.parseColor("#C8B8D0F0") else Color.parseColor("#804060A0")
                    textSize = H * 0.14f                            // 14% of tile height
                    typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
                    textAlign = Paint.Align.RIGHT
                    setShadowLayer(H * 0.02f, 0f, H * 0.01f,
                        if (isDark) Color.parseColor("#60000000") else Color.parseColor("#20000000"))
                }
                // Position: right-inset W*0.10 from right edge, top-inset H*0.10 from top
                val spY = H * 0.20f
                val spX = W - W * 0.10f
                // Draw small superscript background pill for legibility
                val pillH = H * 0.17f; val pillW = H * 0.28f
                val pillR = pillH / 2f
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (isDark) Color.parseColor("#50FFFFFF") else Color.parseColor("#60000030")
                }
                cv.drawRoundRect(
                    RectF(W - pillW - W * 0.06f, H * 0.07f, W - W * 0.06f, H * 0.07f + pillH),
                    pillR, pillR, bgPaint
                )
                cv.drawText(amPm, spX, spY, sp)
            }

            // ── 6. Fold crease ───────────────────────────────────────────────
            p.style = Paint.Style.STROKE
            val cs = (H * 0.013f).coerceAtLeast(1.2f)
            p.strokeWidth = cs
            p.color = if (isDark) Color.parseColor("#55000000") else Color.parseColor("#28000000")
            cv.drawLine(r, mid + cs * 0.5f, W - r, mid + cs * 0.5f, p)
            p.strokeWidth = 1.2f
            p.color = if (isDark) Color.parseColor("#48FFFFFF") else Color.parseColor("#72FFFFFF")
            cv.drawLine(r * 0.6f, mid - 0.6f, W - r * 0.6f, mid - 0.6f, p)
            p.style = Paint.Style.FILL

            // ── 7. Specular rim ──────────────────────────────────────────────
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.0f
            p.color = if (isDark) Color.parseColor("#40FFFFFF") else Color.parseColor("#70FFFFFF")
            cv.drawRoundRect(RectF(0.5f, 0.5f, W - 0.5f, H - 0.5f), r, r, p)
            p.style = Paint.Style.FILL

            return bmp
        }

        // ═════════════════════════════════════════════════════════════════════
        //  SEPARATOR — colon dots, classic style
        // ═════════════════════════════════════════════════════════════════════
        fun sep(isDark: Boolean, cw: Int = CW, ch: Int = CH): Bitmap {
            val w   = (cw * 0.30f).roundToInt().coerceAtLeast(10)
            val bmp = Bitmap.createBitmap(w, ch, Bitmap.Config.ARGB_8888)
            val cv  = Canvas(bmp)
            val rad = (w * 0.24f).coerceAtLeast(4f)

            for (cyF in listOf(ch * 0.36f, ch * 0.64f)) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                // Glow halo
                p.shader = RadialGradient(w / 2f, cyF, rad * 2.8f,
                    intArrayOf(
                        if (isDark) Color.parseColor("#35FFFFFF") else Color.parseColor("#40A0B8CC"),
                        Color.TRANSPARENT
                    ), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                cv.drawCircle(w / 2f, cyF, rad * 2.8f, p); p.shader = null
                // Core
                p.color = if (isDark) Color.parseColor("#D8C8C8D8") else Color.parseColor("#C83A4A5A")
                cv.drawCircle(w / 2f, cyF, rad, p)
                // Rim
                p.style = Paint.Style.STROKE; p.strokeWidth = 0.8f
                p.color = if (isDark) Color.parseColor("#50FFFFFF") else Color.parseColor("#65FFFFFF")
                cv.drawCircle(w / 2f, cyF, rad - 0.4f, p); p.style = Paint.Style.FILL
                // Specular fleck
                p.color = if (isDark) Color.parseColor("#70FFFFFF") else Color.parseColor("#88FFFFFF")
                cv.drawCircle(w / 2f - rad * 0.20f, cyF - rad * 0.24f, rad * 0.34f, p)
            }
            return bmp
        }

        // ═════════════════════════════════════════════════════════════════════
        //  DATE STRIP — bold, prominent bitmap label
        //
        //  Layout (left → right):
        //    [DAY_NAME]  [DD]  [MONTH_NAME]  [YYYY]
        //
        //  • Day name:  bold, accent colour (cyan/dark-blue)
        //  • DD:        large bold — most prominent element
        //  • Month:     medium weight
        //  • Year:      lighter, smaller
        //  • Drop shadow on all text for wallpaper visibility
        //  • Full-width bitmap so it stretches naturally in XML layout
        // ═════════════════════════════════════════════════════════════════════
        fun buildDateBitmap(
            dayName: String,
            dayNum: Int,
            monthName: String,
            year: Int,
            isDark: Boolean,
            widthPx: Int,
            density: Float
        ): Bitmap {
            val H    = (22f * density).roundToInt().coerceAtLeast(20) // strip height
            val bmp  = Bitmap.createBitmap(widthPx, H, Bitmap.Config.ARGB_8888)
            val cv   = Canvas(bmp)

            // Shadow paint helper
            fun makePaint(textSizePx: Float, color: Int, bold: Boolean = false,
                          shadowR: Float = H * 0.3f): Paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color    = color
                    this.textSize = textSizePx
                    typeface = if (bold) Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
                    else      Typeface.create("sans-serif-light", Typeface.NORMAL)
                    textAlign = Paint.Align.LEFT
                    setShadowLayer(shadowR, 0f, shadowR * 0.5f,
                        if (isDark) Color.parseColor("#C0000000") else Color.parseColor("#50000000"))
                }

            val accentColor  = if (isDark) Color.parseColor("#FF4FC3F7") else Color.parseColor("#FF1565C0")
            val primaryColor = if (isDark) Color.parseColor("#FFF0F2F5") else Color.parseColor("#FF101820")
            val muteColor    = if (isDark) Color.parseColor("#FFB0B8C8") else Color.parseColor("#FF4A5A6A")

            val szDay  = H * 0.80f   // Day name — bold, accent
            val szNum  = H * 0.92f   // Day number — largest
            val szMon  = H * 0.80f   // Month name — medium
            val szYr   = H * 0.68f   // Year — smaller, muted

            val pDay  = makePaint(szDay, accentColor,  bold = true)
            val pNum  = makePaint(szNum, primaryColor, bold = true)
            val pMon  = makePaint(szMon, primaryColor, bold = false)
            val pYr   = makePaint(szYr,  muteColor,    bold = false)

            // Measure widths for spacing
            val sep     = H * 0.30f    // gap between elements
            val baseline = H * 0.88f   // common baseline

            var x = 0f

            // Day name
            cv.drawText(dayName, x, baseline, pDay)
            x += pDay.measureText(dayName) + sep

            // Separator dot
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = muteColor; setShadowLayer(H * 0.2f, 0f, H * 0.1f,
                if (isDark) Color.parseColor("#A0000000") else Color.parseColor("#40000000"))
            }
            cv.drawCircle(x, baseline - H * 0.28f, H * 0.055f, dotPaint)
            x += H * 0.22f

            // Day number
            cv.drawText(dayNum.toString(), x, baseline, pNum)
            x += pNum.measureText(dayNum.toString()) + sep * 0.6f

            // Month name
            cv.drawText(monthName, x, baseline, pMon)
            x += pMon.measureText(monthName) + sep

            // Separator dot
            cv.drawCircle(x, baseline - H * 0.28f, H * 0.055f, dotPaint)
            x += H * 0.22f

            // Year
            cv.drawText(year.toString(), x, baseline, pYr)

            return bmp
        }
    }
}