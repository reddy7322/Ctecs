package com.example.weather

import android.content.Context
import com.example.model.BackupDuplicateResolution
import com.example.model.BackupFrequency
import com.example.model.HighwayWorkPlan
import com.example.model.WorkPlanningSettings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class WorkPlanBackupInspection(
    val isValid: Boolean,
    val schemaVersion: Int,
    val exportedAtMs: Long,
    val plans: List<HighwayWorkPlan>,
    val settings: WorkPlanningSettings?,
    val duplicatePlanIds: Set<String>,
    val errorMessage: String? = null
)

data class WorkPlanBackupApplyResult(
    val mergedPlans: List<HighwayWorkPlan>,
    val importedCount: Int,
    val replacedCount: Int,
    val skippedCount: Int,
    val safetyBackupFileName: String? = null
)

data class LocalBackupRecord(
    val fileName: String,
    val filePath: String,
    val createdAtMs: Long,
    val planCount: Int
)

/**
 * Safe versioned JSON Backup, Import, Validation & Restore manager for Highway Work Plans.
 * - Never overwrites silently
 * - Creates a local safety backup prior to any Restore operation
 * - Retains up to MAX_RETAINED_BACKUPS automatic local backups
 */
class WorkPlanBackupManager(private val context: Context) {

    private val backupDir: File
        get() = File(context.filesDir, "work_plan_backups").apply { if (!exists()) mkdirs() }

    fun createBackupJson(
        plans: List<HighwayWorkPlan>,
        settings: WorkPlanningSettings? = null
    ): String {
        val now = System.currentTimeMillis()
        val root = JSONObject().apply {
            put("app", "CTECS")
            put("type", BACKUP_TYPE)
            put("schemaVersion", BACKUP_SCHEMA_VERSION)
            put("exportedAtMs", now)
            put("planCount", plans.size)
            put("plans", JSONArray().apply { plans.forEach { put(it.toJson()) } })
            settings?.let { put("workPlanningSettings", JSONObject(it.toJsonString())) }
        }
        return root.toString(2)
    }

    fun inspectBackupJson(
        rawJson: String,
        existingPlans: List<HighwayWorkPlan>
    ): WorkPlanBackupInspection {
        if (rawJson.isBlank()) {
            return WorkPlanBackupInspection(false, 0, 0L, emptyList(), null, emptySet(), "Backup file is empty.")
        }
        return try {
            val root = JSONObject(rawJson)
            val type = root.optString("type", "")
            val version = root.optInt("schemaVersion", 0)
            if (type != BACKUP_TYPE || version <= 0 || version > BACKUP_SCHEMA_VERSION) {
                return WorkPlanBackupInspection(
                    isValid = false,
                    schemaVersion = version,
                    exportedAtMs = 0L,
                    plans = emptyList(),
                    settings = null,
                    duplicatePlanIds = emptySet(),
                    errorMessage = "Unsupported or invalid CTECS Work Plan backup format (version $version)."
                )
            }
            val arr = root.optJSONArray("plans")
                ?: return WorkPlanBackupInspection(false, version, 0L, emptyList(), null, emptySet(), "Backup contains no plans array.")
            val parsedPlans = buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { add(HighwayWorkPlan.fromJson(it)) }
                }
            }
            val parsedSettings = root.optJSONObject("workPlanningSettings")
                ?.let { WorkPlanningSettings.fromJsonString(it.toString()) }
            val existingIds = existingPlans.map { it.planId }.toSet()
            val duplicates = parsedPlans.map { it.planId }.filter { it in existingIds }.toSet()
            WorkPlanBackupInspection(
                isValid = true,
                schemaVersion = version,
                exportedAtMs = root.optLong("exportedAtMs", 0L),
                plans = parsedPlans,
                settings = parsedSettings,
                duplicatePlanIds = duplicates
            )
        } catch (e: Exception) {
            WorkPlanBackupInspection(
                isValid = false,
                schemaVersion = 0,
                exportedAtMs = 0L,
                plans = emptyList(),
                settings = null,
                duplicatePlanIds = emptySet(),
                errorMessage = "Corrupted JSON backup: ${e.localizedMessage ?: "Parse error"}"
            )
        }
    }

    fun applyImport(
        existingPlans: List<HighwayWorkPlan>,
        inspection: WorkPlanBackupInspection,
        resolution: BackupDuplicateResolution
    ): WorkPlanBackupApplyResult {
        if (!inspection.isValid) {
            return WorkPlanBackupApplyResult(existingPlans, 0, 0, 0)
        }
        val byId = existingPlans.associateBy { it.planId }.toMutableMap()
        val orderedIds = existingPlans.map { it.planId }.toMutableList()
        var imported = 0
        var replaced = 0
        var skipped = 0

        inspection.plans.forEach { incoming ->
            if (byId.containsKey(incoming.planId)) {
                when (resolution) {
                    BackupDuplicateResolution.SKIP -> skipped++
                    BackupDuplicateResolution.REPLACE -> {
                        byId[incoming.planId] = incoming
                        replaced++
                    }
                    BackupDuplicateResolution.IMPORT_AS_NEW -> {
                        val copy = incoming.copy(
                            planId = UUID.randomUUID().toString(),
                            planTitle = "${incoming.effectivePlanTitle} (Imported)",
                            updatedAtMs = System.currentTimeMillis()
                        )
                        byId[copy.planId] = copy
                        orderedIds.add(0, copy.planId)
                        imported++
                    }
                }
            } else {
                byId[incoming.planId] = incoming
                orderedIds.add(0, incoming.planId)
                imported++
            }
        }
        val merged = orderedIds.mapNotNull { byId[it] }
        return WorkPlanBackupApplyResult(merged, imported, replaced, skipped)
    }

    /**
     * Safely restores a backup by FIRST creating an automatic local safety backup of existing plans.
     * Existing plans remain untouched if parsing or validation fails.
     */
    fun safeRestoreBackup(
        rawJson: String,
        existingPlans: List<HighwayWorkPlan>,
        existingSettings: WorkPlanningSettings
    ): Result<WorkPlanBackupApplyResult> {
        val inspection = inspectBackupJson(rawJson, existingPlans)
        if (!inspection.isValid) {
            return Result.failure(IllegalArgumentException(inspection.errorMessage ?: "Invalid backup"))
        }
        val safetyName = saveLocalBackupSnapshot(existingPlans, existingSettings, prefix = "safety_pre_restore")
        return Result.success(
            WorkPlanBackupApplyResult(
                mergedPlans = inspection.plans,
                importedCount = inspection.plans.size,
                replacedCount = 0,
                skippedCount = 0,
                safetyBackupFileName = safetyName
            )
        )
    }

    fun saveLocalBackupSnapshot(
        plans: List<HighwayWorkPlan>,
        settings: WorkPlanningSettings,
        prefix: String = "auto_backup"
    ): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "${prefix}_${stamp}.json"
        val file = File(backupDir, fileName)
        file.writeText(createBackupJson(plans, settings))
        pruneOldBackups()
        return fileName
    }

    fun runScheduledAutoBackupIfDue(
        frequency: BackupFrequency,
        plans: List<HighwayWorkPlan>,
        settings: WorkPlanningSettings,
        nowMs: Long = System.currentTimeMillis()
    ): String? {
        if (frequency == BackupFrequency.MANUAL_ONLY || plans.isEmpty()) return null
        val intervalMs = when (frequency) {
            BackupFrequency.DAILY -> 24L * 60L * 60_000L
            BackupFrequency.WEEKLY -> 7L * 24L * 60L * 60_000L
            BackupFrequency.MANUAL_ONLY -> return null
        }
        val latest = listLocalBackups().maxByOrNull { it.createdAtMs }
        if (latest != null && (nowMs - latest.createdAtMs) < intervalMs) return null
        return saveLocalBackupSnapshot(plans, settings, prefix = "auto_backup")
    }

    fun listLocalBackups(): List<LocalBackupRecord> {
        val files = backupDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?: return emptyList()
        return files.map { file ->
            val count = runCatching { JSONObject(file.readText()).optInt("planCount", 0) }.getOrDefault(0)
            LocalBackupRecord(
                fileName = file.name,
                filePath = file.absolutePath,
                createdAtMs = file.lastModified(),
                planCount = count
            )
        }
    }

    fun readLocalBackup(filePath: String): String? = runCatching { File(filePath).readText() }.getOrNull()

    private fun pruneOldBackups() {
        val files = backupDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(MAX_RETAINED_BACKUPS).forEach { runCatching { it.delete() } }
    }

    companion object {
        const val BACKUP_TYPE = "CTECS_WORK_PLANS_BACKUP"
        const val BACKUP_SCHEMA_VERSION = 1
        const val MAX_RETAINED_BACKUPS = 5
    }
}
