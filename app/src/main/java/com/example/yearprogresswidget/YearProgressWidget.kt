package com.example.yearprogresswidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.graphics.*
import android.widget.RemoteViews
import java.util.Calendar

class YearProgressWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) update(ctx, mgr, id)
    }

    override fun onAppWidgetOptionsChanged(
        ctx: Context, mgr: AppWidgetManager, id: Int, opts: android.os.Bundle
    ) { update(ctx, mgr, id) }

    companion object {

        private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || (y % 400 == 0)

        fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val cal   = Calendar.getInstance()
            val day   = cal.get(Calendar.DAY_OF_YEAR)
            val year  = cal.get(Calendar.YEAR)
            val total = if (isLeap(year)) 366 else 365
            val pct   = "%.1f".format(day.toFloat() / total * 100)

            val opts = mgr.getAppWidgetOptions(id)
            val wDp  = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,  250)
            val hDp  = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
            val dens = ctx.resources.displayMetrics.density

            val views = RemoteViews(ctx.packageName, R.layout.widget_year_progress)
            views.setTextViewText(R.id.tv_title,    "Year Progress")
            views.setTextViewText(R.id.tv_subtitle, "$year  ·  Day $day of $total  ·  $pct%")
            views.setImageViewBitmap(R.id.iv_dots,  buildBitmap(day, total, wDp, hDp, dens))
            mgr.updateAppWidget(id, views)
        }

        /**
         * Dot grid — TRANSPARENT bitmap floating over deep black widget_background.
         *
         * On pure black, we want:
         *  Elapsed  → vivid saturated progress dots (pop strongly against black)
         *  Today    → #4FC3F7 cyan with large bright glow halo
         *  Future   → subtle dark-grey dots, barely visible (gives depth)
         *
         * Opacity tuning for black bg:
         *  • Elapsed dots: full opacity (0xFF), high value (0.88) → vivid on black
         *  • Today glow:   large radius (r×3.0), high alpha (#70) → real iOS glow
         *  • Future dots:  #383838 — just enough to read the grid, not distracting
         */
        fun buildBitmap(
            dayOfYear: Int,
            totalDays: Int,
            widgetWdp: Int,
            widgetHdp: Int,
            density: Float
        ): Bitmap {
            val COLS   = 25
            val PAD_DP = 14
            val bmpW   = ((widgetWdp - PAD_DP * 2) * density).toInt().coerceAtLeast(50)
            val step   = bmpW.toFloat() / COLS
            val dot    = step * 0.70f
            val r      = dot / 2f
            val rows   = (totalDays + COLS - 1) / COLS
            val bmpH   = (rows * step).toInt().coerceAtLeast(1)

            // Transparent — no background fill; black comes from widget_background.xml
            val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
            val cv  = Canvas(bmp)
            val p   = Paint(Paint.ANTI_ALIAS_FLAG)

            for (i in 1..totalDays) {
                val col = (i - 1) % COLS
                val row = (i - 1) / COLS
                val cx  = col * step + step / 2f
                val cy  = row * step + step / 2f

                when {
                    i == dayOfYear -> {
                        // Outer glow — large, bright, iOS-style
                        p.shader = RadialGradient(cx, cy, r * 3.0f,
                            intArrayOf(Color.parseColor("#704FC3F7"), Color.TRANSPARENT),
                            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                        cv.drawCircle(cx, cy, r * 3.0f, p)
                        p.shader = null
                        // Inner glow ring
                        p.shader = RadialGradient(cx, cy, r * 1.6f,
                            intArrayOf(Color.parseColor("#A04FC3F7"), Color.parseColor("#204FC3F7")),
                            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                        cv.drawCircle(cx, cy, r * 1.6f, p)
                        p.shader = null
                        // Core dot
                        p.color = Color.parseColor("#4FC3F7")
                        cv.drawCircle(cx, cy, r, p)
                        // Specular fleck
                        p.color = Color.parseColor("#C0FFFFFF")
                        cv.drawCircle(cx - r * 0.22f, cy - r * 0.26f, r * 0.32f, p)
                    }
                    i < dayOfYear -> {
                        // Vivid progress dots — full opacity on black
                        p.color = progressColor(i.toFloat() / totalDays)
                        cv.drawCircle(cx, cy, r, p)
                    }
                    else -> {
                        // Future: very subtle dark grey — legible grid, not competing
                        p.color = Color.parseColor("#50383838")
                        cv.drawCircle(cx, cy, r, p)
                    }
                }
            }
            return bmp
        }

        /**
         * Preview version (in-app) — slight frosted tint behind dots
         * so they read on any MainActivity background.
         */
        fun buildPreviewBitmap(dayOfYear: Int, totalDays: Int, widthPx: Int): Bitmap {
            val COLS = 25
            val step = widthPx.toFloat() / COLS
            val r    = step * 0.70f / 2f
            val rows = (totalDays + COLS - 1) / COLS
            val bmpH = (rows * step).toInt().coerceAtLeast(1)

            val bmp  = Bitmap.createBitmap(widthPx, bmpH, Bitmap.Config.ARGB_8888)
            val cv   = Canvas(bmp)

            // Very subtle tint for preview context (deep dark, not harsh black)
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A0A0A0E") }
            cv.drawRoundRect(RectF(0f, 0f, widthPx.toFloat(), bmpH.toFloat()), r * 1.4f, r * 1.4f, bg)

            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            for (i in 1..totalDays) {
                val cx = (i - 1) % COLS * step + step / 2f
                val cy = (i - 1) / COLS * step + step / 2f
                when {
                    i == dayOfYear -> {
                        p.shader = RadialGradient(cx, cy, r * 3.0f,
                            intArrayOf(Color.parseColor("#604FC3F7"), Color.TRANSPARENT),
                            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                        cv.drawCircle(cx, cy, r * 3.0f, p); p.shader = null
                        p.color = Color.parseColor("#4FC3F7")
                        cv.drawCircle(cx, cy, r, p)
                        p.color = Color.parseColor("#B0FFFFFF")
                        cv.drawCircle(cx - r * 0.22f, cy - r * 0.26f, r * 0.32f, p)
                    }
                    i < dayOfYear -> {
                        p.color = progressColor(i.toFloat() / totalDays)
                        cv.drawCircle(cx, cy, r, p)
                    }
                    else -> {
                        p.color = Color.parseColor("#40383838")
                        cv.drawCircle(cx, cy, r, p)
                    }
                }
            }
            return bmp
        }

        /** Vivid HSV sweep: green → yellow-green → amber → orange */
        private fun progressColor(ratio: Float) =
            Color.HSVToColor(floatArrayOf(ratio * 120f, 0.90f, 0.92f))
    }
}