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
            val week  = cal.get(Calendar.WEEK_OF_YEAR)
            val year  = cal.get(Calendar.YEAR)
            val total = if (isLeap(year)) 366 else 365
            val pct   = "%.1f".format(day.toFloat() / total * 100)
            val left  = total - day

            val opts = mgr.getAppWidgetOptions(id)
            val dens = ctx.resources.displayMetrics.density

            val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
                opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250))
                .coerceIn(80, 800)
            val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 200))
                .coerceIn(80, 800)

            val compact = wDp < 160 || hDp < 160

            val dotAreaWdp = (wDp - 20).coerceAtLeast(40)
            val dotAreaHdp = (hDp - 40).coerceAtLeast(20)

            val cols = bestCols(total, dotAreaWdp, dotAreaHdp)
            val rows = (total + cols - 1) / cols

            val dotAreaWpx = (dotAreaWdp * dens).toInt().coerceAtLeast(40)
            val dotAreaHpx = (dotAreaHdp * dens).toInt().coerceAtLeast(20)

            val cell = minOf(dotAreaWpx.toFloat() / cols, dotAreaHpx.toFloat() / rows)
            val bmpW = (cell * cols).toInt().coerceAtLeast(1)
            val bmpH = (cell * rows).toInt().coerceAtLeast(1)

            val bmp = buildDotBitmap(day, total, bmpW, bmpH, cols, rows)

            val subtitle = when {
                compact -> "$pct%  ·  $left days left"
                else    -> "$year  ·  Wk $week  ·  Day $day of $total  ·  $pct%  ·  $left days left"
            }

            val views = RemoteViews(ctx.packageName, R.layout.widget_year_progress)
            views.setTextViewText(R.id.tv_title, "Year Progress")
            views.setTextViewText(R.id.tv_subtitle, subtitle)
            views.setImageViewBitmap(R.id.iv_dots, bmp)
            mgr.updateAppWidget(id, views)
        }

        private fun bestCols(total: Int, areaW: Int, areaH: Int): Int {
            var best = 1; var bestDiff = Float.MAX_VALUE
            for (c in 1..60) {
                val r     = (total + c - 1) / c
                val cellW = areaW.toFloat() / c
                val cellH = areaH.toFloat() / r
                val diff  = Math.abs(cellW - cellH)
                if (diff < bestDiff) { bestDiff = diff; best = c }
            }
            return best
        }

        fun buildDotBitmap(
            dayOfYear: Int,
            totalDays: Int,
            bmpW: Int,
            bmpH: Int,
            cols: Int,
            rows: Int = (totalDays + cols - 1) / cols
        ): Bitmap {
            val cellX = bmpW.toFloat() / cols
            val cellY = bmpH.toFloat() / rows
            val r     = minOf(cellX, cellY) * 0.38f

            val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
            val cv  = Canvas(bmp)
            val p   = Paint(Paint.ANTI_ALIAS_FLAG)

            for (i in 1..totalDays) {
                val col = (i - 1) % cols
                val row = (i - 1) / cols
                val cx  = col * cellX + cellX / 2f
                val cy  = row * cellY + cellY / 2f

                when {
                    // ── Today dot — bright cyan core, strong glow ────────────
                    i == dayOfYear -> {
                        // Wide soft outer glow
                        p.shader = RadialGradient(cx, cy, r * 3.4f,
                            intArrayOf(Color.parseColor("#884FC3F7"), Color.TRANSPARENT),
                            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                        cv.drawCircle(cx, cy, r * 3.4f, p); p.shader = null

                        // Mid bloom
                        p.shader = RadialGradient(cx, cy, r * 1.7f,
                            intArrayOf(Color.parseColor("#CC4FC3F7"), Color.parseColor("#224FC3F7")),
                            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                        cv.drawCircle(cx, cy, r * 1.7f, p); p.shader = null

                        // Core dot — radial from near-white centre to cyan edge
                        p.shader = RadialGradient(cx - r * .2f, cy - r * .2f, r * 1.1f,
                            intArrayOf(Color.parseColor("#FFFFFFFF"), Color.parseColor("#FF4FC3F7")),
                            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                        cv.drawCircle(cx, cy, r, p); p.shader = null

                        // Bright specular fleck
                        p.color = Color.parseColor("#EEFFFFFF")
                        cv.drawCircle(cx - r * .28f, cy - r * .30f, r * .28f, p)
                    }

                    // ── Elapsed dots — vivid sweep + top-left shine ──────────
                    i < dayOfYear -> {
                        val ratio = i.toFloat() / totalDays
                        val base  = progressColor(ratio)

                        // Base dot
                        p.color = base
                        cv.drawCircle(cx, cy, r, p)

                        // Soft top-left highlight — makes dot look convex / lit
                        p.shader = RadialGradient(
                            cx - r * .30f, cy - r * .32f, r * .80f,
                            intArrayOf(Color.parseColor("#60FFFFFF"), Color.TRANSPARENT),
                            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                        cv.drawCircle(cx, cy, r, p); p.shader = null
                    }

                    // ── Future dots — slightly brighter, soft inner shadow ────
                    else -> {
                        // Base
                        p.color = Color.parseColor("#60404040")
                        cv.drawCircle(cx, cy, r, p)

                        // Very faint inner shadow at bottom-right (unlit look)
                        p.shader = RadialGradient(
                            cx + r * .20f, cy + r * .22f, r,
                            intArrayOf(Color.parseColor("#25000000"), Color.TRANSPARENT),
                            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                        cv.drawCircle(cx, cy, r, p); p.shader = null
                    }
                }
            }
            return bmp
        }

        /** Preview used in MainActivity */
        fun buildPreviewBitmap(dayOfYear: Int, totalDays: Int, widthPx: Int): Bitmap {
            val heightPx = (widthPx * 0.55f).toInt()
            val cols     = bestCols(totalDays, widthPx, heightPx)
            val rows     = (totalDays + cols - 1) / cols
            val cell     = minOf(widthPx.toFloat() / cols, heightPx.toFloat() / rows)
            val bmpW     = (cell * cols).toInt().coerceAtLeast(1)
            val bmpH     = (cell * rows).toInt().coerceAtLeast(1)
            return buildDotBitmap(dayOfYear, totalDays, bmpW, bmpH, cols, rows)
        }

        /** HSV sweep: green → yellow-green → amber → orange */
        fun progressColor(ratio: Float): Int =
            Color.HSVToColor(floatArrayOf(ratio * 120f, 0.88f, 0.95f))
    }
}