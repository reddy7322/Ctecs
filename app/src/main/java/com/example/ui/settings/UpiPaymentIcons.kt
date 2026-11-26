package com.example.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class UpiAppBrand(
    val packageName: String,
    val displayName: String
) {
    GOOGLE_PAY("com.google.android.apps.nbu.paisa.user", "Google Pay"),
    PHONE_PE("com.phonepe.app", "PhonePe"),
    PAYTM("net.one97.paytm", "Paytm")
}

internal data class LoadedUpiAppIcons(
    val iconsByPackage: Map<String, ImageBitmap> = emptyMap()
) {
    fun iconFor(brand: UpiAppBrand): ImageBitmap? = iconsByPackage[brand.packageName]
    fun isInstalled(brand: UpiAppBrand): Boolean = iconsByPackage.containsKey(brand.packageName)
}

/**
 * Loads authentic installed Android app icons for Google Pay, PhonePe, and Paytm
 * off the main thread via PackageManager. Returns empty entries cleanly when an app
 * is not installed so authentic vector emblems render as seamless fallback.
 */
@Composable
internal fun rememberInstalledUpiAppIcons(): LoadedUpiAppIcons {
    val context = LocalContext.current
    var loaded by remember(context) { mutableStateOf(LoadedUpiAppIcons()) }

    LaunchedEffect(context) {
        val map = withContext(Dispatchers.IO) {
            val result = LinkedHashMap<String, ImageBitmap>()
            val pm = context.packageManager
            for (brand in UpiAppBrand.entries) {
                runCatching {
                    val drawable = pm.getApplicationIcon(brand.packageName)
                    drawableToBitmap(drawable, sizePx = 96)?.asImageBitmap()
                }.getOrNull()?.let { bmp ->
                    result[brand.packageName] = bmp
                }
            }
            result
        }
        loaded = LoadedUpiAppIcons(map)
    }

    return loaded
}

private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap? {
    return runCatching {
        if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
            Bitmap.createScaledBitmap(drawable.bitmap, sizePx, sizePx, true)
        } else {
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = AndroidCanvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        }
    }.getOrNull()
}

/**
 * Renders the authentic installed app icon when available on-device, falling back
 * to a clean, recognizable vector brand icon for Google Pay, PhonePe, or Paytm.
 */
@Composable
internal fun AuthenticUpiBrandIcon(
    brand: UpiAppBrand,
    installedIcon: ImageBitmap?,
    size: Dp = 24.dp
) {
    if (installedIcon != null) {
        Image(
            bitmap = installedIcon,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
        )
        return
    }

    when (brand) {
        UpiAppBrand.GOOGLE_PAY -> GooglePayVectorMark(size = size)
        UpiAppBrand.PHONE_PE -> PhonePeVectorMark(size = size)
        UpiAppBrand.PAYTM -> PaytmVectorMark(size = size)
    }
}

@Composable
private fun GooglePayVectorMark(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.72f)) {
            val strokeWidth = this.size.minDimension * 0.21f
            val inset = strokeWidth / 2f
            val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
            val topLeft = Offset(inset, inset)

            // Red top arc
            drawArc(
                color = Color(0xFFEA4335),
                startAngle = -145f,
                sweepAngle = 105f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            // Yellow left arc
            drawArc(
                color = Color(0xFFFBBC05),
                startAngle = 155f,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            // Green bottom arc
            drawArc(
                color = Color(0xFF34A853),
                startAngle = 45f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            // Blue right arc + crossbar
            drawArc(
                color = Color(0xFF4285F4),
                startAngle = -5f,
                sweepAngle = 55f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            val centerY = this.size.height / 2f
            drawLine(
                color = Color(0xFF4285F4),
                start = Offset(this.size.width * 0.50f, centerY),
                end = Offset(this.size.width - inset * 0.35f, centerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Butt
            )
        }
    }
}

@Composable
private fun PhonePeVectorMark(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF5F259F)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "पे",
            color = Color.White,
            fontSize = (size.value * 0.52f).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun PaytmVectorMark(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            // Classic Paytm navy & sky-blue split accent bar at bottom
            drawRect(
                color = Color(0xFF002970),
                topLeft = Offset(w * 0.14f, h * 0.72f),
                size = Size(w * 0.36f, h * 0.14f)
            )
            drawRect(
                color = Color(0xFF00BAF2),
                topLeft = Offset(w * 0.50f, h * 0.72f),
                size = Size(w * 0.36f, h * 0.14f)
            )
        }
        Text(
            text = "Pay",
            color = Color(0xFF002970),
            fontSize = (size.value * 0.36f).sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

/**
 * Clean UPI twin-chevron emblem (saffron + emerald) for "Other UPI" payment button.
 */
@Composable
internal fun OtherUpiVectorMark(size: Dp = 24.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Saffron leading chevron
        val saffron = Path().apply {
            moveTo(w * 0.24f, h * 0.16f)
            lineTo(w * 0.62f, h * 0.50f)
            lineTo(w * 0.16f, h * 0.84f)
            lineTo(w * 0.38f, h * 0.50f)
            close()
        }
        drawPath(path = saffron, color = Color(0xFFF47920))

        // Emerald trailing chevron
        val green = Path().apply {
            moveTo(w * 0.50f, h * 0.16f)
            lineTo(w * 0.88f, h * 0.50f)
            lineTo(w * 0.42f, h * 0.84f)
            lineTo(w * 0.64f, h * 0.50f)
            close()
        }
        drawPath(path = green, color = Color(0xFF098A42))
    }
}
