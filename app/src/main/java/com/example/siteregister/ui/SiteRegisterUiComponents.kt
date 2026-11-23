package com.example.siteregister.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.siteregister.model.RegisterStatus
import com.example.siteregister.util.QrCodeAndOcrHelper
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.ctecsBorderlessInputColors
import com.example.ui.theme.smartFadeSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SrTopBar(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val c = CtecsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .testTag("sr_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = c.textPrimary
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = c.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = c.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = actions
        )
    }
}

@Composable
fun SrSurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = CtecsTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .smartFadeSurface(
                surfaceColor = c.surface,
                cornerRadius = 16.dp,
                edgeAlpha = 0.85f,
                centerAlpha = 1f
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
fun SrSectionHeader(
    title: String,
    trailing: String? = null,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = c.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp
        )
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                color = c.accent,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun SrPillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = CtecsTheme.colors.accent
) {
    val c = CtecsTheme.colors
    val bg = if (selected) accentColor.copy(alpha = if (c.isDark) 0.24f else 0.16f) else c.secondarySurface
    val fg = if (selected) accentColor else c.textSecondary
    Row(
        modifier = modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        }
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SrStatusBadge(
    label: String,
    color: Color,
    leadingLock: Boolean = false
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (leadingLock) {
            Icon(Icons.Default.Lock, contentDescription = "Locked", tint = color, modifier = Modifier.size(12.dp))
        }
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
fun registerStatusColor(status: RegisterStatus): Color {
    val c = CtecsTheme.colors
    return when (status) {
        RegisterStatus.DRAFT -> c.textSecondary
        RegisterStatus.SUBMITTED, RegisterStatus.PENDING_PMC_APPROVAL, RegisterStatus.RESUBMITTED -> c.warning
        RegisterStatus.APPROVED_LOCKED -> c.success
        RegisterStatus.EDIT_PERMISSION_REQUESTED -> c.accentSecondary
        RegisterStatus.EDIT_PERMISSION_GRANTED, RegisterStatus.REOPENED -> c.accent
        RegisterStatus.EDIT_PERMISSION_EXPIRED, RegisterStatus.REJECTED -> c.error
    }
}

@Composable
fun SrTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    testTagName: String? = null
) {
    val c = CtecsTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = if (placeholder.isNotBlank()) ({ Text(placeholder, fontSize = 12.sp, color = c.textMuted) }) else null,
        singleLine = singleLine,
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(12.dp),
        colors = ctecsBorderlessInputColors(),
        modifier = modifier
            .fillMaxWidth()
            .then(if (testTagName != null) Modifier.testTag(testTagName) else Modifier)
    )
}

@Composable
fun SrPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    containerColor: Color = CtecsTheme.colors.accent,
    testTagName: String? = null
) {
    val c = CtecsTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = c.onAccent,
            disabledContainerColor = c.secondarySurface,
            disabledContentColor = c.textMuted
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(if (testTagName != null) Modifier.testTag(testTagName) else Modifier)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
    }
}

@Composable
fun SrTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    accent: Color = CtecsTheme.colors.accent,
    testTagName: String? = null
) {
    val c = CtecsTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent.copy(alpha = if (c.isDark) 0.18f else 0.13f),
            contentColor = accent,
            disabledContainerColor = c.secondarySurface,
            disabledContentColor = c.textMuted
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(if (testTagName != null) Modifier.testTag(testTagName) else Modifier)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
fun SrBannerStrip(
    message: String?,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    if (message.isNullOrBlank()) return
    val c = CtecsTheme.colors
    val tint = if (isError) c.error else c.success
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = if (c.isDark) 0.22f else 0.14f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(tint)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = message,
            color = c.textPrimary,
            fontSize = 12.5.sp,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = c.textSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun SrSignaturePadCanvas(
    onSignatureChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val currentStroke = remember { mutableStateListOf<Offset>() }

    fun serializeStrokes(): String =
        strokes.joinToString("|") { s -> s.joinToString(";") { "${it.x.toInt()},${it.y.toInt()}" } }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.secondarySurface)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentStroke.clear()
                            currentStroke.add(offset)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            currentStroke.add(change.position)
                        },
                        onDragEnd = {
                            if (currentStroke.isNotEmpty()) {
                                strokes.add(currentStroke.toList())
                                currentStroke.clear()
                                onSignatureChanged(serializeStrokes())
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val all = strokes + listOf(currentStroke.toList())
                all.forEach { pts ->
                    if (pts.size >= 2) {
                        val path = Path().apply {
                            moveTo(pts.first().x, pts.first().y)
                            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                        }
                        drawPath(
                            path = path,
                            color = c.textPrimary,
                            style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }
            if (strokes.isEmpty() && currentStroke.isEmpty()) {
                Text(
                    text = "Draw signature here",
                    color = c.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            SrPillChip(
                label = "Clear Pad",
                selected = false,
                onClick = {
                    strokes.clear()
                    currentStroke.clear()
                    onSignatureChanged("")
                }
            )
        }
    }
}

@Composable
fun SrQrCodeBox(
    codeContent: String,
    caption: String,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    val bitmap: Bitmap = remember(codeContent) {
        QrCodeAndOcrHelper.generateQrCodeBitmap(codeContent, sizePx = 360)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "QR Code for $caption",
            modifier = Modifier.size(164.dp)
        )
        Text(
            text = caption,
            color = Color(0xFF0F172A),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun srFormatTime(ms: Long): String =
    if (ms <= 0L) "Never" else SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(ms))

fun srTodayIsoDate(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
