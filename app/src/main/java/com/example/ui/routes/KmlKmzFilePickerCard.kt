package com.example.ui.routes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.isAppDarkTheme

/**
 * Interactive File & URL Picker UI component for importing .kml, .kmz, Google My Maps links,
 * and direct KML/KMZ URLs through the unified KMZ/KML alignment pipeline.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KmlKmzFilePickerCard(
    onFileSelected: (uri: Uri, displayName: String) -> Unit,
    isImporting: Boolean = false,
    onImportFromUrl: ((String) -> Unit)? = null,
    importStatusMessage: String? = null,
    importErrorMessage: String? = null,
    onDismissStatus: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isAppDarkTheme()
    var showUrlImportDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (ignored: Exception) {}

            var fileName: String? = null
            if (uri.scheme == "content") {
                try {
                    context.contentResolver.query(
                        uri,
                        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx != -1) {
                                val n = cursor.getString(idx)
                                if (!n.isNullOrBlank()) fileName = n
                            }
                        }
                    }
                } catch (ignored: Exception) {}
            } else if (uri.scheme == "file") {
                uri.path?.let { p ->
                    val f = java.io.File(p)
                    if (f.name.isNotBlank()) fileName = f.name
                }
            }
            val rawFallback = uri.lastPathSegment?.substringAfterLast("/") ?: "imported_route.kmz"
            val decodedFallback = try { Uri.decode(rawFallback) } catch (e: Exception) { rawFallback }
            val resolvedName = fileName ?: decodedFallback
            onFileSelected(uri, resolvedName)
        }
    }

    val launchPicker = {
        try {
            filePickerLauncher.launch(arrayOf("*/*"))
        } catch (e: Exception) {
            // Fallback to explicit MIME types if device provider requires it
            filePickerLauncher.launch(
                arrayOf(
                    "application/vnd.google-earth.kml+xml",
                    "application/vnd.google-earth.kmz",
                    "application/zip",
                    "application/octet-stream",
                    "*/*"
                )
            )
        }
    }

    val primaryColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val greenAccent = Color(0xFF10B981)
    val redAccent = Color(0xFFEF4444)
    val cardBg = if (isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC)
    val borderColor = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.5.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("kml_kmz_file_picker_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Icon Badge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(primaryColor.copy(alpha = 0.12f))
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Select KML / KMZ file",
                    tint = primaryColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Import Route Alignment (.KML / .KMZ / URL)",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Pick a local .kml/.kmz file or paste a Google My Maps / direct KML URL to parse chainage & GPS alignment.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Supported Format Badges
            FlowRow(
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FormatBadge(".KMZ Archive", isDark)
                Spacer(modifier = Modifier.width(6.dp))
                FormatBadge(".KML Document", isDark)
                Spacer(modifier = Modifier.width(6.dp))
                FormatBadge("Google My Maps URL", isDark)
                Spacer(modifier = Modifier.width(6.dp))
                FormatBadge("Direct KML URL", isDark)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Pick Button
            Button(
                onClick = launchPicker,
                enabled = !isImporting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("select_kml_kmz_file_button")
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = importStatusMessage ?: "Importing KML/KMZ...",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Browse Local Storage",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (onImportFromUrl != null) {
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { showUrlImportDialog = true },
                    enabled = !isImporting,
                    border = BorderStroke(1.2.dp, primaryColor.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("import_kml_url_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Import KML / My Maps URL",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isImporting) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    color = primaryColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }

            if (!isImporting && !importErrorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = redAccent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, redAccent.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("kml_import_error_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = redAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = importErrorMessage,
                                fontSize = 11.5.sp,
                                color = if (isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C)
                            )
                        }
                        if (onDismissStatus != null) {
                            IconButton(onClick = onDismissStatus, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss error",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (!isImporting && importErrorMessage.isNullOrBlank() && !importStatusMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = greenAccent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, greenAccent.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("kml_import_status_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = greenAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = importStatusMessage,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDark) Color(0xFF6EE7B7) else Color(0xFF047857)
                            )
                        }
                        if (onDismissStatus != null) {
                            IconButton(onClick = onDismissStatus, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss status",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Supports continuous alignment line strings & point markers",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showUrlImportDialog && onImportFromUrl != null) {
        KmlUrlImportDialog(
            onDismiss = { showUrlImportDialog = false },
            onImportUrl = { url -> onImportFromUrl(url) }
        )
    }
}

@Composable
private fun FormatBadge(label: String, isDark: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
