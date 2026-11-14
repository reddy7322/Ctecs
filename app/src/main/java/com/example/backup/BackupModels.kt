package com.example.backup

import org.json.JSONArray
import org.json.JSONObject

enum class BackupCategory(
    val id: String,
    val displayName: String,
    val description: String
) {
    GENERAL(
        id = "general",
        displayName = "General",
        description = "Light/Dark theme, location naming, GPS, speed & coordinate display"
    ),
    CAMERA(
        id = "camera",
        displayName = "Camera",
        description = "Capture timer, grid, horizon level, preview ratio, audio & controls"
    ),
    PHOTO_VIDEO(
        id = "photo_video",
        displayName = "Photo & Video",
        description = "Resolutions, aspect ratios, JPEG quality, FPS, EXIF, filenames & storage"
    ),
    TIMESTAMP_WATERMARK(
        id = "timestamp_watermark",
        displayName = "Timestamp & Watermark",
        description = "Template, fonts, colors, shadow, opacity, padding & watermark logo"
    ),
    HIGHWAY_CUSTOMISATION(
        id = "highway_customisation",
        displayName = "Highway Customisation",
        description = "Active project profile, stretch, work type, side, lane & defined projects"
    ),
    CHAINAGE_CALIBRATION(
        id = "chainage_calibration",
        displayName = "Chainage & Calibration",
        description = "Permanent calibration profiles, control points, offsets & custom chainages"
    ),
    MINI_MAP(
        id = "mini_map",
        displayName = "Mini Map",
        description = "Photo & Video Mini Map style, alignment theme, map type & inset"
    ),
    GALLERY(
        id = "gallery",
        displayName = "Gallery",
        description = "Grid/list view mode, sort order & grouping preferences"
    ),
    DPR(
        id = "dpr",
        displayName = "DPR",
        description = "Default PDF report logo, signature stamp & prepared-by name"
    ),
    OTHER(
        id = "other",
        displayName = "Other",
        description = "Additional stored application preferences"
    );

    companion object {
        fun fromId(id: String?): BackupCategory {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: OTHER
        }
    }
}

enum class PrefValueType {
    STRING,
    BOOLEAN,
    INT,
    FLOAT,
    LONG,
    DOUBLE,
    STRING_SET;

    companion object {
        fun fromString(raw: String?): PrefValueType {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: STRING
        }
    }
}

data class TypedPrefEntry(
    val key: String,
    val type: PrefValueType,
    val value: Any,
    val category: BackupCategory
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("key", key)
        obj.put("type", type.name)
        when (type) {
            PrefValueType.STRING -> obj.put("value", value.toString())
            PrefValueType.BOOLEAN -> obj.put("value", value as? Boolean ?: value.toString().toBoolean())
            PrefValueType.INT -> obj.put("value", (value as? Number)?.toInt() ?: value.toString().toIntOrNull() ?: 0)
            PrefValueType.FLOAT -> obj.put("value", ((value as? Number)?.toDouble() ?: value.toString().toDoubleOrNull() ?: 0.0))
            PrefValueType.LONG -> obj.put("value", (value as? Number)?.toLong() ?: value.toString().toLongOrNull() ?: 0L)
            PrefValueType.DOUBLE -> obj.put("value", (value as? Number)?.toDouble() ?: value.toString().toDoubleOrNull() ?: 0.0)
            PrefValueType.STRING_SET -> {
                val arr = JSONArray()
                val set = (value as? Set<*>)?.mapNotNull { it?.toString() }.orEmpty()
                set.forEach { arr.put(it) }
                obj.put("value", arr)
            }
        }
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject, category: BackupCategory): TypedPrefEntry? {
            val key = obj.optString("key", "").trim()
            if (key.isBlank()) return null
            val type = PrefValueType.fromString(obj.optString("type", "STRING"))
            val parsedValue: Any = when (type) {
                PrefValueType.STRING -> obj.optString("value", "")
                PrefValueType.BOOLEAN -> obj.optBoolean("value", false)
                PrefValueType.INT -> obj.optInt("value", 0)
                PrefValueType.FLOAT -> obj.optDouble("value", 0.0).toFloat()
                PrefValueType.LONG -> obj.optLong("value", 0L)
                PrefValueType.DOUBLE -> obj.optDouble("value", 0.0)
                PrefValueType.STRING_SET -> {
                    val arr = obj.optJSONArray("value") ?: JSONArray()
                    buildSet {
                        for (i in 0 until arr.length()) {
                            add(arr.optString(i))
                        }
                    }
                }
            }
            return TypedPrefEntry(key = key, type = type, value = parsedValue, category = category)
        }
    }
}

data class BackupSupportingFile(
    val zipEntryPath: String,
    val prefKey: String,
    val originalFileName: String,
    val sha256: String,
    val sizeBytes: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("zipEntryPath", zipEntryPath)
        put("prefKey", prefKey)
        put("originalFileName", originalFileName)
        put("sha256", sha256)
        put("sizeBytes", sizeBytes)
    }

    companion object {
        fun fromJson(obj: JSONObject): BackupSupportingFile = BackupSupportingFile(
            zipEntryPath = obj.optString("zipEntryPath", ""),
            prefKey = obj.optString("prefKey", ""),
            originalFileName = obj.optString("originalFileName", ""),
            sha256 = obj.optString("sha256", ""),
            sizeBytes = obj.optLong("sizeBytes", 0L)
        )
    }
}

data class BackupManifest(
    val format: String = FORMAT_IDENTIFIER,
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val createdAtIso: String,
    val createdAtEpochMs: Long,
    val appVersionName: String,
    val appVersionCode: Int,
    val deviceManufacturer: String,
    val deviceModel: String,
    val androidSdk: Int,
    val totalSettingsCount: Int,
    val categoryCounts: Map<String, Int>,
    val calibrationProfileCount: Int,
    val controlPointCount: Int,
    val supportingFiles: List<BackupSupportingFile>,
    val entryChecksums: Map<String, String>,
    val manifestIntegrityHash: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("format", format)
        put("formatVersion", formatVersion)
        put("schemaVersion", schemaVersion)
        put("createdAtIso", createdAtIso)
        put("createdAtEpochMs", createdAtEpochMs)
        put("appVersionName", appVersionName)
        put("appVersionCode", appVersionCode)
        put("deviceManufacturer", deviceManufacturer)
        put("deviceModel", deviceModel)
        put("androidSdk", androidSdk)
        put("totalSettingsCount", totalSettingsCount)
        put("categoryCounts", JSONObject().apply {
            categoryCounts.forEach { (k, v) -> put(k, v) }
        })
        put("calibrationProfileCount", calibrationProfileCount)
        put("controlPointCount", controlPointCount)
        put("supportingFiles", JSONArray().apply {
            supportingFiles.forEach { put(it.toJson()) }
        })
        put("entryChecksums", JSONObject().apply {
            entryChecksums.forEach { (k, v) -> put(k, v) }
        })
        put("manifestIntegrityHash", manifestIntegrityHash)
    }

    companion object {
        const val FORMAT_IDENTIFIER = "ctecs-settings-backup"
        const val CURRENT_FORMAT_VERSION = 1
        const val CURRENT_SCHEMA_VERSION = 1

        fun fromJson(obj: JSONObject): BackupManifest {
            val catObj = obj.optJSONObject("categoryCounts") ?: JSONObject()
            val catMap = buildMap {
                val keys = catObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    put(k, catObj.optInt(k, 0))
                }
            }
            val filesArr = obj.optJSONArray("supportingFiles") ?: JSONArray()
            val files = buildList {
                for (i in 0 until filesArr.length()) {
                    filesArr.optJSONObject(i)?.let { add(BackupSupportingFile.fromJson(it)) }
                }
            }
            val checksumObj = obj.optJSONObject("entryChecksums") ?: JSONObject()
            val checksums = buildMap {
                val keys = checksumObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    put(k, checksumObj.optString(k, ""))
                }
            }
            return BackupManifest(
                format = obj.optString("format", ""),
                formatVersion = obj.optInt("formatVersion", -1),
                schemaVersion = obj.optInt("schemaVersion", -1),
                createdAtIso = obj.optString("createdAtIso", ""),
                createdAtEpochMs = obj.optLong("createdAtEpochMs", 0L),
                appVersionName = obj.optString("appVersionName", "Unknown"),
                appVersionCode = obj.optInt("appVersionCode", 0),
                deviceManufacturer = obj.optString("deviceManufacturer", ""),
                deviceModel = obj.optString("deviceModel", ""),
                androidSdk = obj.optInt("androidSdk", 0),
                totalSettingsCount = obj.optInt("totalSettingsCount", 0),
                categoryCounts = catMap,
                calibrationProfileCount = obj.optInt("calibrationProfileCount", 0),
                controlPointCount = obj.optInt("controlPointCount", 0),
                supportingFiles = files,
                entryChecksums = checksums,
                manifestIntegrityHash = obj.optString("manifestIntegrityHash", "")
            )
        }
    }
}

enum class BackupValidationStatus(val label: String) {
    VALID("Verified"),
    VALID_WITH_WARNINGS("Verified (With Notes)"),
    UNSUPPORTED_VERSION("Unsupported Version"),
    CORRUPTED_CHECKSUM("Integrity Check Failed"),
    MALFORMED_ARCHIVE("Invalid / Corrupted File"),
    EMPTY_ARCHIVE("Empty Backup")
}

enum class RestoreMode {
    COMPLETE,
    SELECTIVE
}

data class BackupPreviewInfo(
    val sourceLabel: String,
    val sourceUriString: String? = null,
    val sourceFilePath: String? = null,
    val manifest: BackupManifest? = null,
    val validationStatus: BackupValidationStatus,
    val validationMessage: String,
    val availableCategories: Set<BackupCategory> = emptySet(),
    val warnings: List<String> = emptyList(),
    val unsupportedKeys: List<String> = emptyList(),
    val adaptedSettingsPreview: List<String> = emptyList(),
    val archiveSizeBytes: Long = 0L
) {
    val isRestorable: Boolean
        get() = validationStatus == BackupValidationStatus.VALID ||
            validationStatus == BackupValidationStatus.VALID_WITH_WARNINGS
}

data class BackupRestoreReport(
    val success: Boolean,
    val mode: RestoreMode,
    val restoredCategories: Set<BackupCategory>,
    val restoredSettingsCount: Int = 0,
    val restoredSupportingFilesCount: Int = 0,
    val restoredCalibrationProfilesCount: Int = 0,
    val restoredControlPointsCount: Int = 0,
    val adaptedSettings: List<String> = emptyList(),
    val skippedSettings: List<String> = emptyList(),
    val rolledBack: Boolean = false,
    val errorMessage: String? = null,
    val completedAtEpochMs: Long = System.currentTimeMillis()
)

data class BackupExportResult(
    val success: Boolean,
    val fileName: String,
    val localBackupFilePath: String? = null,
    val externalUriString: String? = null,
    val sizeBytes: Long = 0L,
    val totalSettingsCount: Int = 0,
    val calibrationProfileCount: Int = 0,
    val controlPointCount: Int = 0,
    val supportingFilesCount: Int = 0,
    val errorMessage: String? = null
)

data class BackupHistoryItem(
    val fileName: String,
    val filePath: String,
    val createdAtEpochMs: Long,
    val appVersionName: String,
    val sizeBytes: Long,
    val validationStatus: BackupValidationStatus,
    val validationSummary: String,
    val totalSettingsCount: Int
)
