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

    companion object {

        const val CW = 120   // default for preview use
        const val CH = 158

        fun startTicker(ctx: Context) {
            try { ctx.startService(Intent(ctx, TickerService::class.java)) } catch (_: Exception) {}
        }

        fun pushFrame(ctx: Context, mgr: AppWidgetManager, id: Int) {
            try {
                val prefs  = ctx.getSharedPreferences("flip_prefs", Context.MODE_PRIVATE)
                val isDark = prefs.getBoolean("dark_$id", true)
                val cal    = Calendar.getInstance()
                val h = cal.get(Calendar.HOUR_OF_DAY)
                val m = cal.get(Calendar.MINUTE)
                val s = cal.get(Calendar.SECOND)
                val digits = intArrayOf(h/10, h%10, m/10, m%10, s/10, s%10)
                val vids   = intArrayOf(R.id.iv_h1,R.id.iv_h2,R.id.iv_m1,R.id.iv_m2,R.id.iv_s1,R.id.iv_s2)

                // ── Dynamic card sizing ───────────────────────────────────────────
                // Row = 6 cards + 2 seps (0.28×cw) + 7 gaps (2dp each) + side pads (16dp)
                // → total_dp = (6 + 0.56)×cw_dp + 14dp + 16dp  ⟹  cw_dp = (wDp - 30) / 6.56
                // We reserve an EXTRA 12 dp safety margin so the last card is never squeezed.
                val opts  = mgr.getAppWidgetOptions(id)
                val wDp   = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280)
                val dens  = ctx.resources.displayMetrics.density
                val cw    = ((wDp - 42) * dens / 6.56f).roundToInt().coerceIn(44, 220)
                val ch    = (cw * 1.36f).roundToInt()

                val views = RemoteViews(ctx.packageName, R.layout.widget_flip_clock)
                for (i in 0..5) views.setImageViewBitmap(vids[i], card(digits[i].toString(), isDark, cw, ch))
                views.setImageViewBitmap(R.id.iv_sep1, sep(isDark, cw, ch))
                views.setImageViewBitmap(R.id.iv_sep2, sep(isDark, cw, ch))

                // Date: bottom-left positioning handled by XML layout_gravity=start
                val D = arrayOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")
                val M = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
                val dateStr = "${D[cal.get(Calendar.DAY_OF_WEEK)-1]}, " +
                        "${cal.get(Calendar.DAY_OF_MONTH)} ${M[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"
                views.setTextViewText(R.id.tv_date, dateStr)
                // High-opacity for both dark and light — #CC = 80% opaque
                views.setTextColor(R.id.tv_date,
                    if (isDark) Color.parseColor("#FFFFFFFF") else Color.parseColor("#CC3A4A5A"))

                mgr.updateAppWidget(id, views)
            } catch (_: Exception) {}
        }

        // ═══════════════════════════════════════════════════════════════════
        //  CARD — Deep Liquid Glass with Physical Reflection Stack
        // ═══════════════════════════════════════════════════════════════════
        /**
         * Six-layer glass simulation. No black tile — fully transparent bg.
         * Adapts: dark mode = smoked glass, light mode = frosted crystal.
         *
         * Layer stack (back → front):
         *   1. Glass body      — translucent pill, top/bottom split
         *   2. Top shimmer     — surface reflection (LinearGradient, white fade)
         *   3. Left bevel      — edge refraction glow
         *   4. Depth gradient  — bottom-right darkening (adds physical depth)
         *   5. Radial sheen    — off-centre metallic specular highlight
         *   6. Digit           — bold, generous shadow for lift
         *   7. Fold crease     — shadow below + specular line above midpoint
         *   8. Rim             — 1px specular ring, brighter on top quarter
         *
         * Squeeze fix:
         *   • textSize = ch * 0.54f  — always fits single digit in any card size
         *   • Digit drawn at exactly (cw/2, mid + measuredHeight/2) — pixel-perfect
         *   • cornerRadius = min(cw*0.20, ch*0.15) — never eats digit space
         *   • Bitmap is cw×ch exactly — XML wrap_content drives layout size
         */
        fun card(digit: String, isDark: Boolean, cw: Int = CW, ch: Int = CH): Bitmap {
            val bmp  = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
            val cv   = Canvas(bmp)
            val r    = min(cw * 0.20f, ch * 0.15f)
            val mid  = ch / 2f
            val W    = cw.toFloat()
            val H    = ch.toFloat()
            val full = RectF(0f, 0f, W, H)
            val p    = Paint(Paint.ANTI_ALIAS_FLAG)

            // ── 1. Glass body — split pill ────────────────────────────────────
            // Dark mode: smoked blue-black glass — higher opacity for visibility
            // Light mode: clear crystal — bright, airy
            p.color = if (isDark) Color.parseColor("#E5080808") else Color.parseColor("#E0EAF2FC")
            cv.save(); cv.clipRect(0f, 0f, W, mid); cv.drawRoundRect(full, r, r, p); cv.restore()

            p.color = if (isDark) Color.parseColor("#F0030305") else Color.parseColor("#C8D4ECF8")
            cv.save(); cv.clipRect(0f, mid, W, H); cv.drawRoundRect(full, r, r, p); cv.restore()

            // ── 2. Top surface shimmer ────────────────────────────────────────
            p.shader = LinearGradient(0f, 0f, 0f, mid * 0.80f,
                intArrayOf(
                    if (isDark) Color.parseColor("#40FFFFFF") else Color.parseColor("#80FFFFFF"),
                    Color.TRANSPARENT
                ), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            cv.save(); cv.clipRect(0f, 0f, W, mid); cv.drawRoundRect(full, r, r, p); cv.restore()
            p.shader = null

            // ── 3. Left-edge bevel (glass thickness refraction) ───────────────
            p.shader = LinearGradient(0f, 0f, W * 0.28f, 0f,
                intArrayOf(
                    if (isDark) Color.parseColor("#28FFFFFF") else Color.parseColor("#35FFFFFF"),
                    Color.TRANSPARENT
                ), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            cv.drawRoundRect(full, r, r, p); p.shader = null

            // ── 4. Bottom-right depth gradient (adds 3D depth) ────────────────
            p.shader = LinearGradient(W * 0.40f, mid, W, H,
                intArrayOf(Color.TRANSPARENT,
                    if (isDark) Color.parseColor("#30000000") else Color.parseColor("#18000000")
                ), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            cv.save(); cv.clipRect(0f, mid, W, H); cv.drawRoundRect(full, r, r, p); cv.restore()
            p.shader = null

            // ── 5. Radial metallic sheen ──────────────────────────────────────
            p.shader = RadialGradient(W * 0.28f, H * 0.12f, W * 0.70f,
                intArrayOf(
                    if (isDark) Color.parseColor("#28FFFFFF") else Color.parseColor("#38FFFFFF"),
                    Color.TRANSPARENT
                ), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            cv.drawRoundRect(full, r, r, p); p.shader = null

            // ── 6. Digit ──────────────────────────────────────────────────────
            // textSize = 54% of ch: always fits within the card without squeeze
            // Uses getTextBounds for exact vertical centering
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = if (isDark) Color.parseColor("#F4F4F8") else Color.parseColor("#0C1820")
                textSize  = H * 0.54f
                typeface  = Typeface.create("sans-serif-thin", Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                // Stronger shadow for lift-off-glass effect
                setShadowLayer(H * 0.035f, 0f, H * 0.020f,
                    if (isDark) Color.parseColor("#70000000") else Color.parseColor("#22000000"))
            }
            val tb = Rect()
            tp.getTextBounds(digit, 0, digit.length, tb)
            // Pixel-perfect vertical centre — tb.height() is the actual glyph height
            cv.drawText(digit, W / 2f, mid + tb.height() / 2f, tp)

            // ── 7. Fold crease ────────────────────────────────────────────────
            p.style = Paint.Style.STROKE
            val cs = (H * 0.010f).coerceAtLeast(1f)
            p.strokeWidth = cs
            p.color = if (isDark) Color.parseColor("#40000000") else Color.parseColor("#20000000")
            cv.drawLine(r, mid + cs * 0.5f, W - r, mid + cs * 0.5f, p)
            p.strokeWidth = 1.0f
            p.color = if (isDark) Color.parseColor("#45FFFFFF") else Color.parseColor("#70FFFFFF")
            cv.drawLine(r * 0.7f, mid - 0.5f, W - r * 0.7f, mid - 0.5f, p)
            p.style = Paint.Style.FILL

            // ── 8. Specular rim ───────────────────────────────────────────────
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.0f
            p.color = if (isDark) Color.parseColor("#48FFFFFF") else Color.parseColor("#78FFFFFF")
            cv.drawRoundRect(RectF(0.5f, 0.5f, W - 0.5f, H - 0.5f), r, r, p)
            p.style = Paint.Style.FILL

            return bmp
        }

        // ═══════════════════════════════════════════════════════════════════
        //  SEPARATOR — Glass orb dots
        // ═══════════════════════════════════════════════════════════════════
        /**
         * Two glass orb dots. Transparent bg. Each orb:
         *  • Outer halo glow (RadialGradient)
         *  • Core dot (solid, 80%+ opacity)
         *  • Inner rim (1px specular ring)
         *  • Specular fleck (top-left quarter sphere)
         */
        fun sep(isDark: Boolean, cw: Int = CW, ch: Int = CH): Bitmap {
            val w   = (cw * 0.28f).roundToInt().coerceAtLeast(10)
            val bmp = Bitmap.createBitmap(w, ch, Bitmap.Config.ARGB_8888)
            val cv  = Canvas(bmp)
            val rad = (w * 0.26f).coerceAtLeast(4f)
            val dotAlpha = if (isDark) "#DCC8C8D4" else "#D83A4A5A"

            for (cyF in listOf(ch * 0.36f, ch * 0.64f)) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                // Halo
                p.shader = RadialGradient(w / 2f, cyF, rad * 3.0f,
                    intArrayOf(
                        if (isDark) Color.parseColor("#30FFFFFF") else Color.parseColor("#38A0CCEE"),
                        Color.TRANSPARENT
                    ), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                cv.drawCircle(w / 2f, cyF, rad * 3.0f, p); p.shader = null
                // Core
                p.color = Color.parseColor(dotAlpha)
                cv.drawCircle(w / 2f, cyF, rad, p)
                // Rim
                p.style = Paint.Style.STROKE; p.strokeWidth = 0.8f
                p.color = if (isDark) Color.parseColor("#55FFFFFF") else Color.parseColor("#70FFFFFF")
                cv.drawCircle(w / 2f, cyF, rad - 0.4f, p); p.style = Paint.Style.FILL
                // Specular fleck
                p.color = if (isDark) Color.parseColor("#78FFFFFF") else Color.parseColor("#90FFFFFF")
                cv.drawCircle(w / 2f - rad * 0.20f, cyF - rad * 0.24f, rad * 0.36f, p)
            }
            return bmp
        }
    }
}