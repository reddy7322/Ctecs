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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * Modern, interactive File Picker UI component for selecting .kml and .kmz route files from local storage.
 * Integrates SAF (Storage Access Framework) with OpenDocument contract, persistable URI grants,
 * visual file format badges, and live parsing feedback.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KmlKmzFilePickerCard(
    onFileSelected: (uri: Uri, displayName: String) -> Unit,
    isImporting: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isAppDarkTheme()

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
                                fileName = cursor.getString(idx)
                            }
                        }
                    }
                } catch (ignored: Exception) {}
            }
            val resolvedName = fileName ?: uri.lastPathSegment?.substringAfterLast("/") ?: "imported_route.kmz"
            onFileSelected(uri, resolvedName)
        }
    }

    val launchPicker = {
        filePickerLauncher.launch(
            arrayOf(
                "application/vnd.google-earth.kml+xml",
                "application/vnd.google-earth.kmz",
                "application/xml",
                "text/xml",
                "application/zip",
                "application/octet-stream",
                "*/*"
            )
        )
    }

    val primaryColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
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
                text = "Select Route File (.KML / .KMZ)",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Pick a Google Earth or CAD alignment file from local storage to parse chainage & GPS coordinates.",
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
                FormatBadge("AutoCAD Civil 3D", isDark)
                Spacer(modifier = Modifier.width(6.dp))
                FormatBadge("QGIS / Global Mapper", isDark)
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
                        text = "Parsing KML/KMZ File...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
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
