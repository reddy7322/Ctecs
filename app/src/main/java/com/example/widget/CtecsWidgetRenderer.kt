package com.example.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.example.model.CtecsWidgetInstanceConfig
import com.example.model.CtecsWidgetType
import com.example.model.GeoPoint
import com.example.model.MiniMapCornerShape
import com.example.model.MiniMapDirectionStyle
import com.example.model.MiniMapStyle
import com.example.model.MiniMapStyleConfig
import com.example.model.RouteData
import com.example.model.WidgetBackgroundStyle
import com.example.model.WidgetSizeTier
import com.example.model.WidgetThemeMode
import com.example.navigation.LockScreenNavUiState
import com.example.overlay.MiniMapRenderer
import kotlin.math.max
import kotlin.math.min

data class WidgetRenderResult(
    val bitmap: Bitmap,
    val sizeTier: WidgetSizeTier,
    val fittedChainageSp: Float,
    val isDarkResolved: Boolean,
    val displayedChainage: String,
    val displayedDirection: String,
    val displayedSpeed: String,
    val displayedNextKm: String,
    val displayedDistance: String,
    val displayedStatus: String
)

/**
 * Pure Minimalist Flat UI renderer for CTECS Android Home-Screen Widgets.
 *
 * Design principles:
 * - Clean, modern Material-inspired Flat UI.
 * - Zero glass effects, bulky cards, heavy borders, glow effects, or drop shadows.
 * - Simple flat surfaces with 1dp subtle separators and restrained accent colors.
 * - Dynamic chainage font sizing & responsive layout switching (Compact / Medium / Large)
 *   so text never clips or overlaps at any launcher widget size.
 */
object CtecsWidgetRenderer {

    fun resolveSizeTier(widthDp: Int, heightDp: Int): WidgetSizeTier {
        return when {
            widthDp < 210 || heightDp < 84 -> WidgetSizeTier.COMPACT
            widthDp >= 300 && heightDp >= 155 -> WidgetSizeTier.LARGE
            else -> WidgetSizeTier.MEDIUM
        }
    }

    fun resolveIsDark(context: Context, themeMode: WidgetThemeMode): Boolean {
        return when (themeMode) {
            WidgetThemeMode.DARK -> true
            WidgetThemeMode.LIGHT -> false
            WidgetThemeMode.SYSTEM -> {
                val nightFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightFlags == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    fun computeFittedTextSizePx(
        text: String,
        paint: Paint,
        maxWidthPx: Float,
        maxHeightPx: Float,
        preferredMaxPx: Float,
        minPx: Float
    ): Float {
        if (maxWidthPx <= 8f || maxHeightPx <= 8f) return minPx
        val template = text.map { if (it.isDigit()) '8' else it }.joinToString("").ifBlank { "888+888" }
        var size = preferredMaxPx.coerceAtLeast(minPx)
        val safeW = maxWidthPx * 0.94f
        val safeH = maxHeightPx * 0.92f
        while (size > minPx) {
            paint.textSize = size
            val measuredW = paint.measureText(template)
            val fm = paint.fontMetrics
            val measuredH = fm.descent - fm.ascent
            if (measuredW <= safeW && measuredH <= safeH) {
                return size
            }
            size -= 1.5f
        }
        paint.textSize = minPx
        return minPx
    }

    fun renderWidgetBitmap(
        context: Context,
        navState: LockScreenNavUiState,
        instanceConfig: CtecsWidgetInstanceConfig,
        widthDp: Int,
        heightDp: Int,
        location: GeoPoint? = null,
        bearing: Float? = null,
        routeData: RouteData? = null,
        isTrackingActive: Boolean = false
    ): WidgetRenderResult {
        val density = context.resources.displayMetrics.density.coerceIn(1.5f, 3.0f)
        val safeWidthDp = widthDp.coerceIn(140, 560)
        val safeHeightDp = heightDp.coerceIn(52, 420)
        val widthPx = (safeWidthDp * density).toInt().coerceIn(220, 1440)
        val heightPx = (safeHeightDp * density).toInt().coerceIn(84, 1080)

        val sizeTier = resolveSizeTier(safeWidthDp, safeHeightDp)
        val isDark = resolveIsDark(context, instanceConfig.themeMode)

        // Flat UI palette
        val baseBgColor = if (isDark) Color.rgb(15, 23, 42) else Color.rgb(248, 250, 252)
        val secondaryTileColor = if (isDark) Color.rgb(23, 33, 53) else Color.rgb(238, 242, 246)
        val separatorColor = if (isDark) Color.rgb(38, 50, 72) else Color.rgb(218, 226, 236)
        val primaryTextColor = if (isDark) Color.rgb(248, 250, 252) else Color.rgb(15, 23, 42)
        val secondaryTextColor = if (isDark) Color.rgb(148, 163, 184) else Color.rgb(100, 116, 139)
        val accentColor = if (isDark) {
            instanceConfig.accentColor.darkColorInt
        } else {
            instanceConfig.accentColor.lightColorInt
        }
        val sideLhsColor = if (isDark) Color.rgb(52, 211, 153) else Color.rgb(5, 150, 105)
        val sideRhsColor = if (isDark) Color.rgb(56, 189, 248) else Color.rgb(2, 132, 199)

        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val w = widthPx.toFloat()
        val h = heightPx.toFloat()
        val cornerPx = 14f * density

        // 1. Flat background surface (Solid, Muted Flat, or Transparent)
        val bgAlpha = (instanceConfig.backgroundStyle.alphaFraction * 255f).toInt().coerceIn(0, 255)
        if (bgAlpha > 0) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.argb(
                    bgAlpha,
                    Color.red(baseBgColor),
                    Color.green(baseBgColor),
                    Color.blue(baseBgColor)
                )
            }
            canvas.drawRoundRect(RectF(0f, 0f, w, h), cornerPx, cornerPx, bgPaint)
        }

        val sepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, 1f * density)
            color = separatorColor
        }

        // Resolve telemetry strings with graceful GPS/route fallbacks
        val rawChainage = navState.chainageText.ifBlank { "--+---" }
        val displayChainage = if (instanceConfig.showChainage) rawChainage else "HIDDEN"
        val sideStr = if (instanceConfig.showRoadSide && !navState.reliableSide.isNullOrBlank()) {
            "${navState.reliableSide} ${navState.directionArrow}".trim()
        } else {
            ""
        }
        val speedStr = if (instanceConfig.showSpeed) {
            if (navState.isLiveGpsValid) {
                "${navState.speedKmh ?: 0} km/h"
            } else {
                "-- km/h"
            }
        } else {
            ""
        }
        val nextKmStr = if (instanceConfig.showNextKmMarker) {
            navState.nextMarkerLabel ?: if (navState.hasRouteData) "Next KM --+---" else "No KMZ Loaded"
        } else {
            ""
        }
        val distStr = if (instanceConfig.showDistanceRemaining) {
            navState.distanceToNextMetres?.let { "$it m" } ?: navState.distanceToNextText.orEmpty()
        } else {
            ""
        }
        val gpsStatusStr = when {
            navState.isLiveGpsValid -> {
                val timePart = navState.currentTimeText.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()
                "GPS Live$timePart"
            }
            !navState.lastUpdateStatusText.isNullOrBlank() -> navState.lastUpdateStatusText
            else -> "GPS unavailable"
        }

        val fittedSp = when (instanceConfig.widgetType) {
            CtecsWidgetType.COMPACT -> renderCompactLayout(
                canvas = canvas,
                w = w,
                h = h,
                density = density,
                sizeTier = sizeTier,
                chainage = rawChainage,
                showChainage = instanceConfig.showChainage,
                sideStr = sideStr,
                isLhs = navState.reliableSide == "LHS",
                speedStr = speedStr,
                gpsStatusStr = gpsStatusStr,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                accentColor = accentColor,
                sideColor = if (navState.reliableSide == "LHS") sideLhsColor else sideRhsColor,
                tileBgColor = secondaryTileColor,
                sepPaint = sepPaint
            )
            CtecsWidgetType.NAVIGATION -> renderNavigationLayout(
                canvas = canvas,
                w = w,
                h = h,
                density = density,
                sizeTier = sizeTier,
                chainage = rawChainage,
                showChainage = instanceConfig.showChainage,
                sideStr = sideStr,
                speedStr = speedStr,
                nextKmStr = nextKmStr,
                distStr = distStr,
                gpsStatusStr = gpsStatusStr,
                isGpsValid = navState.isLiveGpsValid,
                isTrackingActive = isTrackingActive,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                accentColor = accentColor,
                sideColor = if (navState.reliableSide == "LHS") sideLhsColor else sideRhsColor,
                tileBgColor = secondaryTileColor,
                sepPaint = sepPaint
            )
            CtecsWidgetType.MINI_MAP -> renderMiniMapLayout(
                context = context,
                canvas = canvas,
                w = w,
                h = h,
                density = density,
                sizeTier = sizeTier,
                isDark = isDark,
                instanceConfig = instanceConfig,
                location = location,
                bearing = bearing,
                routeData = routeData,
                chainage = rawChainage,
                sideStr = sideStr,
                speedStr = speedStr,
                gpsStatusStr = gpsStatusStr,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                accentColor = accentColor,
                sideColor = if (navState.reliableSide == "LHS") sideLhsColor else sideRhsColor,
                tileBgColor = secondaryTileColor,
                sepPaint = sepPaint
            )
            CtecsWidgetType.DASHBOARD -> renderDashboardLayout(
                context = context,
                canvas = canvas,
                w = w,
                h = h,
                density = density,
                sizeTier = sizeTier,
                isDark = isDark,
                instanceConfig = instanceConfig,
                navState = navState,
                location = location,
                bearing = bearing,
                routeData = routeData,
                chainage = rawChainage,
                sideStr = sideStr,
                speedStr = speedStr,
                nextKmStr = nextKmStr,
                distStr = distStr,
                gpsStatusStr = gpsStatusStr,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                accentColor = accentColor,
                sideColor = if (navState.reliableSide == "LHS") sideLhsColor else sideRhsColor,
                tileBgColor = secondaryTileColor,
                sepPaint = sepPaint
            )
        }

        return WidgetRenderResult(
            bitmap = bmp,
            sizeTier = sizeTier,
            fittedChainageSp = fittedSp,
            isDarkResolved = isDark,
            displayedChainage = displayChainage,
            displayedDirection = sideStr,
            displayedSpeed = speedStr,
            displayedNextKm = nextKmStr,
            displayedDistance = distStr,
            displayedStatus = gpsStatusStr
        )
    }

    private fun renderCompactLayout(
        canvas: Canvas,
        w: Float,
        h: Float,
        density: Float,
        sizeTier: WidgetSizeTier,
        chainage: String,
        showChainage: Boolean,
        sideStr: String,
        isLhs: Boolean,
        speedStr: String,
        gpsStatusStr: String,
        primaryTextColor: Int,
        secondaryTextColor: Int,
        accentColor: Int,
        sideColor: Int,
        tileBgColor: Int,
        sepPaint: Paint
    ): Float {
        val padH = 12f * density
        val padV = 8f * density
        val headerH = 16f * density

        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 9.5f * density
            color = accentColor
            letterSpacing = 0.08f
        }
        // Minimal CTECS branding top-left + refresh indicator top-right
        canvas.drawText("CTECS", padH, padV + 9f * density, labelPaint)

        val statusPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 9f * density
            color = secondaryTextColor
            textAlign = Paint.Align.RIGHT
        }
        drawFlatRefreshGlyph(canvas, w - padH - 5f * density, padV + 5f * density, 4.5f * density, secondaryTextColor, density)
        if (w > 210f * density) {
            val shortStatus = TextUtils.ellipsize(gpsStatusStr, statusPaint, w * 0.48f, TextUtils.TruncateAt.END).toString()
            canvas.drawText(shortStatus, w - padH - 14f * density, padV + 9f * density, statusPaint)
        }

        val bodyTop = padV + headerH
        val bodyBottom = h - padV
        val bodyH = (bodyBottom - bodyTop).coerceAtLeast(24f * density)

        // Right column for Side + Speed pill
        val rightReservedW = when {
            sideStr.isNotEmpty() && speedStr.isNotEmpty() -> min(w * 0.42f, 118f * density)
            sideStr.isNotEmpty() || speedStr.isNotEmpty() -> min(w * 0.34f, 88f * density)
            else -> 0f
        }

        if (rightReservedW > 0f) {
            val sepX = w - padH - rightReservedW
            canvas.drawLine(sepX, bodyTop + 2f * density, sepX, bodyBottom - 2f * density, sepPaint)

            val rightCenterX = sepX + (w - padH - sepX) / 2f
            if (sideStr.isNotEmpty() && speedStr.isNotEmpty()) {
                val sidePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    textSize = 11.5f * density
                    color = sideColor
                    textAlign = Paint.Align.CENTER
                }
                val speedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    textSize = 12f * density
                    color = primaryTextColor
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(sideStr, rightCenterX, bodyTop + bodyH * 0.42f, sidePaint)
                canvas.drawText(speedStr, rightCenterX, bodyTop + bodyH * 0.84f, speedPaint)
            } else {
                val singleText = sideStr.ifEmpty { speedStr }
                val singlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    textSize = 13f * density
                    color = if (sideStr.isNotEmpty()) sideColor else primaryTextColor
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(singleText, rightCenterX, bodyTop + bodyH * 0.65f, singlePaint)
            }
        }

        val chainageMaxW = (w - padH * 2f - rightReservedW - (if (rightReservedW > 0f) 10f * density else 0f))
            .coerceAtLeast(60f * density)
        val chainagePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            color = primaryTextColor
        }
        val preferredMaxPx = when (sizeTier) {
            WidgetSizeTier.COMPACT -> 24f * density
            WidgetSizeTier.MEDIUM -> 30f * density
            WidgetSizeTier.LARGE -> 36f * density
        }
        val fittedPx = computeFittedTextSizePx(
            text = if (showChainage) chainage else "CTECS",
            paint = chainagePaint,
            maxWidthPx = chainageMaxW,
            maxHeightPx = bodyH,
            preferredMaxPx = preferredMaxPx,
            minPx = 14f * density
        )
        val fm = chainagePaint.fontMetrics
        val baselineY = bodyTop + (bodyH - (fm.descent - fm.ascent)) / 2f - fm.ascent
        canvas.drawText(if (showChainage) chainage else "CTECS READY", padH, baselineY, chainagePaint)

        return fittedPx / density
    }

    private fun renderNavigationLayout(
        canvas: Canvas,
        w: Float,
        h: Float,
        density: Float,
        sizeTier: WidgetSizeTier,
        chainage: String,
        showChainage: Boolean,
        sideStr: String,
        speedStr: String,
        nextKmStr: String,
        distStr: String,
        gpsStatusStr: String,
        isGpsValid: Boolean,
        isTrackingActive: Boolean,
        primaryTextColor: Int,
        secondaryTextColor: Int,
        accentColor: Int,
        sideColor: Int,
        tileBgColor: Int,
        sepPaint: Paint
    ): Float {
        val pad = 12f * density
        val headerH = 20f * density

        // Header: CTECS NAVIGATION + GPS status + Tracking pill + Refresh glyph
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 9.5f * density
            color = accentColor
            letterSpacing = 0.06f
        }
        canvas.drawText("CTECS NAVIGATION", pad, pad + 10f * density, headerPaint)

        // Small status dot + status text
        val dotColor = if (isGpsValid) Color.rgb(16, 185, 129) else Color.rgb(245, 158, 11)
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = dotColor
        }
        val rightEdge = w - pad
        drawFlatRefreshGlyph(canvas, rightEdge - 5f * density, pad + 6f * density, 4.5f * density, secondaryTextColor, density)

        val statusPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 9f * density
            color = secondaryTextColor
            textAlign = Paint.Align.RIGHT
        }
        val statusLabel = if (isTrackingActive) "LIVE • $gpsStatusStr" else gpsStatusStr
        val clippedStatus = TextUtils.ellipsize(statusLabel, statusPaint, w * 0.48f, TextUtils.TruncateAt.END).toString()
        canvas.drawText(clippedStatus, rightEdge - 15f * density, pad + 10f * density, statusPaint)
        val statusW = statusPaint.measureText(clippedStatus)
        canvas.drawCircle(rightEdge - 20f * density - statusW, pad + 6.5f * density, 3f * density, dotPaint)

        val sepY1 = pad + headerH
        canvas.drawLine(pad, sepY1, w - pad, sepY1, sepPaint)

        // Footer height for Next KM + Distance + Speed
        val footerH = if (sizeTier == WidgetSizeTier.COMPACT) 24f * density else 32f * density
        val sepY2 = h - pad - footerH
        canvas.drawLine(pad, sepY2, w - pad, sepY2, sepPaint)

        // Central Hero Row: Chainage + LHS/RHS flat badge
        val centerTop = sepY1 + 4f * density
        val centerH = (sepY2 - centerTop - 4f * density).coerceAtLeast(24f * density)

        val badgeW = if (sideStr.isNotEmpty()) 68f * density else 0f
        if (sideStr.isNotEmpty()) {
            val pillRect = RectF(
                w - pad - badgeW,
                centerTop + (centerH - 24f * density).coerceAtLeast(0f) / 2f,
                w - pad,
                centerTop + (centerH + 24f * density).coerceAtMost(centerH) / 2f
            )
            val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = tileBgColor
            }
            canvas.drawRoundRect(pillRect, 6f * density, 6f * density, pillBg)
            val sidePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = 11.5f * density
                color = sideColor
                textAlign = Paint.Align.CENTER
            }
            val fmS = sidePaint.fontMetrics
            val sy = pillRect.centerY() - (fmS.descent + fmS.ascent) / 2f
            canvas.drawText(sideStr, pillRect.centerX(), sy, sidePaint)
        }

        val availChainageW = (w - pad * 2f - badgeW - (if (badgeW > 0f) 10f * density else 0f))
            .coerceAtLeast(70f * density)
        val chainagePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            color = primaryTextColor
        }
        val preferredSp = when (sizeTier) {
            WidgetSizeTier.COMPACT -> 24f * density
            WidgetSizeTier.MEDIUM -> 34f * density
            WidgetSizeTier.LARGE -> 42f * density
        }
        val fittedPx = computeFittedTextSizePx(
            text = if (showChainage) chainage else "CTECS",
            paint = chainagePaint,
            maxWidthPx = availChainageW,
            maxHeightPx = centerH,
            preferredMaxPx = preferredSp,
            minPx = 15f * density
        )
        val fm = chainagePaint.fontMetrics
        val cy = centerTop + (centerH - (fm.descent - fm.ascent)) / 2f - fm.ascent
        canvas.drawText(if (showChainage) chainage else "CHAINAGE HIDDEN", pad, cy, chainagePaint)

        // Footer Strip: Next KM Marker | Distance Remaining | Speed
        val footerBaseline = sepY2 + footerH * 0.68f
        val footerLeftPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 11f * density
            color = primaryTextColor
        }
        val leftParts = buildList {
            if (nextKmStr.isNotBlank()) add(nextKmStr)
            if (distStr.isNotBlank()) add(distStr)
        }
        val leftSummary = leftParts.joinToString("  •  ").ifBlank { gpsStatusStr }
        val maxLeftW = if (speedStr.isNotBlank()) (w - pad * 2f) * 0.68f else (w - pad * 2f)
        val clippedLeft = TextUtils.ellipsize(leftSummary, footerLeftPaint, maxLeftW, TextUtils.TruncateAt.END).toString()
        canvas.drawText(clippedLeft, pad, footerBaseline, footerLeftPaint)

        if (speedStr.isNotBlank()) {
            val speedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = 12f * density
                color = accentColor
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(speedStr, w - pad, footerBaseline, speedPaint)
        }

        return fittedPx / density
    }

    private fun renderMiniMapLayout(
        context: Context,
        canvas: Canvas,
        w: Float,
        h: Float,
        density: Float,
        sizeTier: WidgetSizeTier,
        isDark: Boolean,
        instanceConfig: CtecsWidgetInstanceConfig,
        location: GeoPoint?,
        bearing: Float?,
        routeData: RouteData?,
        chainage: String,
        sideStr: String,
        speedStr: String,
        gpsStatusStr: String,
        primaryTextColor: Int,
        secondaryTextColor: Int,
        accentColor: Int,
        sideColor: Int,
        tileBgColor: Int,
        sepPaint: Paint
    ): Float {
        val pad = 10f * density
        val stripH = (h * 0.28f).coerceIn(34f * density, 54f * density)
        val mapRect = RectF(pad, pad, w - pad, (h - stripH - pad * 0.5f).coerceAtLeast(pad + 36f * density))

        if (instanceConfig.showMiniMap) {
            val flatMapCfg = MiniMapStyleConfig(
                enabled = true,
                mapStyle = if (isDark) MiniMapStyle.DARK else MiniMapStyle.LIGHT,
                orientationMode = instanceConfig.mapOrientation,
                autoZoom = true,
                zoomLevel = 15.6f,
                cornerShape = MiniMapCornerShape.ROUNDED,
                cornerRadiusDp = 10,
                borderEnabled = false,
                borderThicknessDp = 0f
            )
            MiniMapRenderer.drawMiniMap(
                context = context,
                canvas = canvas,
                bounds = mapRect,
                config = flatMapCfg,
                location = location,
                isGpsRetained = location != null,
                bearing = bearing,
                routeData = routeData,
                chainageLabel = null,
                sideLabel = null,
                dpScale = density
            )
        } else {
            val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = tileBgColor
            }
            canvas.drawRoundRect(mapRect, 10f * density, 10f * density, tilePaint)
            val msgPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                textSize = 11f * density
                color = secondaryTextColor
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Mini Map Hidden in Widget Settings", mapRect.centerX(), mapRect.centerY(), msgPaint)
        }

        // Bottom flat telemetry bar: Chainage | LHS/RHS | Speed
        val barTop = mapRect.bottom + 4f * density
        val barH = (h - barTop - pad * 0.6f).coerceAtLeast(24f * density)

        val rightMeta = listOf(sideStr, speedStr).filter { it.isNotBlank() }.joinToString("  •  ")
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 11.5f * density
            color = sideColor
            textAlign = Paint.Align.RIGHT
        }
        val metaW = if (rightMeta.isNotEmpty()) metaPaint.measureText(rightMeta) + 12f * density else 0f

        val chainageMaxW = (w - pad * 2f - metaW).coerceAtLeast(64f * density)
        val chainagePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            color = primaryTextColor
        }
        val fittedPx = computeFittedTextSizePx(
            text = if (instanceConfig.showChainage) chainage else "CTECS MAP",
            paint = chainagePaint,
            maxWidthPx = chainageMaxW,
            maxHeightPx = barH,
            preferredMaxPx = 22f * density,
            minPx = 13f * density
        )
        val fm = chainagePaint.fontMetrics
        val baseline = barTop + (barH - (fm.descent - fm.ascent)) / 2f - fm.ascent
        canvas.drawText(
            if (instanceConfig.showChainage) chainage else gpsStatusStr,
            pad,
            baseline,
            chainagePaint
        )

        if (rightMeta.isNotEmpty()) {
            canvas.drawText(rightMeta, w - pad, baseline, metaPaint)
        }

        return fittedPx / density
    }

    private fun renderDashboardLayout(
        context: Context,
        canvas: Canvas,
        w: Float,
        h: Float,
        density: Float,
        sizeTier: WidgetSizeTier,
        isDark: Boolean,
        instanceConfig: CtecsWidgetInstanceConfig,
        navState: LockScreenNavUiState,
        location: GeoPoint?,
        bearing: Float?,
        routeData: RouteData?,
        chainage: String,
        sideStr: String,
        speedStr: String,
        nextKmStr: String,
        distStr: String,
        gpsStatusStr: String,
        primaryTextColor: Int,
        secondaryTextColor: Int,
        accentColor: Int,
        sideColor: Int,
        tileBgColor: Int,
        sepPaint: Paint
    ): Float {
        val pad = 12f * density
        val showMapPane = instanceConfig.showMiniMap && w >= 220f * density && h >= 110f * density
        val mapW = if (showMapPane) (w * 0.38f).coerceIn(84f * density, 190f * density) else 0f
        val leftW = (w - pad * 2f - mapW - (if (showMapPane) 10f * density else 0f)).coerceAtLeast(110f * density)

        // Header on left pane
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 9.5f * density
            color = accentColor
            letterSpacing = 0.06f
        }
        canvas.drawText("CTECS DASHBOARD", pad, pad + 10f * density, headerPaint)

        if (sideStr.isNotEmpty()) {
            val sidePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = 11f * density
                color = sideColor
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(sideStr, pad + leftW, pad + 10.5f * density, sidePaint)
        }

        val topSepY = pad + 16f * density
        canvas.drawLine(pad, topSepY, pad + leftW, topSepY, sepPaint)

        // Speed + Next KM row + Location row at bottom of left pane
        val bottomBlockH = if (instanceConfig.showLocation && navState.locationName.isNotBlank()) {
            46f * density
        } else {
            30f * density
        }
        val chainageAreaTop = topSepY + 4f * density
        val chainageAreaBottom = (h - pad - bottomBlockH).coerceAtLeast(chainageAreaTop + 26f * density)
        val chainageAreaH = chainageAreaBottom - chainageAreaTop

        // Compact flat speed box next to chainage if enabled
        val speedBoxW = if (instanceConfig.showSpeed) 56f * density else 0f
        if (instanceConfig.showSpeed) {
            val speedBoxRect = RectF(
                pad + leftW - speedBoxW,
                chainageAreaTop + 2f * density,
                pad + leftW,
                chainageAreaBottom - 2f * density
            )
            val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = tileBgColor
            }
            canvas.drawRoundRect(speedBoxRect, 8f * density, 8f * density, boxPaint)

            val speedNumPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = 15f * density
                color = primaryTextColor
                textAlign = Paint.Align.CENTER
            }
            val speedUnitPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = 8.5f * density
                color = secondaryTextColor
                textAlign = Paint.Align.CENTER
            }
            val spdVal = if (navState.isLiveGpsValid) (navState.speedKmh ?: 0).toString() else "--"
            canvas.drawText(spdVal, speedBoxRect.centerX(), speedBoxRect.centerY() + 1f * density, speedNumPaint)
            canvas.drawText("km/h", speedBoxRect.centerX(), speedBoxRect.bottom - 5f * density, speedUnitPaint)
        }

        val chainageMaxW = (leftW - speedBoxW - (if (speedBoxW > 0f) 8f * density else 0f)).coerceAtLeast(64f * density)
        val chainagePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            color = primaryTextColor
        }
        val preferredSp = when (sizeTier) {
            WidgetSizeTier.COMPACT -> 22f * density
            WidgetSizeTier.MEDIUM -> 30f * density
            WidgetSizeTier.LARGE -> 38f * density
        }
        val fittedPx = computeFittedTextSizePx(
            text = if (instanceConfig.showChainage) chainage else "CTECS",
            paint = chainagePaint,
            maxWidthPx = chainageMaxW,
            maxHeightPx = chainageAreaH,
            preferredMaxPx = preferredSp,
            minPx = 14f * density
        )
        val fm = chainagePaint.fontMetrics
        val cy = chainageAreaTop + (chainageAreaH - (fm.descent - fm.ascent)) / 2f - fm.ascent
        canvas.drawText(if (instanceConfig.showChainage) chainage else "READY", pad, cy, chainagePaint)

        // Bottom separator + Next KM / Distance + Location Name
        canvas.drawLine(pad, chainageAreaBottom, pad + leftW, chainageAreaBottom, sepPaint)

        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 10.5f * density
            color = primaryTextColor
        }
        val nextParts = buildList {
            if (nextKmStr.isNotBlank()) add(nextKmStr)
            if (distStr.isNotBlank()) add(distStr)
        }
        val nextLine = nextParts.joinToString(" • ").ifBlank { gpsStatusStr }
        val clippedNext = TextUtils.ellipsize(nextLine, metaPaint, leftW, TextUtils.TruncateAt.END).toString()
        canvas.drawText(clippedNext, pad, chainageAreaBottom + 15f * density, metaPaint)

        if (instanceConfig.showLocation && navState.locationName.isNotBlank()) {
            val locPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                textSize = 9.5f * density
                color = secondaryTextColor
            }
            val clippedLoc = TextUtils.ellipsize(navState.locationName, locPaint, leftW, TextUtils.TruncateAt.END).toString()
            canvas.drawText(clippedLoc, pad, chainageAreaBottom + 30f * density, locPaint)
        }

        // Right Pane: Optional Mini Map
        if (showMapPane) {
            val mapRect = RectF(w - pad - mapW, pad, w - pad, h - pad)
            val flatMapCfg = MiniMapStyleConfig(
                enabled = true,
                mapStyle = if (isDark) MiniMapStyle.DARK else MiniMapStyle.LIGHT,
                orientationMode = instanceConfig.mapOrientation,
                autoZoom = true,
                zoomLevel = 15.5f,
                cornerShape = MiniMapCornerShape.ROUNDED,
                cornerRadiusDp = 10,
                borderEnabled = false,
                borderThicknessDp = 0f
            )
            MiniMapRenderer.drawMiniMap(
                context = context,
                canvas = canvas,
                bounds = mapRect,
                config = flatMapCfg,
                location = location,
                isGpsRetained = location != null,
                bearing = bearing,
                routeData = routeData,
                chainageLabel = null,
                sideLabel = null,
                dpScale = density
            )
        }

        return fittedPx / density
    }

    private fun drawFlatRefreshGlyph(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int,
        density: Float
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f * density
            strokeCap = Paint.Cap.ROUND
            this.color = color
        }
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(oval, -45f, 275f, false, p)
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }
        val path = Path().apply {
            moveTo(cx + radius * 0.7f, cy - radius * 0.9f)
            lineTo(cx + radius * 1.25f, cy - radius * 0.15f)
            lineTo(cx + radius * 0.25f, cy - radius * 0.2f)
            close()
        }
        canvas.drawPath(path, arrowPaint)
    }
}
