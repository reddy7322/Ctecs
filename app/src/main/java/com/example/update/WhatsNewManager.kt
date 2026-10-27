package com.example.update

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.updateDataStore by preferencesDataStore(name = "ctecs_update_prefs")

class WhatsNewManager(private val context: Context) {

    companion object {
        private val KEY_LAST_SEEN_VERSION = stringPreferencesKey("last_seen_version")

        val DEFAULT_FEATURES = listOf(
            WhatsNewFeature(
                title = "Automatic Highway Chainage",
                description = "Loads KMZ/KML alignments and matches 50m highway chainage markers in real-time.",
                category = "ACCURACY"
            ),
            WhatsNewFeature(
                title = "Professional Timestamp Overlay",
                description = "High-contrast white bold typography with dark outline matching the NH-58 reference photo style.",
                category = "OVERLAY"
            ),
            WhatsNewFeature(
                title = "Offline Vector Mini-Map",
                description = "In-camera vector mini-map rendering road alignment, nearby chainage ticks, and GPS location pin.",
                category = "MAP"
            ),
            WhatsNewFeature(
                title = "Single-Tap Field Selectors",
                description = "Instant toggles for Road Side (RHS/LHS), Lane (I/L, M/L, O/L), and Pavement Layers (DBM, BC, GSB).",
                category = "FIELD"
            ),
            WhatsNewFeature(
                title = "Smart Inbuilt GitHub Updates",
                description = "Automatic background update checking from GitHub releases with direct OTA APK installation.",
                category = "UPDATE"
            )
        )

        /**
         * Parses GitHub release markdown body into structured feature highlights.
         */
        fun parseReleaseNotesToFeatures(notes: String): List<WhatsNewFeature> {
            val items = mutableListOf<WhatsNewFeature>()
            val lines = notes.lines()

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")) {
                    val clean = trimmed.removePrefix("- ").removePrefix("* ").removePrefix("• ").trim()
                    if (clean.isNotBlank()) {
                        val parts = clean.split(":", limit = 2)
                        if (parts.size == 2) {
                            items.add(
                                WhatsNewFeature(
                                    title = parts[0].replace("**", "").trim(),
                                    description = parts[1].trim(),
                                    category = "RELEASE"
                                )
                            )
                        } else {
                            items.add(
                                WhatsNewFeature(
                                    title = clean.take(40).replace("**", ""),
                                    description = clean,
                                    category = "RELEASE"
                                )
                            )
                        }
                    }
                }
            }

            return if (items.isNotEmpty()) items else DEFAULT_FEATURES
        }
    }

    val lastSeenVersionFlow: Flow<String?> = context.updateDataStore.data.map { prefs ->
        prefs[KEY_LAST_SEEN_VERSION]
    }

    suspend fun shouldShowWhatsNewOnLaunch(): Boolean {
        val lastSeen = lastSeenVersionFlow.first()
        val currentVersion = BuildConfig.VERSION_NAME
        return lastSeen == null || lastSeen != currentVersion
    }

    suspend fun markCurrentVersionAsSeen() {
        context.updateDataStore.edit { prefs ->
            prefs[KEY_LAST_SEEN_VERSION] = BuildConfig.VERSION_NAME
        }
    }

    fun parseReleaseNotes(notes: String): List<WhatsNewFeature> = parseReleaseNotesToFeatures(notes)
    fun parseReleaseNotesToFeatures(notes: String): List<WhatsNewFeature> = Companion.parseReleaseNotesToFeatures(notes)
}
