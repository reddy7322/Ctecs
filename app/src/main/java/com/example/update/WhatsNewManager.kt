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

        val SIMPLIFIED_FEATURES = listOf(
            WhatsNewFeature(
                title = "Minor Bugs Fixed",
                description = "General stability, UI layout, and performance improvements across modules.",
                category = "FIX"
            ),
            WhatsNewFeature(
                title = "New Improved Features Added",
                description = "Complete Dark/Light theme consistency and enhanced workflow capabilities.",
                category = "FEATURE"
            )
        )

        val DEFAULT_FEATURES = listOf(
            WhatsNewFeature(
                title = "Automatic Highway Chainage",
                description = "Real-time live chainage calculation with high precision interpolation.",
                category = "FEATURE"
            ),
            WhatsNewFeature(
                title = "Offline Highway Alignment",
                description = "Full offline support for KML, GeoJSON, and CSV survey alignments.",
                category = "FEATURE"
            )
        )

        /**
         * Parses release notes markdown into structured features with graceful fallback.
         */
        fun parseReleaseNotesToFeatures(notes: String): List<WhatsNewFeature> {
            if (notes.isBlank()) return DEFAULT_FEATURES

            val lines = notes.lines()
            val parsed = mutableListOf<WhatsNewFeature>()

            for (rawLine in lines) {
                val line = rawLine.trim()
                if (!line.startsWith("-") && !line.startsWith("*")) continue
                val content = line.removePrefix("-").removePrefix("*").trim()
                if (content.isBlank()) continue

                val boldRegex = Regex("""\*\*(.+?)\*\*:\s*(.*)""")
                val boldMatch = boldRegex.find(content)

                if (boldMatch != null) {
                    val title = boldMatch.groupValues[1].trim()
                    val desc = boldMatch.groupValues[2].trim()
                    parsed.add(
                        WhatsNewFeature(
                            title = title,
                            description = desc,
                            category = if (title.contains("Fix", ignoreCase = true) || title.contains("Bug", ignoreCase = true)) "FIX" else "FEATURE"
                        )
                    )
                } else if (content.contains(":")) {
                    val idx = content.indexOf(":")
                    val title = content.substring(0, idx).replace("*", "").trim()
                    val desc = content.substring(idx + 1).trim()
                    parsed.add(
                        WhatsNewFeature(
                            title = title,
                            description = desc,
                            category = if (title.contains("Fix", ignoreCase = true) || title.contains("Bug", ignoreCase = true)) "FIX" else "FEATURE"
                        )
                    )
                }
            }

            return if (parsed.isNotEmpty()) parsed else DEFAULT_FEATURES
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
