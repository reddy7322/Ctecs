package com.example.siteregister.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.siteregister.engine.ApprovalWorkflowEngine
import com.example.siteregister.model.AccountStatus
import com.example.siteregister.model.BcLayingEntry
import com.example.siteregister.model.EditPermissionGrant
import com.example.siteregister.model.EditPermissionRequest
import com.example.siteregister.model.EditRequestStatus
import com.example.siteregister.model.GlassGridEntry
import com.example.siteregister.model.HighwayActivityCategory
import com.example.siteregister.model.MeasurementMethod
import com.example.siteregister.model.MeasurementUnit
import com.example.siteregister.model.MembershipApprovalStatus
import com.example.siteregister.model.ObservationCategory
import com.example.siteregister.model.ObservationSeverity
import com.example.siteregister.model.OrganizationType
import com.example.siteregister.model.ProjectCodeRecord
import com.example.siteregister.model.ProjectInfo
import com.example.siteregister.model.ProjectMembership
import com.example.siteregister.model.ProjectRole
import com.example.siteregister.model.QuantityClassification
import com.example.siteregister.model.QualityTestCategory
import com.example.siteregister.model.QualityTestReportRecord
import com.example.siteregister.model.RectificationStatus
import com.example.siteregister.model.RegisterSharingCode
import com.example.siteregister.model.RegisterStatus
import com.example.siteregister.model.RegistrationRequestRecord
import com.example.siteregister.model.SignatureStatus
import com.example.siteregister.model.SiteAuditEvent
import com.example.siteregister.model.SiteObservationRecord
import com.example.siteregister.model.SiteRegisterRecord
import com.example.siteregister.model.SiteRegisterRevision
import com.example.siteregister.model.SiteRegisterSettings
import com.example.siteregister.model.SiteSegmentRecord
import com.example.siteregister.model.SiteSignatureRecord
import com.example.siteregister.model.SyncState
import com.example.siteregister.model.TackCoatTestRecord
import com.example.siteregister.model.TackCoatTrayRow
import com.example.siteregister.model.TestResultStatus
import com.example.siteregister.model.UserProfile
import com.example.siteregister.model.UserSession
import com.example.siteregister.model.VerifiedProjectCodeResult
import com.example.siteregister.network.SupabaseSiteRegisterClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.siteRegisterDataStore by preferencesDataStore(name = "ctecs_site_register_prefs")

/**
 * Offline-first Repository combining Room local cache + Pending Sync Queue +
 * Supabase Serverless Backend (Auth, PostgreSQL PostgREST/RPC, Storage, Realtime).
 *
 * Security & Reliability Guarantees:
 * - Every write persists locally first with a deterministic UUID and original timestamp.
 * - Pre-sync lock guard verifies approved-register lock & active PMC edit permission BEFORE
 *   committing offline edits to Supabase so offline mode can NEVER bypass PMC lock rules.
 * - Permanent append-only audit trail recorded for every registration, approval, measurement,
 *   signature, edit permission, and export event.
 */
class SiteRegisterRepository(
    private val context: Context,
    private val dao: SiteRegisterDao,
    val supabaseClient: SupabaseSiteRegisterClient = SupabaseSiteRegisterClient()
) {
    companion object {
        const val TYPE_PROJECT = "PROJECT"
        const val TYPE_PROJECT_CODE = "PROJECT_CODE"
        const val TYPE_MEMBERSHIP = "MEMBERSHIP"
        const val TYPE_REG_REQUEST = "REG_REQUEST"
        const val TYPE_REGISTER = "REGISTER"
        const val TYPE_REVISION = "REVISION"
        const val TYPE_SEGMENT = "SEGMENT"
        const val TYPE_BC_ENTRY = "BC_ENTRY"
        const val TYPE_GLASS_GRID = "GLASS_GRID"
        const val TYPE_TACKCOAT_TEST = "TACKCOAT_TEST"
        const val TYPE_QUALITY_TEST = "QUALITY_TEST"
        const val TYPE_OBSERVATION = "OBSERVATION"
        const val TYPE_SIGNATURE = "SIGNATURE"
        const val TYPE_SHARING_CODE = "SHARING_CODE"
        const val TYPE_EDIT_REQUEST = "EDIT_REQUEST"
        const val TYPE_EDIT_PERMISSION = "EDIT_PERMISSION"
        const val TYPE_AUDIT_LOG = "AUDIT_LOG"

        private val KEY_SESSION_JSON = stringPreferencesKey("sr_session_json")
        private val KEY_PROFILE_JSON = stringPreferencesKey("sr_profile_json")
        private val KEY_ACTIVE_PROJECT_ID = stringPreferencesKey("sr_active_project_id")
        private val KEY_SETTINGS_JSON = stringPreferencesKey("sr_settings_json")
        private val KEY_LAST_SYNC_MS = longPreferencesKey("sr_last_sync_ms")
    }

    val pendingSyncCount: Flow<Int> = dao.observePendingSyncCount()

    val lastSyncTimeMs: Flow<Long> = context.siteRegisterDataStore.data.map { prefs ->
        prefs[KEY_LAST_SYNC_MS] ?: 0L
    }

    fun isNetworkOnline(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Throwable) {
            false
        }
    }

    // =========================================================================
    // SESSION, PROFILE & SETTINGS PERSISTENCE
    // =========================================================================

    suspend fun saveSession(session: UserSession?, profile: UserProfile?) {
        context.siteRegisterDataStore.edit { prefs ->
            if (session == null) {
                prefs.remove(KEY_SESSION_JSON)
            } else {
                prefs[KEY_SESSION_JSON] = JSONObject().apply {
                    put("accessToken", session.accessToken)
                    put("refreshToken", session.refreshToken)
                    put("expiresAtEpochMs", session.expiresAtEpochMs)
                    put("userId", session.userId)
                    put("email", session.email)
                    put("emailVerified", session.emailVerified)
                }.toString()
            }
            if (profile == null) {
                prefs.remove(KEY_PROFILE_JSON)
            } else {
                prefs[KEY_PROFILE_JSON] = profileToJson(profile).toString()
            }
        }
    }

    suspend fun loadSession(): Pair<UserSession?, UserProfile?> {
        val prefs = context.siteRegisterDataStore.data.first()
        val session = prefs[KEY_SESSION_JSON]?.let { raw ->
            try {
                val o = JSONObject(raw)
                UserSession(
                    accessToken = o.optString("accessToken"),
                    refreshToken = o.optString("refreshToken"),
                    expiresAtEpochMs = o.optLong("expiresAtEpochMs"),
                    userId = o.optString("userId"),
                    email = o.optString("email"),
                    emailVerified = o.optBoolean("emailVerified", true)
                )
            } catch (_: Throwable) { null }
        }
        val profile = prefs[KEY_PROFILE_JSON]?.let { raw ->
            try { jsonToProfile(JSONObject(raw)) } catch (_: Throwable) { null }
        }
        return session to profile
    }

    suspend fun saveActiveProjectId(projectId: String) {
        context.siteRegisterDataStore.edit { it[KEY_ACTIVE_PROJECT_ID] = projectId }
    }

    suspend fun loadActiveProjectId(): String =
        context.siteRegisterDataStore.data.first()[KEY_ACTIVE_PROJECT_ID].orEmpty()

    suspend fun saveSettings(settings: SiteRegisterSettings) {
        context.siteRegisterDataStore.edit { prefs ->
            prefs[KEY_SETTINGS_JSON] = JSONObject().apply {
                put("defaultProjectId", settings.defaultProjectId)
                put("defaultContractor", settings.defaultContractor)
                put("defaultShift", settings.defaultShift)
                put("defaultLane", settings.defaultLane)
                put("frequentActivityCodes", JSONArray(settings.frequentActivityCodes))
                put("defaultLengthUnit", settings.defaultLengthUnit.symbol)
                put("defaultDepthUnit", settings.defaultDepthUnit.symbol)
                put("decimalPrecision", settings.decimalPrecision)
                put("sharingCodeExpiryHours", settings.sharingCodeExpiryHours)
                put("includePhotosInPdf", settings.includePhotosInPdf)
                put("projectDensityByActivity", JSONObject(settings.projectDensityByActivity))
                put("specRefByActivity", JSONObject(settings.specRefByActivity))
                put("customTestTemplates", JSONArray(settings.customTestTemplates))
            }.toString()
        }
    }

    suspend fun loadSettings(): SiteRegisterSettings {
        val raw = context.siteRegisterDataStore.data.first()[KEY_SETTINGS_JSON] ?: return SiteRegisterSettings()
        return try {
            val o = JSONObject(raw)
            val freq = mutableListOf<String>()
            val freqArr = o.optJSONArray("frequentActivityCodes")
            if (freqArr != null) for (i in 0 until freqArr.length()) freq += freqArr.optString(i)
            val densities = mutableMapOf<String, Double>()
            o.optJSONObject("projectDensityByActivity")?.let { d ->
                d.keys().forEach { k -> densities[k] = d.optDouble(k) }
            }
            val specs = mutableMapOf<String, String>()
            o.optJSONObject("specRefByActivity")?.let { s ->
                s.keys().forEach { k -> specs[k] = s.optString(k) }
            }
            val customTests = mutableListOf<String>()
            o.optJSONArray("customTestTemplates")?.let { arr ->
                for (i in 0 until arr.length()) customTests += arr.optString(i)
            }
            SiteRegisterSettings(
                defaultProjectId = o.optString("defaultProjectId"),
                defaultContractor = o.optString("defaultContractor"),
                defaultShift = o.optString("defaultShift", "DAY"),
                defaultLane = o.optString("defaultLane", "MCW"),
                frequentActivityCodes = freq.ifEmpty { SiteRegisterSettings().frequentActivityCodes },
                defaultLengthUnit = MeasurementUnit.fromSymbol(o.optString("defaultLengthUnit", "m")),
                defaultDepthUnit = MeasurementUnit.fromSymbol(o.optString("defaultDepthUnit", "mm")),
                decimalPrecision = o.optInt("decimalPrecision", 3).coerceIn(1, 5),
                projectDensityByActivity = densities,
                specRefByActivity = specs,
                sharingCodeExpiryHours = o.optInt("sharingCodeExpiryHours", 24),
                includePhotosInPdf = o.optBoolean("includePhotosInPdf", true),
                customTestTemplates = customTests
            )
        } catch (_: Throwable) {
            SiteRegisterSettings()
        }
    }

    // =========================================================================
    // PROJECT CODE VALIDATION (REMOTE SUPABASE RPC FIRST, LOCAL CACHE FALLBACK)
    // =========================================================================

    suspend fun validateProjectCode(code: String, accessToken: String? = null): VerifiedProjectCodeResult {
        if (supabaseClient.isConfigured && isNetworkOnline()) {
            val remote = supabaseClient.validateProjectCodeRemote(code, accessToken)
            if (remote.isSuccess) {
                val res = remote.getOrThrow()
                if (res.isValid && res.project != null && res.codeRecord != null) {
                    saveProject(res.project, enqueueRemote = false)
                    saveProjectCode(res.codeRecord, enqueueRemote = false)
                }
                return res
            }
        }
        val localProjects = getProjects()
        val localCodes = getProjectCodes()
        return ApprovalWorkflowEngine.validateProjectCode(code, localCodes, localProjects)
    }

    // =========================================================================
    // CRUD OPERATIONS (OFFLINE-FIRST ROOM + IDEMPOTENT SYNC QUEUE)
    // =========================================================================

    fun observeProjects(): Flow<List<ProjectInfo>> =
        dao.observeByType(TYPE_PROJECT).map { list -> list.mapNotNull { runCatching { jsonToProject(JSONObject(it.payloadJson)) }.getOrNull() } }

    suspend fun getProjects(): List<ProjectInfo> =
        dao.getAllByType(TYPE_PROJECT).mapNotNull { runCatching { jsonToProject(JSONObject(it.payloadJson)) }.getOrNull() }

    suspend fun saveProject(project: ProjectInfo, enqueueRemote: Boolean = true) {
        val json = projectToJson(project)
        dao.upsertRecord(
            SiteRegisterLocalRecordEntity(
                id = project.id,
                recordType = TYPE_PROJECT,
                projectId = project.id,
                status = project.projectStatus,
                payloadJson = json.toString(),
                syncState = if (enqueueRemote) SyncState.PENDING_SYNC.name else SyncState.SYNCED.name
            )
        )
        if (enqueueRemote) {
            enqueueUpsert("projects", project.id, "", 1, json)
        }
    }

    fun observeProjectCodes(projectId: String): Flow<List<ProjectCodeRecord>> =
        dao.observeByTypeAndProject(TYPE_PROJECT_CODE, projectId).map { list ->
            list.mapNotNull { runCatching { jsonToProjectCode(JSONObject(it.payloadJson)) }.getOrNull() }
        }

    suspend fun getProjectCodes(): List<ProjectCodeRecord> =
        dao.getAllByType(TYPE_PROJECT_CODE).mapNotNull { runCatching { jsonToProjectCode(JSONObject(it.payloadJson)) }.getOrNull() }

    suspend fun saveProjectCode(code: ProjectCodeRecord, enqueueRemote: Boolean = true) {
        val json = projectCodeToJson(code)
        dao.upsertRecord(
            SiteRegisterLocalRecordEntity(
                id = code.id,
                recordType = TYPE_PROJECT_CODE,
                projectId = code.projectId,
                status = if (code.isRevoked) "REVOKED" else if (code.isEnabled) "ENABLED" else "DISABLED",
                payloadJson = json.toString(),
                syncState = if (enqueueRemote) SyncState.PENDING_SYNC.name else SyncState.SYNCED.name
            )
        )
        if (enqueueRemote) {
            enqueueUpsert("project_invitations", code.id, "", 1, json)
        }
    }

    fun observeAllMemberships(): Flow<List<ProjectMembership>> =
        dao.observeByType(TYPE_MEMBERSHIP).map { list -> list.mapNotNull { runCatching { jsonToMembership(JSONObject(it.payloadJson)) }.getOrNull() } }

    suspend fun saveMembership(membership: ProjectMembership, enqueueRemote: Boolean = true) {
        val json = membershipToJson(membership)
        dao.upsertRecord(
            SiteRegisterLocalRecordEntity(
                id = membership.id,
                recordType = TYPE_MEMBERSHIP,
                projectId = membership.projectId,
                parentId = membership.userId,
                status = membership.approvalStatus.dbValue,
                payloadJson = json.toString(),
                syncState = if (enqueueRemote) SyncState.PENDING_SYNC.name else SyncState.SYNCED.name
            )
        )
        if (enqueueRemote) {
            enqueueUpsert("project_members", membership.id, "", 1, json)
        }
    }

    fun observeRegistrationRequests(projectId: String): Flow<List<RegistrationRequestRecord>> =
        dao.observeByTypeAndProject(TYPE_REG_REQUEST, projectId).map { list ->
            list.mapNotNull { runCatching { jsonToRegRequest(JSONObject(it.payloadJson)) }.getOrNull() }
        }

    suspend fun saveRegistrationRequest(req: RegistrationRequestRecord, enqueueRemote: Boolean = true) {
        val json = regRequestToJson(req)
        dao.upsertRecord(
            SiteRegisterLocalRecordEntity(
                id = req.id,
                recordType = TYPE_REG_REQUEST,
                projectId = req.projectId,
                parentId = req.userId,
                status = req.status.dbValue,
                payloadJson = json.toString(),
                syncState = if (enqueueRemote) SyncState.PENDING_SYNC.name else SyncState.SYNCED.name
            )
        )
        if (enqueueRemote) {
            enqueueUpsert("registration_requests", req.id, "", 1, json)
        }
    }

    fun observeRegisters(projectId: String): Flow<List<SiteRegisterRecord>> =
        dao.observeByTypeAndProject(TYPE_REGISTER, projectId).map { list ->
            list.mapNotNull { entity ->
                runCatching {
                    jsonToRegister(JSONObject(entity.payloadJson))
                        .copy(syncState = runCatching { SyncState.valueOf(entity.syncState) }.getOrDefault(SyncState.PENDING_SYNC))
                }.getOrNull()
            }
        }

    suspend fun saveRegister(register: SiteRegisterRecord, enqueueRemote: Boolean = true) {
        val json = registerToJson(register)
        dao.upsertRecord(
            SiteRegisterLocalRecordEntity(
                id = register.id,
                recordType = TYPE_REGISTER,
                projectId = register.projectId,
                revisionNumber = register.currentRevision,
                status = register.status.dbValue,
                payloadJson = json.toString(),
                syncState = if (enqueueRemote) SyncState.PENDING_SYNC.name else SyncState.SYNCED.name,
                createdAtMs = register.createdAtMs,
                updatedAtMs = register.updatedAtMs
            )
        )
        if (enqueueRemote) {
            enqueueUpsert("site_registers", register.id, register.id, register.currentRevision, json)
        }
    }

    suspend fun deleteDraftRegister(registerId: String) {
        dao.deleteRecordById(registerId)
    }

    fun observeRevisions(registerId: String): Flow<List<SiteRegisterRevision>> =
        dao.observeByTypeAndParent(TYPE_REVISION, registerId).map { list ->
            list.mapNotNull { runCatching { jsonToRevision(JSONObject(it.payloadJson)) }.getOrNull() }
                .sortedByDescending { it.revisionNumber }
        }

    suspend fun saveRevision(revision: SiteRegisterRevision, enqueueRemote: Boolean = true) {
        val json = revisionToJson(revision)
        dao.upsertRecord(
            SiteRegisterLocalRecordEntity(
                id = revision.id,
                recordType = TYPE_REVISION,
                projectId = revision.projectId,
                parentId = revision.registerId,
                revisionNumber = revision.revisionNumber,
                status = revision.status.dbValue,
                payloadJson = json.toString(),
                syncState = if (enqueueRemote) SyncState.PENDING_SYNC.name else SyncState.SYNCED.name
            )
        )
        if (enqueueRemote) {
            enqueueUpsert("site_register_revisions", revision.id, revision.registerId, revision.revisionNumber, json)
        }
    }

    fun observeSegments(registerId: String): Flow<List<SiteSegmentRecord>> =
        dao.observeByTypeAndParent(TYPE_SEGMENT, registerId).map { list ->
            list.mapNotNull { runCatching { jsonToSegment(JSONObject(it.payloadJson)) }.getOrNull() }
                .sortedBy { it.segmentIndex }
        }

    suspend fun saveSegment(projectId: String, segment: SiteSegmentRecord, enqueueRemote: Boolean = true) {
        val json = segmentToJson(segment)
        dao.upsertRecord(
            SiteRegisterLocalRecordEntity(
                id = segment.id,
                recordType = TYPE_SEGMENT,
                projectId = projectId,
                parentId = segment.registerId,
                revisionNumber = segment.revisionNumber,
                payloadJson = json.toString(),
                syncState = if (enqueueRemote) SyncState.PENDING_SYNC.name else SyncState.SYNCED.name
            )
        )
        if (enqueueRemote) {
            enqueueUpsert("site_segments", segment.id, segment.registerId, segment.revisionNumber, json)
        }
    }

    fun observeBcEntries(registerId: String): Flow<List<BcLayingEntry>> =
        dao.observeByTypeAndParent(TYPE_BC_ENTRY, registerId).map { list ->
            list.mapNotNull { runCatching { jsonToBcEntry(JSONObject(it.payloadJson)) }.getOrNull() }
                .sortedBy { it.serialNumber }
        }

    suspend fun saveBcEntry(projectId: String, entry: BcLayingEntry, enqueueRemote: Boolean = true) {
        val json = bcEntryToJson(entry)
        dao.upsertRecord(
            SiteRegisterLocalRecordEntity(
                id = entry.id,
                recordType = TYPE_BC_ENTRY,
                projectId = projectId,
                parentId = entry.registerId,
                revisionNumber = entry.revisionNumber,
                payloadJson = json.toString(),
                syncState = if (enqueueRemote) SyncState.PENDING_SYNC.name else SyncState.SYNCED.name
            )
        )
        if (enqueueRemote) {
            enqueueUpsert("site_register_entries", entry.id, entry.registerId, entry.revisionNumber, json)
        }
    }

    fun observeGlassGridEntries(registerId: String): Flow<List<GlassGridEntry>> =
        dao.observeByTypeAndParent(TYPE_GLASS_GRID, registerId).map { list ->
            list.mapNotNull { runCatching { jsonToGlassGrid(JSONObject(it.payloadJson)) }.getOrNull() }
        }

    suspend fun saveGlassGridEntry(projectId: String, entry: GlassGridEntry, enqueueRemote: Boolean = true) {
        val json = glassGridToJson(entry)
        dao.upsertRecord(
            SiteRegisterLocalRecordEntity(
                id = entry.id,
                recordType = TYPE_GLASS_GRID,
                projectId = projectId,
                parentId = entry.registerId,
                revisionNumber = entry.revisionNumber,
                payloadJson = json.toString(),
                syncState = if (enqueueRemote) SyncState.PENDING_SYNC.name else SyncState.SYNCED.name
            )
        )
        if (enqueueRemote) {
            enqueueUpsert("glass_grid_entries", entry.id, entry.registerId, entry.revisionNumber, json)
        }
    }

    fun observeTackCoatTests(projectId: String): Flow<List<TackCoatTestRecord>> =
        dao.observeByTypeAndProject(TYPE_TACKCOAT_TEST, projectId).map { list ->
            list.mapNotNull { runCatching { jsonToTackCoatTest(JSONObject(it.payloadJson)) }.getOrNull() }
        }

    suspend fun saveTackCoatTest(test: TackCoatTestRecord, enqueueRemote: Boolean = true) {
        val json = tackCoatTestToJson(test)
        dao.upsertRecord(
            SiteRegisterLocalRecordEntity(
                id = test.id,
                recordType = TYPE_TACKCOAT_TEST,
                projectId = test.projectId,
                parentId = test.registerId.orEmpty(),
                payloadJson = json.toString(),
                syncState = if (enqueueRemote) SyncState.PENDING_SYNC.name else SyncState.SYNCED.name
            )
        )
        if (enqueueRemote) {
            enqueueUpsert("tackcoat_tests", test.id, test.registerId.orEmpty(), 1, json)
        }
    }

    fun observeQualityTests(projectId: String): Flow<List<QualityTestReportRecord>> =
        dao.observeByTypeAndProject(TYPE_QUALITY_TEST, projectId).map { list ->
            list.mapNotNull { runCatching { jsonToQualityTest(JSONObject(it.payloadJson)) }.getOrNull() }
        }

    suspend fun saveQualityTest(test: QualityTestReportRecord, enqueueRemote: Boolean = true) {
        val json = qualityTestToJson(test)
        dao.upsertRecord(
            SiteRegisterLocalRecordEntity(
                id = test.id,
                recordType = TYPE_QUALITY_TEST,
                projectId = test.projectId,
                parentId = test.registerId.orEmpty(),
                payloadJson = json.toString(),
                syncState = if (enqueueRemote) SyncState.PENDING_SYNC.name else SyncState.SYNCED.name
            )
        )
        if (enqueueRemote) {
            enqueueUpsert("quality_test_reports", test.id, test.registerId.orEmpty(), 1, json)
        }
    }

    fun observeObservations(projectId: String): Flow<List<SiteObservationRecord>> =
        dao.observeByTypeAndProject(TYPE_OBSERVATION, projectId).map { list ->
            list.mapNotNull { runCatching { jsonToObservation(JSONObject(it.payloadJson)) }.getOrNull() }
        }

    suspend fun saveObservation(obs: SiteObservationRecord, enqueueRemote: Boolean = true) {
        val json = observationToJson(obs)
        dao.upsertRecord(
            SiteRegisterLocalRecordEntity(
                id = obs.id,
                recordType = TYPE_OBSERVATION,
                projectId = obs.projectId,
                parentId = obs.registerId.orEmpty(),
                status = obs.status.dbValue,
                payloadJson = json.toString(),
                syncState = if (enqueueRemote) SyncState.PENDING_SYNC.name else SyncState.SYNCED.name
            )
        )
        if (enqueueRemote) {
            enqueueUpsert("site_observations", obs.id, obs.registerId.orEmpty(), 1, json)
        }
    }

    fun observeSignatures(registerId: String): Flow<List<SiteSignatureRecord>> =
        dao.observeByTypeAndParent(TYPE_SIGNATURE, registerId).map { list ->
            list.mapNotNull { runCatching { jsonToSignature(JSONObject(it.payloadJson)) }.getOrNull() }
        }

    suspend fun saveSignature(sig: SiteSignatureRecord, enqueueRemote: Boolean = true) {
        val json = signatureToJson(sig)
        dao.upsertRecord(
            SiteRegisterLocalRecordEntity(
                id = sig.id,
                recordType = TYPE_SIGNATURE,
                projectId = sig.projectId,
                parentId = sig.registerId,
                revisionNumber = sig.revisionNumber,
                status = sig.status.dbValue,
                payloadJson = json.toString(),
                syncState = if (enqueueRemote) SyncState.PENDING_SYNC.name else SyncState.SYNCED.name
            )
        )
        if (enqueueRemote) {
            enqueueUpsert("site_signatures", sig.id, sig.registerId, sig.revisionNumber, json)
        }
    }

    fun observeSharingCodes(projectId: String): Flow<List<RegisterSharingCode>> =
        dao.observeByTypeAndProject(TYPE_SHARING_CODE, projectId).map { list ->
            list.mapNotNull { runCatching { jsonToSharingCode(JSONObject(it.payloadJson)) }.getOrNull() }
        }

    suspend fun saveSharingCode(code: RegisterSharingCode, enqueueRemote: Boolean = true) {
        val json = sharingCodeToJson(code)
        dao.upsertRecord(
            SiteRegisterLocalRecordEntity(
                id = code.id,
                recordType = TYPE_SHARING_CODE,
                projectId = code.projectId,
                parentId = code.registerId,
                status = if (code.isRevoked) "REVOKED" else "ACTIVE",
                payloadJson = json.toString(),
                syncState = if (enqueueRemote) SyncState.PENDING_SYNC.name else SyncState.SYNCED.name
            )
        )
        if (enqueueRemote) {
            enqueueUpsert("site_sharing_codes", code.id, code.registerId, 1, json)
        }
    }

    fun observeEditRequests(projectId: String): Flow<List<EditPermissionRequest>> =
        dao.observeByTypeAndProject(TYPE_EDIT_REQUEST, projectId).map { list ->
            list.mapNotNull { runCatching { jsonToEditRequest(JSONObject(it.payloadJson)) }.getOrNull() }
        }

    suspend fun saveEditRequest(req: EditPermissionRequest, enqueueRemote: Boolean = true) {
        val json = editRequestToJson(req)
        dao.upsertRecord(
            SiteRegisterLocalRecordEntity(
                id = req.id,
                recordType = TYPE_EDIT_REQUEST,
                projectId = req.projectId,
                parentId = req.registerId,
                revisionNumber = req.revisionNumber,
                status = req.status.dbValue,
                payloadJson = json.toString(),
                syncState = if (enqueueRemote) SyncState.PENDING_SYNC.name else SyncState.SYNCED.name
            )
        )
        if (enqueueRemote) {
            enqueueUpsert("site_edit_requests", req.id, req.registerId, req.revisionNumber, json)
        }
    }

    fun observeEditPermissions(projectId: String): Flow<List<EditPermissionGrant>> =
        dao.observeByTypeAndProject(TYPE_EDIT_PERMISSION, projectId).map { list ->
            list.mapNotNull { runCatching { jsonToEditPermission(JSONObject(it.payloadJson)) }.getOrNull() }
        }

    suspend fun saveEditPermission(grant: EditPermissionGrant, enqueueRemote: Boolean = true) {
        val json = editPermissionToJson(grant)
        dao.upsertRecord(
            SiteRegisterLocalRecordEntity(
                id = grant.id,
                recordType = TYPE_EDIT_PERMISSION,
                projectId = grant.projectId,
                parentId = grant.registerId,
                revisionNumber = grant.targetRevisionNumber,
                status = if (grant.isActive) "ACTIVE" else "INACTIVE",
                payloadJson = json.toString(),
                syncState = if (enqueueRemote) SyncState.PENDING_SYNC.name else SyncState.SYNCED.name
            )
        )
        if (enqueueRemote) {
            enqueueUpsert("site_edit_permissions", grant.id, grant.registerId, grant.targetRevisionNumber, json)
        }
    }

    fun observeAuditLogs(projectId: String): Flow<List<SiteAuditEvent>> =
        dao.observeByTypeAndProject(TYPE_AUDIT_LOG, projectId).map { list ->
            list.mapNotNull { runCatching { jsonToAuditEvent(JSONObject(it.payloadJson)) }.getOrNull() }
                .sortedByDescending { it.timestampMs }
        }

    suspend fun appendAuditEvent(event: SiteAuditEvent, enqueueRemote: Boolean = true) {
        val json = auditEventToJson(event)
        dao.upsertRecord(
            SiteRegisterLocalRecordEntity(
                id = event.id,
                recordType = TYPE_AUDIT_LOG,
                projectId = event.projectId,
                parentId = event.registerId.orEmpty(),
                revisionNumber = event.revisionNumber ?: 0,
                status = event.action,
                payloadJson = json.toString(),
                syncState = if (enqueueRemote) SyncState.PENDING_SYNC.name else SyncState.SYNCED.name,
                createdAtMs = event.timestampMs,
                updatedAtMs = event.timestampMs
            )
        )
        if (enqueueRemote) {
            enqueueUpsert("site_audit_logs", event.id, event.registerId.orEmpty(), event.revisionNumber ?: 0, json)
        }
    }

    // =========================================================================
    // IDEMPOTENT OFFLINE-TO-SUPABASE SYNCHRONIZATION WITH PRE-PUSH LOCK GUARD
    // =========================================================================

    data class SyncSummary(
        val syncedCount: Int,
        val failedCount: Int,
        val blockedByLockCount: Int,
        val message: String
    )

    suspend fun synchronizePendingQueue(
        session: UserSession?,
        activeMembership: ProjectMembership?
    ): SyncSummary {
        if (!isNetworkOnline()) {
            return SyncSummary(0, 0, 0, "Offline — records safely queued locally.")
        }
        if (!supabaseClient.isConfigured || session == null || session.accessToken.isBlank()) {
            return SyncSummary(0, 0, 0, "Local sync ready — configure Supabase key & sign in for cloud sync.")
        }

        val pending = dao.getPendingSyncOperations()
        if (pending.isEmpty()) {
            context.siteRegisterDataStore.edit { it[KEY_LAST_SYNC_MS] = System.currentTimeMillis() }
            return SyncSummary(0, 0, 0, "All records are synchronized.")
        }

        var synced = 0
        var failed = 0
        var blocked = 0

        for (op in pending) {
            // Security Guard: Offline mode must NEVER bypass an Approved – Locked register!
            if (op.registerId.isNotBlank() && op.tableName in setOf(
                    "site_register_entries", "site_measurements", "site_segments", "glass_grid_entries"
                )
            ) {
                val localRegEntity = dao.getById(op.registerId)
                val localReg = localRegEntity?.let { runCatching { jsonToRegister(JSONObject(it.payloadJson)) }.getOrNull() }
                if (localReg != null) {
                    val perms = dao.getByTypeAndProject(TYPE_EDIT_PERMISSION, localReg.projectId)
                        .mapNotNull { runCatching { jsonToEditPermission(JSONObject(it.payloadJson)) }.getOrNull() }
                    val canEdit = ApprovalWorkflowEngine.canUserEditRegister(
                        register = localReg,
                        userId = session.userId,
                        membership = activeMembership,
                        activePermissions = perms
                    )
                    if (!canEdit) {
                        dao.markSyncFailed(
                            op.operationId,
                            "BLOCKED_LOCKED",
                            "Register revision ${localReg.currentRevision} is PMC-approved & locked. Request edit permission first."
                        )
                        dao.updateSyncState(op.recordId, SyncState.CONFLICT.name)
                        blocked++
                        continue
                    }
                }
            }

            val res = if (op.operationType == "RPC" && op.rpcName.isNotBlank()) {
                supabaseClient.callRpc(op.rpcName, JSONObject(op.payloadJson), session.accessToken)
            } else {
                supabaseClient.upsertTableRow(op.tableName, JSONObject(op.payloadJson), session.accessToken)
            }

            if (res.isSuccess) {
                dao.markSyncCompleted(op.operationId)
                dao.updateSyncState(op.recordId, SyncState.SYNCED.name)
                synced++
            } else {
                dao.markSyncFailed(op.operationId, "FAILED", res.exceptionOrNull()?.message ?: "Sync error")
                dao.updateSyncState(op.recordId, SyncState.FAILED.name)
                failed++
            }
        }

        if (synced > 0) {
            context.siteRegisterDataStore.edit { it[KEY_LAST_SYNC_MS] = System.currentTimeMillis() }
        }
        val msg = buildString {
            append("Synced $synced")
            if (failed > 0) append(" • $failed pending retry")
            if (blocked > 0) append(" • $blocked blocked by PMC lock")
        }
        return SyncSummary(synced, failed, blocked, msg)
    }

    private suspend fun enqueueUpsert(
        table: String,
        recordId: String,
        registerId: String,
        revisionNumber: Int,
        payload: JSONObject
    ) {
        dao.enqueueSync(
            SiteRegisterSyncQueueEntity(
                operationId = "$table:$recordId:$revisionNumber",
                tableName = table,
                recordId = recordId,
                registerId = registerId,
                revisionNumber = revisionNumber,
                operationType = "UPSERT",
                payloadJson = payload.toString()
            )
        )
    }

    // =========================================================================
    // JSON SERIALIZERS (EXPLICIT, SAFE, ZERO REFLECTION)
    // =========================================================================

    private fun profileToJson(p: UserProfile) = JSONObject().apply {
        put("id", p.id)
        put("full_name", p.fullName)
        put("username", p.username)
        put("email", p.email)
        put("mobile_number", p.mobileNumber)
        put("default_designation", p.defaultDesignation)
        put("default_organization_type", p.defaultOrganizationType.dbValue)
        put("default_organization_name", p.defaultOrganizationName)
        put("is_system_admin", p.isSystemAdmin)
        put("account_status", p.accountStatus.dbValue)
        put("email_verified", p.emailVerified)
        put("created_at_ms", p.createdAtMs)
    }

    private fun jsonToProfile(o: JSONObject) = UserProfile(
        id = o.optString("id"),
        fullName = o.optString("full_name"),
        username = o.optString("username"),
        email = o.optString("email"),
        mobileNumber = o.optString("mobile_number"),
        defaultDesignation = o.optString("default_designation"),
        defaultOrganizationType = OrganizationType.fromDb(o.optString("default_organization_type")),
        defaultOrganizationName = o.optString("default_organization_name"),
        isSystemAdmin = o.optBoolean("is_system_admin", false),
        accountStatus = AccountStatus.fromDb(o.optString("account_status")),
        emailVerified = o.optBoolean("email_verified", true),
        createdAtMs = o.optLong("created_at_ms", System.currentTimeMillis())
    )

    private fun projectToJson(p: ProjectInfo) = JSONObject().apply {
        put("id", p.id)
        put("project_name", p.projectName)
        put("highway_name", p.highwayName)
        put("nh_sh_number", p.nhShNumber)
        put("package_number", p.packageNumber)
        put("project_location", p.projectLocation)
        put("state_name", p.stateName)
        put("contractor_name", p.contractorName)
        put("concessionaire_name", p.concessionaireName)
        put("client_name", p.clientName)
        put("pmc_organization", p.pmcOrganization)
        put("ie_organization", p.ieOrganization)
        put("assigned_pmc_name", p.assignedPmcName)
        put("assigned_pmc_contact", p.assignedPmcContact)
        put("project_identification", p.projectIdentification)
        put("project_status", p.projectStatus)
        put("registration_status", p.registrationStatus)
        put("permitted_organization_types", JSONArray(p.permittedOrganizationTypes.map { it.dbValue }))
        put("created_by", p.createdBy)
        put("created_at_ms", p.createdAtMs)
    }

    private fun jsonToProject(o: JSONObject): ProjectInfo {
        val permitted = mutableListOf<OrganizationType>()
        o.optJSONArray("permitted_organization_types")?.let { arr ->
            for (i in 0 until arr.length()) permitted += OrganizationType.fromDb(arr.optString(i))
        }
        return ProjectInfo(
            id = o.optString("id"),
            projectName = o.optString("project_name"),
            highwayName = o.optString("highway_name"),
            nhShNumber = o.optString("nh_sh_number"),
            packageNumber = o.optString("package_number"),
            projectLocation = o.optString("project_location"),
            stateName = o.optString("state_name"),
            contractorName = o.optString("contractor_name"),
            concessionaireName = o.optString("concessionaire_name"),
            clientName = o.optString("client_name"),
            pmcOrganization = o.optString("pmc_organization"),
            ieOrganization = o.optString("ie_organization"),
            assignedPmcName = o.optString("assigned_pmc_name"),
            assignedPmcContact = o.optString("assigned_pmc_contact"),
            projectIdentification = o.optString("project_identification"),
            projectStatus = o.optString("project_status", "ACTIVE"),
            registrationStatus = o.optString("registration_status", "OPEN"),
            permittedOrganizationTypes = permitted.ifEmpty { OrganizationType.entries },
            createdBy = o.optString("created_by"),
            createdAtMs = o.optLong("created_at_ms", System.currentTimeMillis())
        )
    }

    private fun projectCodeToJson(c: ProjectCodeRecord) = JSONObject().apply {
        put("id", c.id)
        put("project_id", c.projectId)
        put("project_code", c.projectCode)
        put("permitted_organization_types", JSONArray(c.permittedOrganizationTypes.map { it.dbValue }))
        if (c.maxRegistrations != null) put("max_registrations", c.maxRegistrations)
        put("current_registrations", c.currentRegistrations)
        if (c.expiresAtMs != null) put("expires_at_ms", c.expiresAtMs)
        put("is_enabled", c.isEnabled)
        put("is_revoked", c.isRevoked)
        put("created_by", c.createdBy)
        put("created_at_ms", c.createdAtMs)
    }

    private fun jsonToProjectCode(o: JSONObject): ProjectCodeRecord {
        val permitted = mutableListOf<OrganizationType>()
        o.optJSONArray("permitted_organization_types")?.let { arr ->
            for (i in 0 until arr.length()) permitted += OrganizationType.fromDb(arr.optString(i))
        }
        return ProjectCodeRecord(
            id = o.optString("id"),
            projectId = o.optString("project_id"),
            projectCode = o.optString("project_code"),
            permittedOrganizationTypes = permitted.ifEmpty { OrganizationType.entries },
            maxRegistrations = if (o.has("max_registrations")) o.optInt("max_registrations") else null,
            currentRegistrations = o.optInt("current_registrations", 0),
            expiresAtMs = if (o.has("expires_at_ms")) o.optLong("expires_at_ms") else null,
            isEnabled = o.optBoolean("is_enabled", true),
            isRevoked = o.optBoolean("is_revoked", false),
            createdBy = o.optString("created_by"),
            createdAtMs = o.optLong("created_at_ms", System.currentTimeMillis())
        )
    }

    private fun membershipToJson(m: ProjectMembership) = JSONObject().apply {
        put("id", m.id)
        put("project_id", m.projectId)
        put("user_id", m.userId)
        put("project_name", m.projectName)
        put("organization_type", m.organizationType.dbValue)
        put("organization_name", m.organizationName)
        put("organization_verified", m.organizationVerified)
        put("designation", m.designation)
        put("assigned_role", m.assignedRole.dbValue)
        put("approval_status", m.approvalStatus.dbValue)
        put("project_code_used", m.projectCodeUsed)
        if (m.approvedBy != null) put("approved_by", m.approvedBy)
        if (m.approvedAtMs != null) put("approved_at_ms", m.approvedAtMs)
        put("remarks", m.remarks)
        put("created_at_ms", m.createdAtMs)
    }

    private fun jsonToMembership(o: JSONObject) = ProjectMembership(
        id = o.optString("id"),
        projectId = o.optString("project_id"),
        userId = o.optString("user_id"),
        projectName = o.optString("project_name"),
        organizationType = OrganizationType.fromDb(o.optString("organization_type")),
        organizationName = o.optString("organization_name"),
        organizationVerified = o.optBoolean("organization_verified", false),
        designation = o.optString("designation"),
        assignedRole = ProjectRole.fromDb(o.optString("assigned_role")),
        approvalStatus = MembershipApprovalStatus.fromDb(o.optString("approval_status")),
        projectCodeUsed = o.optString("project_code_used"),
        approvedBy = o.optString("approved_by").takeIf { it.isNotBlank() },
        approvedAtMs = if (o.has("approved_at_ms")) o.optLong("approved_at_ms") else null,
        remarks = o.optString("remarks"),
        createdAtMs = o.optLong("created_at_ms", System.currentTimeMillis())
    )

    private fun regRequestToJson(r: RegistrationRequestRecord) = JSONObject().apply {
        put("id", r.id)
        put("project_id", r.projectId)
        put("project_name", r.projectName)
        put("user_id", r.userId)
        put("project_code_used", r.projectCodeUsed)
        put("full_name", r.fullName)
        put("username", r.username)
        put("email", r.email)
        put("mobile_number", r.mobileNumber)
        put("requested_organization_type", r.requestedOrganizationType.dbValue)
        put("requested_organization_name", r.requestedOrganizationName)
        put("is_unlisted_organization", r.isUnlistedOrganization)
        put("organization_verified", r.organizationVerified)
        put("requested_designation", r.requestedDesignation)
        put("status", r.status.dbValue)
        if (r.assignedRole != null) put("assigned_role", r.assignedRole.dbValue)
        put("pmc_remarks", r.pmcRemarks)
        if (r.reviewedBy != null) put("reviewed_by", r.reviewedBy)
        if (r.reviewedAtMs != null) put("reviewed_at_ms", r.reviewedAtMs)
        put("created_at_ms", r.createdAtMs)
    }

    private fun jsonToRegRequest(o: JSONObject) = RegistrationRequestRecord(
        id = o.optString("id"),
        projectId = o.optString("project_id"),
        projectName = o.optString("project_name"),
        userId = o.optString("user_id"),
        projectCodeUsed = o.optString("project_code_used"),
        fullName = o.optString("full_name"),
        username = o.optString("username"),
        email = o.optString("email"),
        mobileNumber = o.optString("mobile_number"),
        requestedOrganizationType = OrganizationType.fromDb(o.optString("requested_organization_type")),
        requestedOrganizationName = o.optString("requested_organization_name"),
        isUnlistedOrganization = o.optBoolean("is_unlisted_organization", false),
        organizationVerified = o.optBoolean("organization_verified", false),
        requestedDesignation = o.optString("requested_designation"),
        status = MembershipApprovalStatus.fromDb(o.optString("status")),
        assignedRole = o.optString("assigned_role").takeIf { it.isNotBlank() }?.let { ProjectRole.fromDb(it) },
        pmcRemarks = o.optString("pmc_remarks"),
        reviewedBy = o.optString("reviewed_by").takeIf { it.isNotBlank() },
        reviewedAtMs = if (o.has("reviewed_at_ms")) o.optLong("reviewed_at_ms") else null,
        createdAtMs = o.optLong("created_at_ms", System.currentTimeMillis())
    )

    private fun registerToJson(r: SiteRegisterRecord) = JSONObject().apply {
        put("id", r.id)
        put("project_id", r.projectId)
        put("project_name", r.projectName)
        put("contractor_name", r.contractorName)
        put("highway_name", r.highwayName)
        put("package_location", r.packageLocation)
        put("activity_code", r.activityCode)
        put("activity_name", r.activityName)
        put("activity_category", r.activityCategory.dbValue)
        put("template_version", r.templateVersion)
        put("register_number", r.registerNumber)
        put("work_date", r.workDate)
        put("shift", r.shift)
        put("side", r.side)
        put("lane", r.lane)
        put("chainage_from", r.chainageFrom)
        put("chainage_to", r.chainageTo)
        put("current_revision", r.currentRevision)
        put("status", r.status.dbValue)
        put("total_length_m", r.totalLengthM)
        put("total_area_sqm", r.totalAreaSqm)
        put("total_volume_cum", r.totalVolumeCum)
        if (r.totalMaterialQty != null) put("total_material_qty", r.totalMaterialQty)
        put("total_material_unit", r.totalMaterialUnit.symbol)
        put("quantity_classification", r.quantityClassification.dbValue)
        put("remarks", r.remarks)
        put("created_by", r.createdBy)
        put("created_by_name", r.createdByName)
        if (r.submittedAtMs != null) put("submitted_at_ms", r.submittedAtMs)
        if (r.approvedAtMs != null) put("approved_at_ms", r.approvedAtMs)
        if (r.approvedBy != null) put("approved_by", r.approvedBy)
        if (r.approvedByName != null) put("approved_by_name", r.approvedByName)
        put("created_at_ms", r.createdAtMs)
        put("updated_at_ms", r.updatedAtMs)
    }

    private fun jsonToRegister(o: JSONObject) = SiteRegisterRecord(
        id = o.optString("id"),
        projectId = o.optString("project_id"),
        projectName = o.optString("project_name"),
        contractorName = o.optString("contractor_name"),
        highwayName = o.optString("highway_name"),
        packageLocation = o.optString("package_location"),
        activityCode = o.optString("activity_code"),
        activityName = o.optString("activity_name"),
        activityCategory = runCatching { HighwayActivityCategory.valueOf(o.optString("activity_category")) }.getOrDefault(HighwayActivityCategory.BITUMINOUS_WORKS),
        templateVersion = o.optInt("template_version", 1),
        registerNumber = o.optString("register_number"),
        workDate = o.optString("work_date"),
        shift = o.optString("shift", "DAY"),
        side = o.optString("side", "RHS"),
        lane = o.optString("lane", "MCW"),
        chainageFrom = o.optString("chainage_from"),
        chainageTo = o.optString("chainage_to"),
        currentRevision = o.optInt("current_revision", 1),
        status = RegisterStatus.fromDb(o.optString("status")),
        totalLengthM = o.optDouble("total_length_m", 0.0),
        totalAreaSqm = o.optDouble("total_area_sqm", 0.0),
        totalVolumeCum = o.optDouble("total_volume_cum", 0.0),
        totalMaterialQty = if (o.has("total_material_qty") && !o.isNull("total_material_qty")) o.optDouble("total_material_qty") else null,
        totalMaterialUnit = MeasurementUnit.fromSymbol(o.optString("total_material_unit", "MT")),
        quantityClassification = QuantityClassification.fromDb(o.optString("quantity_classification")),
        remarks = o.optString("remarks"),
        createdBy = o.optString("created_by"),
        createdByName = o.optString("created_by_name"),
        submittedAtMs = if (o.has("submitted_at_ms")) o.optLong("submitted_at_ms") else null,
        approvedAtMs = if (o.has("approved_at_ms")) o.optLong("approved_at_ms") else null,
        approvedBy = o.optString("approved_by").takeIf { it.isNotBlank() },
        approvedByName = o.optString("approved_by_name").takeIf { it.isNotBlank() },
        createdAtMs = o.optLong("created_at_ms", System.currentTimeMillis()),
        updatedAtMs = o.optLong("updated_at_ms", System.currentTimeMillis())
    )

    private fun revisionToJson(r: SiteRegisterRevision) = JSONObject().apply {
        put("id", r.id)
        put("register_id", r.registerId)
        put("project_id", r.projectId)
        put("revision_number", r.revisionNumber)
        put("status", r.status.dbValue)
        put("is_locked", r.isLocked)
        put("change_summary", r.changeSummary)
        put("snapshot_json", r.snapshotJson)
        put("created_by", r.createdBy)
        if (r.approvedBy != null) put("approved_by", r.approvedBy)
        if (r.approvedAtMs != null) put("approved_at_ms", r.approvedAtMs)
        put("created_at_ms", r.createdAtMs)
    }

    private fun jsonToRevision(o: JSONObject) = SiteRegisterRevision(
        id = o.optString("id"),
        registerId = o.optString("register_id"),
        projectId = o.optString("project_id"),
        revisionNumber = o.optInt("revision_number", 1),
        status = RegisterStatus.fromDb(o.optString("status")),
        isLocked = o.optBoolean("is_locked", false),
        changeSummary = o.optString("change_summary"),
        snapshotJson = o.optString("snapshot_json", "{}"),
        createdBy = o.optString("created_by"),
        approvedBy = o.optString("approved_by").takeIf { it.isNotBlank() },
        approvedAtMs = if (o.has("approved_at_ms")) o.optLong("approved_at_ms") else null,
        createdAtMs = o.optLong("created_at_ms", System.currentTimeMillis())
    )

    private fun segmentToJson(s: SiteSegmentRecord) = JSONObject().apply {
        put("id", s.id)
        put("register_id", s.registerId)
        put("revision_number", s.revisionNumber)
        put("segment_index", s.segmentIndex)
        put("from_chainage", s.fromChainage)
        put("to_chainage", s.toChainage)
        put("side", s.side)
        put("lane", s.lane)
        s.startWidthM?.let { put("start_width_m", it) }
        s.middleWidthM?.let { put("middle_width_m", it) }
        s.endWidthM?.let { put("end_width_m", it) }
        s.actualWidthM?.let { put("actual_width_m", it) }
        s.designThickness?.let { put("design_thickness", it) }
        s.looseThickness?.let { put("loose_thickness", it) }
        s.actualCompactedThickness?.let { put("actual_compacted_thickness", it) }
        s.verifiedThickness?.let { put("verified_thickness", it) }
        put("thickness_unit", s.thicknessUnit.symbol)
        s.verifiedDensity?.let { put("verified_density", it) }
        s.densityUnit?.let { put("density_unit", it) }
        put("measurement_method", s.measurementMethod.dbValue)
        s.crossSectionArea1Sqm?.let { put("cross_section_area_1", it) }
        s.crossSectionArea2Sqm?.let { put("cross_section_area_2", it) }
        s.surveyedAreaSqm?.let { put("surveyed_area_sqm", it) }
        put("structural_element_type", s.structuralElementType)
        put("unit_count", s.unitCount)
        put("remarks", s.remarks)
        put("created_at_ms", s.createdAtMs)
    }

    private fun jsonToSegment(o: JSONObject) = SiteSegmentRecord(
        id = o.optString("id"),
        registerId = o.optString("register_id"),
        revisionNumber = o.optInt("revision_number", 1),
        segmentIndex = o.optInt("segment_index", 1),
        fromChainage = o.optString("from_chainage"),
        toChainage = o.optString("to_chainage"),
        side = o.optString("side", "RHS"),
        lane = o.optString("lane", "MCW"),
        startWidthM = optDoubleOrNull(o, "start_width_m"),
        middleWidthM = optDoubleOrNull(o, "middle_width_m"),
        endWidthM = optDoubleOrNull(o, "end_width_m"),
        actualWidthM = optDoubleOrNull(o, "actual_width_m"),
        designThickness = optDoubleOrNull(o, "design_thickness"),
        looseThickness = optDoubleOrNull(o, "loose_thickness"),
        actualCompactedThickness = optDoubleOrNull(o, "actual_compacted_thickness"),
        verifiedThickness = optDoubleOrNull(o, "verified_thickness"),
        thicknessUnit = MeasurementUnit.fromSymbol(o.optString("thickness_unit", "mm")),
        verifiedDensity = optDoubleOrNull(o, "verified_density"),
        densityUnit = o.optString("density_unit").takeIf { it.isNotBlank() },
        measurementMethod = MeasurementMethod.fromDb(o.optString("measurement_method")),
        crossSectionArea1Sqm = optDoubleOrNull(o, "cross_section_area_1"),
        crossSectionArea2Sqm = optDoubleOrNull(o, "cross_section_area_2"),
        surveyedAreaSqm = optDoubleOrNull(o, "surveyed_area_sqm"),
        structuralElementType = o.optString("structural_element_type"),
        unitCount = o.optInt("unit_count", 1),
        remarks = o.optString("remarks"),
        createdAtMs = o.optLong("created_at_ms", System.currentTimeMillis())
    )

    private fun bcEntryToJson(e: BcLayingEntry) = JSONObject().apply {
        put("id", e.id)
        put("register_id", e.registerId)
        put("revision_number", e.revisionNumber)
        put("serial_number", e.serialNumber)
        put("vehicle_number", e.vehicleNumber)
        put("bc_type", e.bcType)
        put("layer", e.layer)
        put("from_chainage", e.fromChainage)
        put("to_chainage", e.toChainage)
        put("side", e.side)
        put("lane", e.lane)
        put("start_time", e.startTime)
        put("end_time", e.endTime)
        e.layingTempC?.let { put("laying_temp_c", it) }
        e.rollingTempC?.let { put("rolling_temp_c", it) }
        e.startWidthM?.let { put("start_width_m", it) }
        e.middleWidthM?.let { put("middle_width_m", it) }
        e.endWidthM?.let { put("end_width_m", it) }
        e.actualLaidLengthM?.let { put("actual_laid_length_m", it) }
        e.designThicknessMm?.let { put("design_thickness_mm", it) }
        e.looseThicknessMm?.let { put("loose_thickness_mm", it) }
        e.compactedThicknessMm?.let { put("compacted_thickness_mm", it) }
        e.measuredMaterialMt?.let { put("measured_material_mt", it) }
        e.verifiedDensityMtPerCum?.let { put("verified_density_mt_per_cum", it) }
        put("remarks", e.remarks)
        put("recorded_at_ms", e.recordedAtMs)
    }

    private fun jsonToBcEntry(o: JSONObject) = BcLayingEntry(
        id = o.optString("id"),
        registerId = o.optString("register_id"),
        revisionNumber = o.optInt("revision_number", 1),
        serialNumber = o.optInt("serial_number", 1),
        vehicleNumber = o.optString("vehicle_number"),
        bcType = o.optString("bc_type"),
        layer = o.optString("layer"),
        fromChainage = o.optString("from_chainage"),
        toChainage = o.optString("to_chainage"),
        side = o.optString("side", "RHS"),
        lane = o.optString("lane", "MCW"),
        startTime = o.optString("start_time"),
        endTime = o.optString("end_time"),
        layingTempC = optDoubleOrNull(o, "laying_temp_c"),
        rollingTempC = optDoubleOrNull(o, "rolling_temp_c"),
        startWidthM = optDoubleOrNull(o, "start_width_m"),
        middleWidthM = optDoubleOrNull(o, "middle_width_m"),
        endWidthM = optDoubleOrNull(o, "end_width_m"),
        actualLaidLengthM = optDoubleOrNull(o, "actual_laid_length_m"),
        designThicknessMm = optDoubleOrNull(o, "design_thickness_mm"),
        looseThicknessMm = optDoubleOrNull(o, "loose_thickness_mm"),
        compactedThicknessMm = optDoubleOrNull(o, "compacted_thickness_mm"),
        measuredMaterialMt = optDoubleOrNull(o, "measured_material_mt"),
        verifiedDensityMtPerCum = optDoubleOrNull(o, "verified_density_mt_per_cum"),
        remarks = o.optString("remarks"),
        recordedAtMs = o.optLong("recorded_at_ms", System.currentTimeMillis())
    )

    private fun glassGridToJson(g: GlassGridEntry) = JSONObject().apply {
        put("id", g.id)
        put("register_id", g.registerId)
        put("revision_number", g.revisionNumber)
        put("from_chainage", g.fromChainage)
        put("to_chainage", g.toChainage)
        put("application_length_m", g.applicationLengthM)
        put("application_width_m", g.applicationWidthM)
        put("roll_width_m", g.rollWidthM)
        put("number_of_rolls", g.numberOfRolls)
        put("overlap_width_m", g.overlapWidthM)
        put("application_side", g.applicationSide)
        put("start_time", g.startTime)
        put("end_time", g.endTime)
        put("surface_preparation", g.surfacePreparation)
        put("tack_coat_status", g.tackCoatStatus)
        g.ptrPasses?.let { put("ptr_passes", it) }
        put("wrinkles_alignment", g.wrinklesAlignment)
        put("remarks", g.remarks)
        put("created_at_ms", g.createdAtMs)
    }

    private fun jsonToGlassGrid(o: JSONObject) = GlassGridEntry(
        id = o.optString("id"),
        registerId = o.optString("register_id"),
        revisionNumber = o.optInt("revision_number", 1),
        fromChainage = o.optString("from_chainage"),
        toChainage = o.optString("to_chainage"),
        applicationLengthM = o.optDouble("application_length_m", 0.0),
        applicationWidthM = o.optDouble("application_width_m", 0.0),
        rollWidthM = o.optDouble("roll_width_m", 0.0),
        numberOfRolls = o.optInt("number_of_rolls", 1),
        overlapWidthM = o.optDouble("overlap_width_m", 0.0),
        applicationSide = o.optString("application_side", "RHS"),
        startTime = o.optString("start_time"),
        endTime = o.optString("end_time"),
        surfacePreparation = o.optString("surface_preparation"),
        tackCoatStatus = o.optString("tack_coat_status"),
        ptrPasses = if (o.has("ptr_passes")) o.optInt("ptr_passes") else null,
        wrinklesAlignment = o.optString("wrinkles_alignment"),
        remarks = o.optString("remarks"),
        createdAtMs = o.optLong("created_at_ms", System.currentTimeMillis())
    )

    private fun tackCoatTestToJson(t: TackCoatTestRecord) = JSONObject().apply {
        put("id", t.id)
        t.registerId?.let { put("register_id", it) }
        put("project_id", t.projectId)
        put("project_name", t.projectName)
        put("contractor_name", t.contractorName)
        put("independent_engineer", t.independentEngineer)
        put("pmc_consultant", t.pmcConsultant)
        put("project_manager", t.projectManager)
        put("location", t.location)
        put("chainage", t.chainage)
        put("side", t.side)
        put("material_type", t.materialType)
        put("purpose", t.purpose)
        put("test_date", t.testDate)
        put("weather", t.weather)
        put("tested_by", t.testedBy)
        t.temperatureC?.let { put("temperature_c", it) }
        t.specMinRateKgSqm?.let { put("spec_min", it) }
        t.specMaxRateKgSqm?.let { put("spec_max", it) }
        put("spec_reference", t.specReference)
        put("trays", JSONArray().apply {
            t.trays.forEach { row ->
                put(JSONObject().apply {
                    put("id", row.id)
                    put("tray_number", row.trayNumber)
                    put("tray_area_sqm", row.trayAreaSqm)
                    put("weight_before_kg", row.weightBeforeKg)
                    put("weight_after_kg", row.weightAfterKg)
                    put("remarks", row.remarks)
                })
            }
        })
        put("photo_uris", JSONArray(t.photoUris))
        put("remarks", t.remarks)
        put("created_by", t.createdBy)
        put("created_at_ms", t.createdAtMs)
    }

    private fun jsonToTackCoatTest(o: JSONObject): TackCoatTestRecord {
        val trays = mutableListOf<TackCoatTrayRow>()
        o.optJSONArray("trays")?.let { arr ->
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                trays += TackCoatTrayRow(
                    id = r.optString("id", UUID.randomUUID().toString()),
                    trayNumber = r.optInt("tray_number", i + 1),
                    trayAreaSqm = r.optDouble("tray_area_sqm", 0.0),
                    weightBeforeKg = r.optDouble("weight_before_kg", 0.0),
                    weightAfterKg = r.optDouble("weight_after_kg", 0.0),
                    remarks = r.optString("remarks")
                )
            }
        }
        return TackCoatTestRecord(
            id = o.optString("id"),
            registerId = o.optString("register_id").takeIf { it.isNotBlank() },
            projectId = o.optString("project_id"),
            projectName = o.optString("project_name"),
            contractorName = o.optString("contractor_name"),
            independentEngineer = o.optString("independent_engineer"),
            pmcConsultant = o.optString("pmc_consultant"),
            projectManager = o.optString("project_manager"),
            location = o.optString("location"),
            chainage = o.optString("chainage"),
            side = o.optString("side", "RHS"),
            materialType = o.optString("material_type"),
            purpose = o.optString("purpose"),
            testDate = o.optString("test_date"),
            weather = o.optString("weather"),
            testedBy = o.optString("tested_by"),
            temperatureC = optDoubleOrNull(o, "temperature_c"),
            specMinRateKgSqm = optDoubleOrNull(o, "spec_min"),
            specMaxRateKgSqm = optDoubleOrNull(o, "spec_max"),
            specReference = o.optString("spec_reference"),
            trays = trays,
            photoUris = jsonArrayToStrings(o.optJSONArray("photo_uris")),
            remarks = o.optString("remarks"),
            createdBy = o.optString("created_by"),
            createdAtMs = o.optLong("created_at_ms", System.currentTimeMillis())
        )
    }

    private fun qualityTestToJson(t: QualityTestReportRecord) = JSONObject().apply {
        put("id", t.id)
        t.registerId?.let { put("register_id", it) }
        put("project_id", t.projectId)
        put("category", t.category.name)
        put("test_title", t.testTitle)
        put("test_date", t.testDate)
        put("chainage", t.chainage)
        put("side", t.side)
        put("sample_location", t.sampleLocation)
        put("tested_by", t.testedBy)
        put("measured_values", JSONObject(t.measuredValues))
        put("spec_criteria", t.specCriteria)
        put("result_status", t.resultStatus.dbValue)
        put("photo_uris", JSONArray(t.photoUris))
        put("ocr_extracted_text", t.ocrExtractedText)
        t.ocrConfidence?.let { put("ocr_confidence", it.toDouble()) }
        put("ocr_verified_manually", t.ocrVerifiedManually)
        put("remarks", t.remarks)
        put("created_by", t.createdBy)
        put("created_at_ms", t.createdAtMs)
    }

    private fun jsonToQualityTest(o: JSONObject): QualityTestReportRecord {
        val vals = mutableMapOf<String, String>()
        o.optJSONObject("measured_values")?.let { mv ->
            mv.keys().forEach { k -> vals[k] = mv.optString(k) }
        }
        return QualityTestReportRecord(
            id = o.optString("id"),
            registerId = o.optString("register_id").takeIf { it.isNotBlank() },
            projectId = o.optString("project_id"),
            category = runCatching { QualityTestCategory.valueOf(o.optString("category")) }.getOrDefault(QualityTestCategory.CUSTOM),
            testTitle = o.optString("test_title"),
            testDate = o.optString("test_date"),
            chainage = o.optString("chainage"),
            side = o.optString("side", "RHS"),
            sampleLocation = o.optString("sample_location"),
            testedBy = o.optString("tested_by"),
            measuredValues = vals,
            specCriteria = o.optString("spec_criteria"),
            resultStatus = TestResultStatus.fromDb(o.optString("result_status")),
            photoUris = jsonArrayToStrings(o.optJSONArray("photo_uris")),
            ocrExtractedText = o.optString("ocr_extracted_text"),
            ocrConfidence = optDoubleOrNull(o, "ocr_confidence")?.toFloat(),
            ocrVerifiedManually = o.optBoolean("ocr_verified_manually", false),
            remarks = o.optString("remarks"),
            createdBy = o.optString("created_by"),
            createdAtMs = o.optLong("created_at_ms", System.currentTimeMillis())
        )
    }

    private fun observationToJson(obs: SiteObservationRecord) = JSONObject().apply {
        put("id", obs.id)
        obs.registerId?.let { put("register_id", it) }
        put("project_id", obs.projectId)
        put("serial_number", obs.serialNumber)
        put("observed_at_ms", obs.observedAtMs)
        put("activity_name", obs.activityName)
        put("chainage_from", obs.chainageFrom)
        put("chainage_to", obs.chainageTo)
        put("side", obs.side)
        put("lane", obs.lane)
        obs.affectedLengthM?.let { put("affected_length_m", it) }
        obs.affectedWidthM?.let { put("affected_width_m", it) }
        obs.affectedDepthM?.let { put("affected_depth_m", it) }
        obs.affectedAreaSqm?.let { put("affected_area_sqm", it) }
        obs.affectedVolumeCum?.let { put("affected_volume_cum", it) }
        put("category", obs.category.name)
        put("description", obs.description)
        put("severity", obs.severity.name)
        put("before_photo_uris", JSONArray(obs.beforePhotoUris))
        put("after_photo_uris", JSONArray(obs.afterPhotoUris))
        put("corrective_action", obs.correctiveAction)
        put("responsible_contractor", obs.responsibleContractor)
        put("target_date", obs.targetDate)
        put("status", obs.status.dbValue)
        put("verified_by_name", obs.verifiedByName)
        obs.verificationDateMs?.let { put("verification_date_ms", it) }
        put("final_remarks", obs.finalRemarks)
        put("created_by", obs.createdBy)
    }

    private fun jsonToObservation(o: JSONObject) = SiteObservationRecord(
        id = o.optString("id"),
        registerId = o.optString("register_id").takeIf { it.isNotBlank() },
        projectId = o.optString("project_id"),
        serialNumber = o.optInt("serial_number", 1),
        observedAtMs = o.optLong("observed_at_ms", System.currentTimeMillis()),
        activityName = o.optString("activity_name"),
        chainageFrom = o.optString("chainage_from"),
        chainageTo = o.optString("chainage_to"),
        side = o.optString("side", "RHS"),
        lane = o.optString("lane", "MCW"),
        affectedLengthM = optDoubleOrNull(o, "affected_length_m"),
        affectedWidthM = optDoubleOrNull(o, "affected_width_m"),
        affectedDepthM = optDoubleOrNull(o, "affected_depth_m"),
        affectedAreaSqm = optDoubleOrNull(o, "affected_area_sqm"),
        affectedVolumeCum = optDoubleOrNull(o, "affected_volume_cum"),
        category = runCatching { ObservationCategory.valueOf(o.optString("category")) }.getOrDefault(ObservationCategory.OTHER),
        description = o.optString("description"),
        severity = runCatching { ObservationSeverity.valueOf(o.optString("severity")) }.getOrDefault(ObservationSeverity.MEDIUM),
        beforePhotoUris = jsonArrayToStrings(o.optJSONArray("before_photo_uris")),
        afterPhotoUris = jsonArrayToStrings(o.optJSONArray("after_photo_uris")),
        correctiveAction = o.optString("corrective_action"),
        responsibleContractor = o.optString("responsible_contractor"),
        targetDate = o.optString("target_date"),
        status = RectificationStatus.fromDb(o.optString("status")),
        verifiedByName = o.optString("verified_by_name"),
        verificationDateMs = if (o.has("verification_date_ms")) o.optLong("verification_date_ms") else null,
        finalRemarks = o.optString("final_remarks"),
        createdBy = o.optString("created_by")
    )

    private fun signatureToJson(s: SiteSignatureRecord) = JSONObject().apply {
        put("id", s.id)
        put("register_id", s.registerId)
        put("project_id", s.projectId)
        put("revision_number", s.revisionNumber)
        put("signer_user_id", s.signerUserId)
        put("signer_name", s.signerName)
        put("signer_designation", s.signerDesignation)
        put("signer_organization", s.signerOrganization)
        put("signer_role", s.signerRole.dbValue)
        put("signature_points", s.signaturePointsSerialized)
        put("status", s.status.dbValue)
        put("signed_at_ms", s.signedAtMs)
        put("remarks", s.remarks)
    }

    private fun jsonToSignature(o: JSONObject) = SiteSignatureRecord(
        id = o.optString("id"),
        registerId = o.optString("register_id"),
        projectId = o.optString("project_id"),
        revisionNumber = o.optInt("revision_number", 1),
        signerUserId = o.optString("signer_user_id"),
        signerName = o.optString("signer_name"),
        signerDesignation = o.optString("signer_designation"),
        signerOrganization = o.optString("signer_organization"),
        signerRole = ProjectRole.fromDb(o.optString("signer_role")),
        signaturePointsSerialized = o.optString("signature_points"),
        status = SignatureStatus.fromDb(o.optString("status")),
        signedAtMs = o.optLong("signed_at_ms", System.currentTimeMillis()),
        remarks = o.optString("remarks")
    )

    private fun sharingCodeToJson(c: RegisterSharingCode) = JSONObject().apply {
        put("id", c.id)
        put("register_id", c.registerId)
        put("project_id", c.projectId)
        put("code", c.code)
        c.expiresAtMs?.let { put("expires_at_ms", it) }
        put("is_revoked", c.isRevoked)
        put("authorized_users", JSONArray(c.authorizedUserNames))
        put("created_by", c.createdBy)
        put("created_at_ms", c.createdAtMs)
    }

    private fun jsonToSharingCode(o: JSONObject) = RegisterSharingCode(
        id = o.optString("id"),
        registerId = o.optString("register_id"),
        projectId = o.optString("project_id"),
        code = o.optString("code"),
        expiresAtMs = if (o.has("expires_at_ms")) o.optLong("expires_at_ms") else null,
        isRevoked = o.optBoolean("is_revoked", false),
        authorizedUserNames = jsonArrayToStrings(o.optJSONArray("authorized_users")),
        createdBy = o.optString("created_by"),
        createdAtMs = o.optLong("created_at_ms", System.currentTimeMillis())
    )

    private fun editRequestToJson(r: EditPermissionRequest) = JSONObject().apply {
        put("id", r.id)
        put("register_id", r.registerId)
        put("project_id", r.projectId)
        put("revision_number", r.revisionNumber)
        put("requested_by", r.requestedBy)
        put("requested_by_name", r.requestedByName)
        put("reason", r.reason)
        put("proposed_correction", r.proposedCorrection)
        put("supporting_evidence", r.supportingEvidence)
        put("status", r.status.dbValue)
        r.reviewedBy?.let { put("reviewed_by", it) }
        put("pmc_remarks", r.pmcRemarks)
        put("created_at_ms", r.createdAtMs)
    }

    private fun jsonToEditRequest(o: JSONObject) = EditPermissionRequest(
        id = o.optString("id"),
        registerId = o.optString("register_id"),
        projectId = o.optString("project_id"),
        revisionNumber = o.optInt("revision_number", 1),
        requestedBy = o.optString("requested_by"),
        requestedByName = o.optString("requested_by_name"),
        reason = o.optString("reason"),
        proposedCorrection = o.optString("proposed_correction"),
        supportingEvidence = o.optString("supporting_evidence"),
        status = EditRequestStatus.fromDb(o.optString("status")),
        reviewedBy = o.optString("reviewed_by").takeIf { it.isNotBlank() },
        pmcRemarks = o.optString("pmc_remarks"),
        createdAtMs = o.optLong("created_at_ms", System.currentTimeMillis())
    )

    private fun editPermissionToJson(g: EditPermissionGrant) = JSONObject().apply {
        put("id", g.id)
        put("edit_request_id", g.editRequestId)
        put("register_id", g.registerId)
        put("project_id", g.projectId)
        put("target_revision_number", g.targetRevisionNumber)
        put("authorized_user_id", g.authorizedUserId)
        put("granted_by_pmc_id", g.grantedByPmcId)
        put("granted_at_ms", g.grantedAtMs)
        put("expires_at_ms", g.expiresAtMs)
        put("is_active", g.isActive)
        g.revokedAtMs?.let { put("revoked_at_ms", it) }
        g.consumedAtMs?.let { put("consumed_at_ms", it) }
    }

    private fun jsonToEditPermission(o: JSONObject) = EditPermissionGrant(
        id = o.optString("id"),
        editRequestId = o.optString("edit_request_id"),
        registerId = o.optString("register_id"),
        projectId = o.optString("project_id"),
        targetRevisionNumber = o.optInt("target_revision_number", 1),
        authorizedUserId = o.optString("authorized_user_id"),
        grantedByPmcId = o.optString("granted_by_pmc_id"),
        grantedAtMs = o.optLong("granted_at_ms", System.currentTimeMillis()),
        expiresAtMs = o.optLong("expires_at_ms", System.currentTimeMillis()),
        isActive = o.optBoolean("is_active", false),
        revokedAtMs = if (o.has("revoked_at_ms")) o.optLong("revoked_at_ms") else null,
        consumedAtMs = if (o.has("consumed_at_ms")) o.optLong("consumed_at_ms") else null
    )

    private fun auditEventToJson(a: SiteAuditEvent) = JSONObject().apply {
        put("id", a.id)
        put("project_id", a.projectId)
        a.registerId?.let { put("register_id", it) }
        a.revisionNumber?.let { put("revision_number", it) }
        put("user_id", a.userId)
        put("user_name", a.userName)
        put("organization_type", a.organizationType)
        put("assigned_role", a.assignedRole)
        put("action", a.action)
        put("before_summary", a.beforeSummary)
        put("after_summary", a.afterSummary)
        put("timestamp_ms", a.timestampMs)
    }

    private fun jsonToAuditEvent(o: JSONObject) = SiteAuditEvent(
        id = o.optString("id"),
        projectId = o.optString("project_id"),
        registerId = o.optString("register_id").takeIf { it.isNotBlank() },
        revisionNumber = if (o.has("revision_number")) o.optInt("revision_number") else null,
        userId = o.optString("user_id"),
        userName = o.optString("user_name"),
        organizationType = o.optString("organization_type"),
        assignedRole = o.optString("assigned_role"),
        action = o.optString("action"),
        beforeSummary = o.optString("before_summary"),
        afterSummary = o.optString("after_summary"),
        timestampMs = o.optLong("timestamp_ms", System.currentTimeMillis())
    )

    private fun optDoubleOrNull(o: JSONObject, key: String): Double? =
        if (o.has(key) && !o.isNull(key)) o.optDouble(key).takeIf { it.isFinite() } else null

    private fun jsonArrayToStrings(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
    }
}
