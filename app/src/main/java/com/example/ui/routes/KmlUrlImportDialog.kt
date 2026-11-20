package com.example.ui.routes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parser.KmlUrlImporter
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.ctecsBorderlessInputColors
import com.example.ui.theme.rememberDialogColors

/**
 * Shared Material 3 dialog for pasting a Google My Maps URL or direct KML/KMZ URL
 * and feeding it directly into the existing KMZ/KML import pipeline.
 */
@Composable
fun KmlUrlImportDialog(
    initialUrl: String = "",
    onDismiss: () -> Unit,
    onImportUrl: (String) -> Unit
) {
    val dialogColors = rememberDialogColors()
    val clipboardManager = LocalClipboardManager.current
    var urlInput by remember(initialUrl) { mutableStateOf(initialUrl) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val c = CtecsTheme.colors
    val primaryAccent = c.accent
    val greenAccent = c.success
    val isMyMapsDetected = remember(urlInput) {
        urlInput.isNotBlank() && KmlUrlImporter.isGoogleMyMapsUrl(urlInput)
    }

    val submitUrl = {
        val trimmed = urlInput.trim()
        try {
            KmlUrlImporter.resolveDownloadPlan(trimmed)
            validationError = null
            onDismiss()
            onImportUrl(trimmed)
        } catch (e: Exception) {
            validationError = e.message ?: "Please enter a valid http:// or https:// KML/KMZ URL."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogColors.shape,
        containerColor = dialogColors.containerColor,
        tonalElevation = 0.dp,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = primaryAccent,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Import KML / My Maps URL",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = dialogColors.titleColor
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Paste a Google My Maps sharing link or a direct KML/KMZ URL to download and parse it just like a local file.",
                    fontSize = 12.5.sp,
                    color = dialogColors.textSecondaryColor,
                    lineHeight = 17.sp
                )

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = {
                        urlInput = it
                        if (validationError != null) validationError = null
                    },
                    label = { Text("Google My Maps or KML/KMZ URL") },
                    placeholder = {
                        Text(
                            "https://www.google.com/maps/d/edit?mid=...",
                            fontSize = 12.sp
                        )
                    },
                    singleLine = false,
                    maxLines = 3,
                    isError = validationError != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { submitUrl() }
                    ),
                    trailingIcon = {
                        if (urlInput.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    urlInput = ""
                                    validationError = null
                                },
                                modifier = Modifier.testTag("kml_url_clear_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear URL"
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    val clipText = clipboardManager.getText()?.text?.trim()
                                    if (!clipText.isNullOrEmpty()) {
                                        urlInput = clipText
                                        validationError = null
                                    }
                                },
                                modifier = Modifier.testTag("kml_url_paste_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste URL from clipboard",
                                    tint = primaryAccent
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ctecsBorderlessInputColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("kml_url_input")
                )

                if (isMyMapsDetected) {
                    val mapId = KmlUrlImporter.parseGoogleMyMapsId(urlInput).orEmpty()
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = greenAccent.copy(alpha = 0.14f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mymaps_detected_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = greenAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Google My Maps detected (ID: $mapId)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (dialogColors.isDark) Color(0xFF6EE7B7) else Color(0xFF047857)
                            )
                        }
                    }
                }

                validationError?.let { err ->
                    Text(
                        text = err,
                        fontSize = 11.5.sp,
                        color = c.error,
                        modifier = Modifier.testTag("kml_url_validation_error")
                    )
                }

                Text(
                    text = "Tip: For Google My Maps links, ensure sharing is set to 'Anyone with the link can view'. Extensionless direct KML links and redirects are supported automatically.",
                    fontSize = 11.sp,
                    color = dialogColors.textSecondaryColor,
                    lineHeight = 15.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = submitUrl,
                enabled = urlInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryAccent,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("kml_url_submit_button")
            ) {
                Spacer(modifier = Modifier.width(2.dp))
                Text("Import", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("kml_url_cancel_button")
            ) {
                Text("Cancel", color = dialogColors.textSecondaryColor)
            }
        }
    )
}
