package com.example.ui.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ui.theme.rememberDialogColors
import com.example.update.AppReleaseInfo
import com.example.update.WhatsNewFeature

@Composable
fun UpdateAvailableDialog(
    releaseInfo: AppReleaseInfo,
    isDownloading: Boolean,
    downloadProgress: Float,
    onDownloadAndInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val dialogColors = rememberDialogColors()
    val isBetaUpdate = releaseInfo.isBeta || releaseInfo.channel.equals("beta", ignoreCase = true)
    val accentColor = if (isBetaUpdate) Color(0xFFF59E0B) else Color(0xFF0284C7)
    val badgeBgColor = if (isBetaUpdate) Color(0xFFB45309) else Color(0xFF0369A1)

    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        shape = dialogColors.shape,
        containerColor = dialogColors.containerColor,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isBetaUpdate) "Beta Update Available" else "Update Available",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = dialogColors.titleColor
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accentColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isBetaUpdate) "BETA" else "STABLE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = dialogColors.surfaceColor,
                        border = BorderStroke(1.dp, dialogColors.borderColor)
                    ) {
                        Text(
                            text = "Current: v${BuildConfig.VERSION_NAME}",
                            fontSize = 11.sp,
                            color = dialogColors.textSecondaryColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text("→", color = dialogColors.textSecondaryColor, fontSize = 12.sp)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeBgColor
                    ) {
                        Text(
                            text = "New: ${releaseInfo.tagName}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (releaseInfo.apkSize > 0) {
                    val sizeMb = releaseInfo.apkSize / (1024.0 * 1024.0)
                    Text(
                        text = "Download Size: %.1f MB".format(sizeMb),
                        fontSize = 12.sp,
                        color = dialogColors.textSecondaryColor
                    )
                }

                // Release notes box with adaptive colors
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = dialogColors.surfaceColor),
                    border = BorderStroke(1.dp, dialogColors.borderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Release Notes:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = accentColor
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = releaseInfo.releaseNotes.trim(),
                            fontSize = 12.sp,
                            color = dialogColors.textColor,
                            lineHeight = 16.sp
                        )
                    }
                }

                if (isDownloading) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Downloading update APK...",
                                fontSize = 12.sp,
                                color = accentColor
                            )
                            Text(
                                text = "${(downloadProgress * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = dialogColors.titleColor
                            )
                        }
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            color = accentColor,
                            trackColor = dialogColors.surfaceColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDownloadAndInstall,
                enabled = !isDownloading && releaseInfo.apkDownloadUrl != null,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("install_update_button")
            ) {
                Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isDownloading) "Downloading..." else "Download & Install")
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text("Later", color = dialogColors.textSecondaryColor)
                }
            }
        }
    )
}

@Composable
fun WhatsNewDialog(
    versionName: String,
    features: List<WhatsNewFeature>,
    onDismiss: () -> Unit
) {
    val dialogColors = rememberDialogColors()

    // Enforce simplified change log showing only the two requested descriptions
    val simplifiedFeatures = com.example.update.WhatsNewManager.SIMPLIFIED_FEATURES

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogColors.shape,
        containerColor = dialogColors.containerColor,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF59E0B).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NewReleases,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "What's New in Ctecs",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = dialogColors.titleColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Version $versionName",
                    fontSize = 12.sp,
                    color = dialogColors.textSecondaryColor,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                simplifiedFeatures.forEach { feature ->
                    WhatsNewSimplifiedRow(feature = feature, dialogColors = dialogColors)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (dialogColors.isDark) Color(0xFF2563EB) else Color(0xFF0284C7),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("whats_new_got_it_button")
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Got It", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun WhatsNewSimplifiedRow(
    feature: WhatsNewFeature,
    dialogColors: com.example.ui.theme.DialogThemeTokens
) {
    val (icon, iconColor) = if (feature.title.contains("Bug", ignoreCase = true)) {
        Pair(Icons.Default.Build, Color(0xFF10B981))
    } else {
        Pair(Icons.Default.Stars, Color(0xFFF59E0B))
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = dialogColors.surfaceColor,
        border = BorderStroke(1.dp, dialogColors.borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feature.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = dialogColors.titleColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = feature.description,
                    fontSize = 12.sp,
                    color = dialogColors.textSecondaryColor,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
