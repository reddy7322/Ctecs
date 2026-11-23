package com.example.siteregister.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.siteregister.data.SiteRegisterRepository
import com.example.siteregister.engine.ApprovalWorkflowEngine
import com.example.siteregister.engine.HighwayMeasurementEngine
import com.example.siteregister.export.SiteRegisterExportEngine
import com.example.siteregister.model.AccountStatus
import com.example.siteregister.model.BcLayingEntry
import com.example.siteregister.model.EditPermissionGrant
import com.example.siteregister.model.EditPermissionRequest
import com.example.siteregister.model.EditRequestStatus
import com.example.siteregister.model.GlassGridEntry
import com.example.siteregister.model.HighwayActivityCatalog
import com.example.siteregister.model.HighwayActivityCategory
import com.example.siteregister.model.HighwayActivityTemplate
import com.example.siteregister.model.LoginAccessRoute
import com.example.siteregister.model.MeasurementUnit
import com.example.siteregister.model.MembershipApprovalStatus
import com.example.siteregister.model.OrganizationType
import com.example.siteregister.model.ProjectCodeRecord
import com.example.siteregister.model.ProjectInfo
import com.example.siteregister.model.ProjectMembership
import com.example.siteregister.model.ProjectRole
import com.example.siteregister.model.QuantityClassification
import com.example.siteregister.model.QualityTestReportRecord
import com.example.siteregister.model.RectificationStatus
import com.example.siteregister.model.RegisterSharingCode
import com.example.siteregister.model.RegisterStatus
import com.example.siteregister.model.RegistrationRequestRecord
import com.example.siteregister.model.SiteAuditEvent
import com.example.siteregister.model.SiteObservationRecord
import com.example.siteregister.model.SiteRegisterRecord
import com.example.siteregister.model.SiteRegisterRevision
import com.example.siteregister.model.SiteRegisterSettings
import com.example.siteregister.model.SiteSegmentRecord
import com.example.siteregister.model.SiteSignatureRecord
import com.example.siteregister.model.SyncState
import com.example.siteregister.model.TackCoatTestRecord
import com.example.siteregister.model.UserProfile
import com.example.siteregister.model.UserSession
import com.example.siteregister.model.VerifiedProjectCodeResult
import com.example.siteregister.util.QrCodeAndOcrHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class SiteRegisterNavSection {
    DASHBOARD,
    ACTIVITY_PICKER,
    REGISTER_DETAIL,
    QUALITY_TESTS,
    OBSERVATIONS,
    PMC_ADMIN,
    AUDIT_HISTORY,
    EXPORT_CENTER,
    SETTINGS
}

data class SiteRegisterDashboardKpis(
    val totalRegisters: Int = 0,
    val todayEntries: Int = 0,
    val totalLengthM: Double = 0.0,
    val totalAreaSqm: Double = 0.0,
    val totalVolumeCum: Double = 0.0,
    val totalMaterialMt: Double = 0.0,
    val pendingSubmissions: Int = 0,
    val pmcPendingApprovals: Int = 0,
    val approvedRegisters: Int = 0,
    val pendingEditRequests: Int = 0,
    val qualityTestReports: Int = 0,
    val openObservations: Int = 0,
    val rectifiedObservations: Int = 0
)

data class SiteRegisterUiState(
    val isLoading: Boolean = false,
    val session: UserSession? = null,
    val profile: UserProfile? = null,
    val accessRoute: LoginAccessRoute = LoginAccessRoute.UNAUTHENTICATED,
    val isSupabaseConfigured: Boolean = false,
    val isOnline: Boolean = false,
    val isRealtimeLive: Boolean = false,
    val pendingSyncCount: Int = 0,
    val lastSyncTimeMs: Long = 0L,

    // Auth / Registration flow
    val verifiedCodeResult: VerifiedProjectCodeResult? = null,
    val isVerifyingCode: Boolean = false,
    val showRegistrationScreen: Boolean = false,
    val showCreatePmcProjectBootstrap: Boolean = false,

    // Multi-project context
    val projects: List<ProjectInfo> = emptyList(),
    val userMemberships: List<ProjectMembership> = emptyList(),
    val activeProject: ProjectInfo? = null,
    val activeMembership: ProjectMembership? = null,
    val projectCodes: List<ProjectCodeRecord> = emptyList(),
    val registrationRequests: List<RegistrationRequestRecord> = emptyList(),

    // Navigation & filters
    val currentSection: SiteRegisterNavSection = SiteRegisterNavSection.DASHBOARD,
    val selectedDateFilter: String = "", // empty = all dates, or YYYY-MM-DD
    val selectedShiftFilter: String = "ALL", // ALL, DAY, NIGHT
    val selectedActivityFilter: String = "ALL",
    val searchQuery: String = "",

    // Project workspace records
    val registers: List<SiteRegisterRecord> = emptyList(),
    val selectedRegisterId: String? = null,
    val activeRegisterRevisions: List<SiteRegisterRevision> = emptyList(),
    val activeRegisterSegments: List<SiteSegmentRecord> = emptyList(),
    val activeRegisterBcEntries: List<BcLayingEntry> = emptyList(),
    val activeRegisterGlassGrid: List<GlassGridEntry> = emptyList(),
    val activeRegisterSignatures: List<SiteSignatureRecord> = emptyList(),
    val tackCoatTests: List<TackCoatTestRecord> = emptyList(),
    val qualityTests: List<QualityTestReportRecord> = emptyList(),
    val observations: List<SiteObservationRecord> = emptyList(),
    val sharingCodes: List<RegisterSharingCode> = emptyList(),
    val editRequests: List<EditPermissionRequest> = emptyList(),
    val editPermissions: List<EditPermissionGrant> = emptyList(),
    val auditLogs: List<SiteAuditEvent> = emptyList(),

    // Read-only shared view (via Live Sharing Code)
    val sharedViewRegisterId: String? = null,
    val sharedViewCodeLabel: String? = null,

    // OCR assistant state
    val lastOcrResult: QrCodeAndOcrHelper.OcrExtractionResult? = null,

    // Export & settings
    val lastExportResult: SiteRegisterExportEngine.ExportResult? = null,
    val isExporting: Boolean = false,
    val settings: SiteRegisterSettings = SiteRegisterSettings(),

    // Feedback
    val bannerMessage: String? = null,
    val bannerIsError: Boolean = false
) {
    val activeRegister: SiteRegisterRecord?
        get() = registers.firstOrNull { it.id == (selectedRegisterId ?: sharedViewRegisterId) }

    val allActivities: List<HighwayActivityTemplate>
        get() = HighwayActivityCatalog.standardActivities + settings.customActivities

    val canEditActiveRegister: Boolean
        get() {
            val reg = activeRegister ?: return false
            val uid = session?.userId ?: return false
            if (sharedViewRegisterId == reg.id) return false // Sharing code NEVER grants edit rights
            return ApprovalWorkflowEngine.canUserEditRegister(
                register = reg,
                userId = uid,
                membership = activeMembership,
                activePermissions = editPermissions
            )
        }

    val canDeleteActiveRegister: Boolean
        get() {
            val reg = activeRegister ?: return false
            val uid = session?.userId ?: return false
            return ApprovalWorkflowEngine.canUserDeleteRegister(reg, uid, activeMembership)
        }

    val filteredRegisters: List<SiteRegisterRecord>
        get() = registers.filter { r ->
            (selectedDateFilter.isBlank() || r.workDate == selectedDateFilter) &&
                (selectedShiftFilter == "ALL" || r.shift.equals(selectedShiftFilter, true)) &&
                (selectedActivityFilter == "ALL" || r.activityCode.equals(selectedActivityFilter, true)) &&
                (searchQuery.isBlank() ||
                    r.activityName.contains(searchQuery, true) ||
                    r.registerNumber.contains(searchQuery, true) ||
                    r.chainageFrom.contains(searchQuery, true) ||
                    r.chainageTo.contains(searchQuery, true))
        }

    val kpis: SiteRegisterDashboardKpis
        get() {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            return SiteRegisterDashboardKpis(
                totalRegisters = registers.size,
                todayEntries = registers.count { it.workDate == today },
                totalLengthM = registers.sumOf { it.totalLengthM },
                totalAreaSqm = registers.sumOf { it.totalAreaSqm },
                totalVolumeCum = registers.sumOf { it.totalVolumeCum },
                totalMaterialMt = registers.sumOf { if (it.totalMaterialUnit == MeasurementUnit.MT) (it.totalMaterialQty ?: 0.0) else 0.0 },
                pendingSubmissions = registers.count { it.status == RegisterStatus.DRAFT || it.status == RegisterStatus.REJECTED },
                pmcPendingApprovals = registers.count {
                    it.status == RegisterStatus.SUBMITTED ||
                        it.status == RegisterStatus.PENDING_PMC_APPROVAL ||
                        it.status == RegisterStatus.RESUBMITTED
                },
                approvedRegisters = registers.count { it.status == RegisterStatus.APPROVED_LOCKED },
                pendingEditRequests = editRequests.count { it.status == EditRequestStatus.PENDING },
                qualityTestReports = tackCoatTests.size + qualityTests.size,
                openObservations = observations.count { it.status == RectificationStatus.OPEN || it.status == RectificationStatus.UNDER_RECTIFICATION },
                rectifiedObservations = observations.count {
                    it.status == RectificationStatus.RECTIFIED ||
                        it.status == RectificationStatus.VERIFIED ||
                        it.status == RectificationStatus.CLOSED
                }
            )
        }
}

class SiteRegisterViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).siteRegisterDao()
    val repository = SiteRegisterRepository(application.applicationContext, dao)

    private val _uiState = MutableStateFlow(
        SiteRegisterUiState(
            isSupabaseConfigured = repository.supabaseClient.isConfigured,
            isOnline = repository.isNetworkOnline()
        )
    )
    val uiState: StateFlow<SiteRegisterUiState> = _uiState.asStateFlow()

    private var projectObserversJob: Job? = null
    private var registerObserversJob: Job? = null
    private var realtimeJob: Job? = null

    init {
        bootstrapState()
    }

    private fun bootstrapState() {
        viewModelScope.launch {
            val (session, profile) = repository.loadSession()
            val settings = repository.loadSettings()
            val savedProjectId = repository.loadActiveProjectId()
            _uiState.update {
                it.copy(
                    session = session,
                    profile = profile,
                    settings = settings,
                    isOnline = repository.isNetworkOnline(),
                    isSupabaseConfigured = repository.supabaseClient.isConfigured
                )
            }
            observeGlobalStores(savedProjectId)
        }

        viewModelScope.launch {
            repository.pendingSyncCount.collectLatest { count ->
                _uiState.update { it.copy(pendingSyncCount = count) }
            }
        }
        viewModelScope.launch {
            repository.lastSyncTimeMs.collectLatest { ts ->
                _uiState.update { it.copy(lastSyncTimeMs = ts) }
            }
        }
    }

    private fun observeGlobalStores(preferredProjectId: String) {
        viewModelScope.launch {
            repository.observeProjects().collectLatest { projects ->
                _uiState.update { state ->
                    val resolvedActive = state.activeProject?.let { cur -> projects.firstOrNull { it.id == cur.id } }
                        ?: projects.firstOrNull { it.id == preferredProjectId }
                    state.copy(projects = projects, activeProject = resolvedActive)
                }
                recomputeAccessRoute()
            }
        }
        viewModelScope.launch {
            repository.observeAllMemberships().collectLatest { allMemberships ->
                val uid = _uiState.value.session?.userId
                val mine = if (uid != null) allMemberships.filter { it.userId == uid } else emptyList()
                _uiState.update { it.copy(userMemberships = mine) }
                recomputeAccessRoute(preferredProjectId)
            }
        }
    }

    private fun recomputeAccessRoute(preferredProjectId: String = "") {
        val state = _uiState.value
        val route = ApprovalWorkflowEngine.resolveLoginAccessRoute(
            session = state.session,
            profile = state.profile,
            memberships = state.userMemberships
        )
        val approved = state.userMemberships.filter { it.approvalStatus == MembershipApprovalStatus.APPROVED }
        val chosenMembership = state.activeMembership?.takeIf { cur -> approved.any { it.id == cur.id } }
            ?: approved.firstOrNull { it.projectId == preferredProjectId }
            ?: if (approved.size == 1) approved.first() else null

        val chosenProject = chosenMembership?.let { m ->
            state.projects.firstOrNull { it.id == m.projectId }
        } ?: state.activeProject

        val effectiveRoute = if (route == LoginAccessRoute.PROJECT_SELECTOR && chosenMembership != null && state.activeProject != null) {
            LoginAccessRoute.PROJECT_DASHBOARD
        } else {
            route
        }

        val projectChanged = chosenProject?.id != state.activeProject?.id
        _uiState.update {
            it.copy(
                accessRoute = effectiveRoute,
                activeMembership = chosenMembership,
                activeProject = chosenProject
            )
        }
        if (chosenProject != null && (projectChanged || projectObserversJob == null)) {
            observeProjectWorkspace(chosenProject.id)
        }
    }

    private fun observeProjectWorkspace(projectId: String) {
        projectObserversJob?.cancel()
        projectObserversJob = viewModelScope.launch {
            launch {
                repository.observeProjectCodes(projectId).collectLatest { list ->
                    _uiState.update { it.copy(projectCodes = list) }
                }
            }
            launch {
                repository.observeRegistrationRequests(projectId).collectLatest { list ->
                    _uiState.update { it.copy(registrationRequests = list.sortedByDescending { r -> r.createdAtMs }) }
                }
            }
            launch {
                repository.observeRegisters(projectId).collectLatest { list ->
                    val perms = _uiState.value.editPermissions
                    val normalized = list.map { reg ->
                        val eff = ApprovalWorkflowEngine.resolveEffectiveRegisterStatus(reg, perms)
                        if (eff != reg.status) reg.copy(status = eff) else reg
                    }.sortedByDescending { it.updatedAtMs }
                    _uiState.update { it.copy(registers = normalized) }
                }
            }
            launch {
                repository.observeTackCoatTests(projectId).collectLatest { list ->
                    _uiState.update { it.copy(tackCoatTests = list.sortedByDescending { t -> t.createdAtMs }) }
                }
            }
            launch {
                repository.observeQualityTests(projectId).collectLatest { list ->
                    _uiState.update { it.copy(qualityTests = list.sortedByDescending { t -> t.createdAtMs }) }
                }
            }
            launch {
                repository.observeObservations(projectId).collectLatest { list ->
                    _uiState.update { it.copy(observations = list.sortedByDescending { o -> o.observedAtMs }) }
                }
            }
            launch {
                repository.observeSharingCodes(projectId).collectLatest { list ->
                    _uiState.update { it.copy(sharingCodes = list.sortedByDescending { c -> c.createdAtMs }) }
                }
            }
            launch {
                repository.observeEditRequests(projectId).collectLatest { list ->
                    _uiState.update { it.copy(editRequests = list.sortedByDescending { r -> r.createdAtMs }) }
                }
            }
            launch {
                repository.observeEditPermissions(projectId).collectLatest { list ->
                    _uiState.update { state ->
                        val updatedRegs = state.registers.map { reg ->
                            val eff = ApprovalWorkflowEngine.resolveEffectiveRegisterStatus(reg, list)
                            if (eff != reg.status) reg.copy(status = eff) else reg
                        }
                        state.copy(editPermissions = list, registers = updatedRegs)
                    }
                }
            }
            launch {
                repository.observeAuditLogs(projectId).collectLatest { list ->
                    _uiState.update { it.copy(auditLogs = list) }
                }
            }
        }
        startRealtimeSubscription(projectId)
    }

    private fun startRealtimeSubscription(projectId: String) {
        realtimeJob?.cancel()
        val token = _uiState.value.session?.accessToken.orEmpty()
        if (!repository.supabaseClient.isConfigured || token.isBlank() || !repository.isNetworkOnline()) {
            _uiState.update { it.copy(isRealtimeLive = false) }
            return
        }
        _uiState.update { it.copy(isRealtimeLive = true) }
        realtimeJob = viewModelScope.launch {
            repository.supabaseClient.observeProjectRealtimeChanges(projectId, token)
                .catch { _uiState.update { s -> s.copy(isRealtimeLive = false) } }
                .collectLatest { event ->
                    showBanner("Live update received (${event.table}: ${event.eventType})", isError = false)
                }
        }
    }

    private fun observeRegisterDetails(registerId: String) {
        registerObserversJob?.cancel()
        registerObserversJob = viewModelScope.launch {
            launch {
                repository.observeRevisions(registerId).collectLatest { list ->
                    _uiState.update { it.copy(activeRegisterRevisions = list) }
                }
            }
            launch {
                repository.observeSegments(registerId).collectLatest { list ->
                    _uiState.update { it.copy(activeRegisterSegments = list) }
                }
            }
            launch {
                repository.observeBcEntries(registerId).collectLatest { list ->
                    _uiState.update { it.copy(activeRegisterBcEntries = list) }
                }
            }
            launch {
                repository.observeGlassGridEntries(registerId).collectLatest { list ->
                    _uiState.update { it.copy(activeRegisterGlassGrid = list) }
                }
            }
            launch {
                repository.observeSignatures(registerId).collectLatest { list ->
                    _uiState.update { it.copy(activeRegisterSignatures = list) }
                }
            }
        }
    }

    // =========================================================================
    // AUTHENTICATION, PROJECT CODE VERIFICATION & REGISTRATION
    // =========================================================================

    fun showRegistrationFlow(show: Boolean) {
        _uiState.update {
            it.copy(
                showRegistrationScreen = show,
                showCreatePmcProjectBootstrap = false,
                verifiedCodeResult = null,
                bannerMessage = null
            )
        }
    }

    fun showCreatePmcBootstrap(show: Boolean) {
        _uiState.update { it.copy(showCreatePmcProjectBootstrap = show, bannerMessage = null) }
    }

    fun clearBanner() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    private fun showBanner(message: String, isError: Boolean) {
        _uiState.update { it.copy(bannerMessage = message, bannerIsError = isError) }
    }

    fun verifyProjectCode(rawCode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isVerifyingCode = true, verifiedCodeResult = null, bannerMessage = null) }
            val result = repository.validateProjectCode(rawCode, _uiState.value.session?.accessToken)
            _uiState.update {
                it.copy(
                    isVerifyingCode = false,
                    verifiedCodeResult = result,
                    bannerMessage = if (!result.isValid) result.errorMessage else null,
                    bannerIsError = !result.isValid
                )
            }
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            showBanner("Please enter both email and password.", isError = true)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, bannerMessage = null) }
            if (repository.supabaseClient.isConfigured && repository.isNetworkOnline()) {
                val res = repository.supabaseClient.signInWithPassword(email, password)
                res.onSuccess { session ->
                    val existingProfile = _uiState.value.profile?.takeIf { it.email.equals(email.trim(), true) }
                        ?: UserProfile(
                            id = session.userId,
                            fullName = email.substringBefore("@").replace('.', ' ').replaceFirstChar { it.uppercase() },
                            username = email.substringBefore("@"),
                            email = session.email,
                            emailVerified = session.emailVerified
                        )
                    repository.saveSession(session, existingProfile)
                    _uiState.update { it.copy(isLoading = false, session = session, profile = existingProfile) }
                    refreshUserMembershipsFor(session.userId)
                }.onFailure { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    showBanner(err.message ?: "Login failed.", isError = true)
                }
            } else {
                // Offline-capable field workspace session using deterministic UUID per email
                val uid = UUID.nameUUIDFromBytes(email.trim().lowercase(Locale.US).toByteArray()).toString()
                val session = UserSession(
                    accessToken = "offline-session-$uid",
                    refreshToken = "",
                    expiresAtEpochMs = System.currentTimeMillis() + 30L * 24 * 3600 * 1000L,
                    userId = uid,
                    email = email.trim(),
                    emailVerified = true
                )
                val profile = UserProfile(
                    id = uid,
                    fullName = email.substringBefore("@").replace('.', ' ').replaceFirstChar { it.uppercase() },
                    username = email.substringBefore("@"),
                    email = email.trim(),
                    emailVerified = true
                )
                repository.saveSession(session, profile)
                _uiState.update { it.copy(isLoading = false, session = session, profile = profile) }
                refreshUserMembershipsFor(uid)
                if (!repository.supabaseClient.isConfigured) {
                    showBanner("Signed in (Offline-First Mode). Add SUPABASE_PUBLISHABLE_KEY in Secrets panel for cloud sync.", isError = false)
                }
            }
        }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            showBanner("Enter your registered email address first.", isError = true)
            return
        }
        viewModelScope.launch {
            val res = repository.supabaseClient.sendPasswordReset(email)
            res.onSuccess {
                showBanner("Password reset link sent to $email.", isError = false)
            }.onFailure {
                showBanner(it.message ?: "Unable to send password reset email.", isError = true)
            }
        }
    }

    /**
     * Project-Code-first Registration.
     * Selecting PMC or any organization NEVER self-approves or grants privileges;
     * always creates a PENDING registration request & PENDING membership awaiting PMC approval.
     */
    fun registerWithVerifiedProjectCode(
        fullName: String,
        username: String,
        email: String,
        mobile: String,
        password: String,
        confirmPassword: String,
        organizationType: OrganizationType,
        organizationName: String,
        isUnlistedOrganization: Boolean,
        designation: String
    ) {
        val verified = _uiState.value.verifiedCodeResult
        if (verified == null || !verified.isValid || verified.project == null || verified.codeRecord == null) {
            showBanner("Please verify a valid PMC Project Code first.", isError = true)
            return
        }
        if (fullName.isBlank() || username.isBlank() || email.isBlank() || designation.isBlank() || organizationName.isBlank()) {
            showBanner("Please complete all required registration fields.", isError = true)
            return
        }
        if (password.length < 8 || password != confirmPassword) {
            showBanner("Password must be at least 8 characters and match confirmation.", isError = true)
            return
        }
        if (organizationType !in verified.codeRecord.permittedOrganizationTypes) {
            showBanner("${organizationType.displayLabel} is not permitted for this Project Code.", isError = true)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, bannerMessage = null) }
            val sessionResult: Result<UserSession> = if (repository.supabaseClient.isConfigured && repository.isNetworkOnline()) {
                repository.supabaseClient.signUp(email, password, fullName, username)
            } else {
                val uid = UUID.nameUUIDFromBytes(email.trim().lowercase(Locale.US).toByteArray()).toString()
                Result.success(
                    UserSession(
                        accessToken = "offline-session-$uid",
                        refreshToken = "",
                        expiresAtEpochMs = System.currentTimeMillis() + 30L * 24 * 3600 * 1000L,
                        userId = uid,
                        email = email.trim(),
                        emailVerified = true
                    )
                )
            }

            sessionResult.onSuccess { session ->
                val profile = UserProfile(
                    id = session.userId,
                    fullName = fullName.trim(),
                    username = username.trim(),
                    email = email.trim(),
                    mobileNumber = mobile.trim(),
                    defaultDesignation = designation.trim(),
                    defaultOrganizationType = organizationType,
                    defaultOrganizationName = organizationName.trim(),
                    accountStatus = AccountStatus.ACTIVE,
                    emailVerified = session.emailVerified
                )
                repository.saveSession(session, profile)

                val project = verified.project
                val codeRecord = verified.codeRecord

                // Increment code usage
                repository.saveProjectCode(
                    codeRecord.copy(currentRegistrations = codeRecord.currentRegistrations + 1)
                )

                // Always PENDING — never auto-approved or elevated
                val membership = ProjectMembership(
                    projectId = project.id,
                    userId = session.userId,
                    projectName = project.projectName,
                    organizationType = organizationType,
                    organizationName = organizationName.trim(),
                    organizationVerified = !isUnlistedOrganization,
                    designation = designation.trim(),
                    assignedRole = ProjectRole.CLIENT_VIEWER, // Placeholder until PMC explicitly assigns role
                    approvalStatus = MembershipApprovalStatus.PENDING,
                    projectCodeUsed = codeRecord.projectCode
                )
                repository.saveMembership(membership)

                val request = RegistrationRequestRecord(
                    projectId = project.id,
                    projectName = project.projectName,
                    userId = session.userId,
                    projectCodeUsed = codeRecord.projectCode,
                    fullName = fullName.trim(),
                    username = username.trim(),
                    email = email.trim(),
                    mobileNumber = mobile.trim(),
                    requestedOrganizationType = organizationType,
                    requestedOrganizationName = organizationName.trim(),
                    isUnlistedOrganization = isUnlistedOrganization,
                    organizationVerified = !isUnlistedOrganization,
                    requestedDesignation = designation.trim(),
                    status = MembershipApprovalStatus.PENDING
                )
                repository.saveRegistrationRequest(request)

                repository.appendAuditEvent(
                    SiteAuditEvent(
                        projectId = project.id,
                        userId = session.userId,
                        userName = profile.fullName,
                        organizationType = organizationType.dbValue,
                        assignedRole = "PENDING",
                        action = "USER_REGISTRATION_SUBMITTED",
                        afterSummary = "Code ${codeRecord.projectCode} | Org: ${organizationName.trim()} (${organizationType.displayLabel}) | Desig: ${designation.trim()}"
                    )
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        session = session,
                        profile = profile,
                        showRegistrationScreen = false,
                        verifiedCodeResult = null
                    )
                }
                refreshUserMembershipsFor(session.userId)
                showBanner("Registration submitted! Awaiting PMC Representative approval before Site Register access.", isError = false)
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false) }
                showBanner(err.message ?: "Registration failed.", isError = true)
            }
        }
    }

    /**
     * Join an additional project using a second PMC Project Code (independent membership & role per project).
     */
    fun joinAdditionalProject(
        organizationType: OrganizationType,
        organizationName: String,
        isUnlistedOrganization: Boolean,
        designation: String
    ) {
        val session = _uiState.value.session ?: return
        val profile = _uiState.value.profile ?: return
        val verified = _uiState.value.verifiedCodeResult
        if (verified == null || !verified.isValid || verified.project == null || verified.codeRecord == null) {
            showBanner("Verify a valid PMC Project Code first.", isError = true)
            return
        }
        viewModelScope.launch {
            val project = verified.project
            val code = verified.codeRecord
            repository.saveProjectCode(code.copy(currentRegistrations = code.currentRegistrations + 1))

            val membership = ProjectMembership(
                projectId = project.id,
                userId = session.userId,
                projectName = project.projectName,
                organizationType = organizationType,
                organizationName = organizationName.trim(),
                organizationVerified = !isUnlistedOrganization,
                designation = designation.trim(),
                assignedRole = ProjectRole.CLIENT_VIEWER,
                approvalStatus = MembershipApprovalStatus.PENDING,
                projectCodeUsed = code.projectCode
            )
            repository.saveMembership(membership)
            repository.saveRegistrationRequest(
                RegistrationRequestRecord(
                    projectId = project.id,
                    projectName = project.projectName,
                    userId = session.userId,
                    projectCodeUsed = code.projectCode,
                    fullName = profile.fullName,
                    username = profile.username,
                    email = profile.email,
                    mobileNumber = profile.mobileNumber,
                    requestedOrganizationType = organizationType,
                    requestedOrganizationName = organizationName.trim(),
                    isUnlistedOrganization = isUnlistedOrganization,
                    requestedDesignation = designation.trim(),
                    status = MembershipApprovalStatus.PENDING
                )
            )
            _uiState.update { it.copy(verifiedCodeResult = null) }
            showBanner("Join request submitted for ${project.projectName}. Pending PMC approval.", isError = false)
        }
    }

    fun completeProfile(fullName: String, username: String, mobile: String, designation: String) {
        val session = _uiState.value.session ?: return
        if (fullName.isBlank() || username.isBlank()) {
            showBanner("Full name and username are required.", isError = true)
            return
        }
        viewModelScope.launch {
            val updated = (_uiState.value.profile ?: UserProfile(session.userId, fullName, username, session.email)).copy(
                fullName = fullName.trim(),
                username = username.trim(),
                mobileNumber = mobile.trim(),
                defaultDesignation = designation.trim(),
                accountStatus = AccountStatus.ACTIVE
            )
            repository.saveSession(session, updated)
            _uiState.update { it.copy(profile = updated) }
            recomputeAccessRoute()
        }
    }

    fun markEmailVerifiedRefresh() {
        val session = _uiState.value.session ?: return
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            val updatedSession = session.copy(emailVerified = true)
            val updatedProfile = profile.copy(emailVerified = true)
            repository.saveSession(updatedSession, updatedProfile)
            _uiState.update { it.copy(session = updatedSession, profile = updatedProfile) }
            recomputeAccessRoute()
        }
    }

    fun logout() {
        viewModelScope.launch {
            val token = _uiState.value.session?.accessToken.orEmpty()
            if (token.isNotBlank() && repository.supabaseClient.isConfigured) {
                repository.supabaseClient.logout(token)
            }
            realtimeJob?.cancel()
            projectObserversJob?.cancel()
            registerObserversJob?.cancel()
            repository.saveSession(null, null)
            _uiState.update {
                SiteRegisterUiState(
                    isSupabaseConfigured = repository.supabaseClient.isConfigured,
                    isOnline = repository.isNetworkOnline(),
                    settings = it.settings
                )
            }
        }
    }

    private suspend fun refreshUserMembershipsFor(userId: String) {
        val all = dao.getAllByType(SiteRegisterRepository.TYPE_MEMBERSHIP)
        // observeAllMemberships updates automatically; force route recompute immediately
        val current = _uiState.value.userMemberships
        if (current.isEmpty() && all.isNotEmpty()) {
            recomputeAccessRoute(repository.loadActiveProjectId())
        } else {
            recomputeAccessRoute(repository.loadActiveProjectId())
        }
    }

    // =========================================================================
    // PMC PROJECT CREATION, DYNAMIC PROJECT CODES & REGISTRATION APPROVALS
    // =========================================================================

    /**
     * Allows an authenticated PMC Project Administrator to create a new Highway Project
     * and auto-generate its first unpredictable PMC Project Code.
     */
    fun createProjectAsPmcAdmin(
        projectName: String,
        highwayName: String,
        nhShNumber: String,
        packageNumber: String,
        projectLocation: String,
        stateName: String,
        contractorName: String,
        concessionaireName: String,
        clientName: String,
        pmcOrganization: String,
        ieOrganization: String,
        assignedPmcName: String,
        assignedPmcContact: String,
        projectIdentification: String,
        adminFullName: String? = null,
        adminEmail: String? = null
    ) {
        if (projectName.isBlank() || pmcOrganization.isBlank()) {
            showBanner("Project Name and PMC Organization are required.", isError = true)
            return
        }
        viewModelScope.launch {
            var session = _uiState.value.session
            var profile = _uiState.value.profile
            if (session == null) {
                val email = adminEmail?.trim().orEmpty().ifBlank { "pmc.admin@ctecs.local" }
                val name = adminFullName?.trim().orEmpty().ifBlank { assignedPmcName.ifBlank { "PMC Administrator" } }
                val uid = UUID.nameUUIDFromBytes(email.lowercase(Locale.US).toByteArray()).toString()
                session = UserSession("offline-session-$uid", "", System.currentTimeMillis() + 30L * 86400_000L, uid, email, true)
                profile = UserProfile(
                    id = uid,
                    fullName = name,
                    username = email.substringBefore("@"),
                    email = email,
                    defaultDesignation = "Resident Engineer",
                    defaultOrganizationType = OrganizationType.PMC,
                    defaultOrganizationName = pmcOrganization.trim(),
                    isSystemAdmin = true,
                    accountStatus = AccountStatus.ACTIVE,
                    emailVerified = true
                )
                repository.saveSession(session, profile)
            }

            val project = ProjectInfo(
                projectName = projectName.trim(),
                highwayName = highwayName.trim(),
                nhShNumber = nhShNumber.trim(),
                packageNumber = packageNumber.trim(),
                projectLocation = projectLocation.trim(),
                stateName = stateName.trim(),
                contractorName = contractorName.trim(),
                concessionaireName = concessionaireName.trim(),
                clientName = clientName.trim(),
                pmcOrganization = pmcOrganization.trim(),
                ieOrganization = ieOrganization.trim(),
                assignedPmcName = assignedPmcName.trim().ifBlank { profile?.fullName.orEmpty() },
                assignedPmcContact = assignedPmcContact.trim(),
                projectIdentification = projectIdentification.trim(),
                createdBy = session.userId
            )
            repository.saveProject(project)

            val codeStr = ApprovalWorkflowEngine.generateUnpredictableCode("CTECS", nhShNumber.ifBlank { projectName })
            val codeRecord = ProjectCodeRecord(
                projectId = project.id,
                projectCode = codeStr,
                permittedOrganizationTypes = OrganizationType.entries,
                createdBy = session.userId
            )
            repository.saveProjectCode(codeRecord)

            val adminMembership = ProjectMembership(
                projectId = project.id,
                userId = session.userId,
                projectName = project.projectName,
                organizationType = OrganizationType.PMC,
                organizationName = pmcOrganization.trim(),
                organizationVerified = true,
                designation = profile?.defaultDesignation.orEmpty().ifBlank { "Resident Engineer" },
                assignedRole = ProjectRole.PMC_REPRESENTATIVE,
                approvalStatus = MembershipApprovalStatus.APPROVED,
                projectCodeUsed = codeStr,
                approvedBy = session.userId,
                approvedAtMs = System.currentTimeMillis(),
                remarks = "Initial PMC Project Bootstrap"
            )
            repository.saveMembership(adminMembership)
            repository.saveActiveProjectId(project.id)

            repository.appendAuditEvent(
                SiteAuditEvent(
                    projectId = project.id,
                    userId = session.userId,
                    userName = profile?.fullName.orEmpty(),
                    organizationType = OrganizationType.PMC.dbValue,
                    assignedRole = ProjectRole.PMC_REPRESENTATIVE.dbValue,
                    action = "PROJECT_CREATED",
                    afterSummary = "Project '${project.projectName}' created with Project Code $codeStr"
                )
            )

            _uiState.update {
                it.copy(
                    session = session,
                    profile = profile,
                    activeProject = project,
                    activeMembership = adminMembership,
                    showCreatePmcProjectBootstrap = false,
                    accessRoute = LoginAccessRoute.PROJECT_DASHBOARD,
                    currentSection = SiteRegisterNavSection.DASHBOARD
                )
            }
            observeProjectWorkspace(project.id)
            showBanner("Project created! Share Project Code: $codeStr", isError = false)
        }
    }

    fun selectActiveProject(membership: ProjectMembership) {
        val project = _uiState.value.projects.firstOrNull { it.id == membership.projectId } ?: return
        viewModelScope.launch {
            repository.saveActiveProjectId(project.id)
            _uiState.update {
                it.copy(
                    activeProject = project,
                    activeMembership = membership,
                    accessRoute = LoginAccessRoute.PROJECT_DASHBOARD,
                    currentSection = SiteRegisterNavSection.DASHBOARD,
                    selectedRegisterId = null,
                    sharedViewRegisterId = null
                )
            }
            observeProjectWorkspace(project.id)
        }
    }

    fun generateNewProjectCode(
        permittedOrgs: List<OrganizationType>,
        maxRegistrations: Int?,
        expiryDays: Int?
    ) {
        val project = _uiState.value.activeProject ?: return
        val membership = _uiState.value.activeMembership ?: return
        if (!membership.assignedRole.canManageProjectAndUsers) {
            showBanner("Only PMC Representative / Project Administrator can generate Project Codes.", isError = true)
            return
        }
        viewModelScope.launch {
            val codeStr = ApprovalWorkflowEngine.generateUnpredictableCode("CTECS", project.nhShNumber.ifBlank { project.projectName })
            val expiresAt = expiryDays?.takeIf { it > 0 }?.let { System.currentTimeMillis() + it * 86_400_000L }
            val record = ProjectCodeRecord(
                projectId = project.id,
                projectCode = codeStr,
                permittedOrganizationTypes = permittedOrgs.ifEmpty { OrganizationType.entries },
                maxRegistrations = maxRegistrations?.takeIf { it > 0 },
                expiresAtMs = expiresAt,
                createdBy = membership.userId
            )
            repository.saveProjectCode(record)
            repository.appendAuditEvent(
                SiteAuditEvent(
                    projectId = project.id,
                    userId = membership.userId,
                    userName = _uiState.value.profile?.fullName.orEmpty(),
                    organizationType = membership.organizationType.dbValue,
                    assignedRole = membership.assignedRole.dbValue,
                    action = "PROJECT_CODE_GENERATED",
                    afterSummary = "Code $codeStr | Max: ${maxRegistrations ?: "Unlimited"}"
                )
            )
            showBanner("Generated Project Code: $codeStr", isError = false)
        }
    }

    fun toggleProjectCodeEnabled(code: ProjectCodeRecord) {
        val membership = _uiState.value.activeMembership ?: return
        if (!membership.assignedRole.canManageProjectAndUsers) return
        viewModelScope.launch {
            repository.saveProjectCode(code.copy(isEnabled = !code.isEnabled))
            showBanner(if (!code.isEnabled) "Project Code enabled." else "Project Code paused.", isError = false)
        }
    }

    fun revokeProjectCode(code: ProjectCodeRecord) {
        val membership = _uiState.value.activeMembership ?: return
        if (!membership.assignedRole.canManageProjectAndUsers) return
        viewModelScope.launch {
            repository.saveProjectCode(code.copy(isRevoked = true, isEnabled = false))
            repository.appendAuditEvent(
                SiteAuditEvent(
                    projectId = code.projectId,
                    userId = membership.userId,
                    userName = _uiState.value.profile?.fullName.orEmpty(),
                    organizationType = membership.organizationType.dbValue,
                    assignedRole = membership.assignedRole.dbValue,
                    action = "PROJECT_CODE_REVOKED",
                    afterSummary = "Revoked ${code.projectCode}"
                )
            )
            showBanner("Project Code ${code.projectCode} permanently revoked.", isError = false)
        }
    }

    fun reviewRegistrationRequest(
        request: RegistrationRequestRecord,
        decision: MembershipApprovalStatus,
        assignedRole: ProjectRole,
        organizationVerified: Boolean,
        remarks: String
    ) {
        val actorMembership = _uiState.value.activeMembership
        val isSysAdmin = _uiState.value.profile?.isSystemAdmin == true
        val check = ApprovalWorkflowEngine.validateMembershipApprovalAction(
            actorMembership = actorMembership,
            actorIsSystemAdmin = isSysAdmin,
            applicantUserId = request.userId,
            roleToAssign = assignedRole
        )
        if (check.isFailure) {
            showBanner(check.exceptionOrNull()?.message ?: "Unauthorized approval action.", isError = true)
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val reviewerId = _uiState.value.session?.userId.orEmpty()
            repository.saveRegistrationRequest(
                request.copy(
                    status = decision,
                    assignedRole = assignedRole,
                    organizationVerified = organizationVerified,
                    pmcRemarks = remarks.trim(),
                    reviewedBy = reviewerId,
                    reviewedAtMs = now
                )
            )

            // Update or create the project_members record
            val allMem = dao.getAllByType(SiteRegisterRepository.TYPE_MEMBERSHIP)
            val existingEntity = allMem.firstOrNull { it.projectId == request.projectId && it.parentId == request.userId }
            val existingId = existingEntity?.id ?: UUID.randomUUID().toString()
            repository.saveMembership(
                ProjectMembership(
                    id = existingId,
                    projectId = request.projectId,
                    userId = request.userId,
                    projectName = request.projectName,
                    organizationType = request.requestedOrganizationType,
                    organizationName = request.requestedOrganizationName,
                    organizationVerified = organizationVerified,
                    designation = request.requestedDesignation,
                    assignedRole = assignedRole,
                    approvalStatus = decision,
                    projectCodeUsed = request.projectCodeUsed,
                    approvedBy = reviewerId,
                    approvedAtMs = now,
                    remarks = remarks.trim()
                )
            )

            repository.appendAuditEvent(
                SiteAuditEvent(
                    projectId = request.projectId,
                    userId = reviewerId,
                    userName = _uiState.value.profile?.fullName.orEmpty(),
                    organizationType = actorMembership?.organizationType?.dbValue.orEmpty(),
                    assignedRole = actorMembership?.assignedRole?.dbValue.orEmpty(),
                    action = "REGISTRATION_${decision.dbValue}",
                    beforeSummary = "Applicant: ${request.fullName} (${request.status.dbValue})",
                    afterSummary = "Decision: ${decision.dbValue} | Role: ${assignedRole.displayLabel} | OrgVerified: $organizationVerified"
                )
            )
            showBanner("${request.fullName}: ${decision.label} as ${assignedRole.displayLabel}.", isError = false)
        }
    }

    // =========================================================================
    // NAVIGATION, FILTERS & SITE REGISTER CREATION
    // =========================================================================

    fun navigateTo(section: SiteRegisterNavSection) {
        _uiState.update { it.copy(currentSection = section, bannerMessage = null) }
    }

    fun setDateFilter(date: String) = _uiState.update { it.copy(selectedDateFilter = date) }
    fun setShiftFilter(shift: String) = _uiState.update { it.copy(selectedShiftFilter = shift) }
    fun setActivityFilter(code: String) = _uiState.update { it.copy(selectedActivityFilter = code) }
    fun setSearchQuery(q: String) = _uiState.update { it.copy(searchQuery = q) }

    fun openRegister(registerId: String, asReadOnlyShare: Boolean = false, shareCodeLabel: String? = null) {
        _uiState.update {
            it.copy(
                selectedRegisterId = registerId,
                sharedViewRegisterId = if (asReadOnlyShare) registerId else null,
                sharedViewCodeLabel = shareCodeLabel,
                currentSection = SiteRegisterNavSection.REGISTER_DETAIL,
                bannerMessage = null
            )
        }
        observeRegisterDetails(registerId)
    }

    fun createRegisterForActivity(
        template: HighwayActivityTemplate,
        workDate: String,
        shift: String,
        side: String,
        lane: String,
        chainageFrom: String,
        chainageTo: String,
        remarks: String
    ) {
        val project = _uiState.value.activeProject ?: return
        val membership = _uiState.value.activeMembership ?: return
        val profile = _uiState.value.profile ?: return
        if (!membership.assignedRole.canCreateRegister) {
            showBanner("Your role (${membership.assignedRole.displayLabel}) cannot create registers.", isError = true)
            return
        }
        viewModelScope.launch {
            val regNum = "SR-${template.code.take(4).uppercase()}-${SimpleDateFormat("MMdd-HHmm", Locale.US).format(Date())}"
            val reg = SiteRegisterRecord(
                projectId = project.id,
                projectName = project.projectName,
                contractorName = project.contractorName.ifBlank { _uiState.value.settings.defaultContractor },
                highwayName = project.highwayName,
                packageLocation = listOf(project.packageNumber, project.projectLocation).filter { it.isNotBlank() }.joinToString(" • "),
                activityCode = template.code,
                activityName = template.name,
                activityCategory = template.category,
                templateVersion = template.templateVersion,
                registerNumber = regNum,
                workDate = workDate.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) },
                shift = shift.ifBlank { _uiState.value.settings.defaultShift },
                side = side.ifBlank { "RHS" },
                lane = lane.ifBlank { _uiState.value.settings.defaultLane },
                chainageFrom = chainageFrom.trim(),
                chainageTo = chainageTo.trim(),
                totalMaterialUnit = template.primaryUnit,
                remarks = remarks.trim(),
                createdBy = membership.userId,
                createdByName = profile.fullName
            )
            repository.saveRegister(reg)
            repository.saveRevision(
                SiteRegisterRevision(
                    registerId = reg.id,
                    projectId = project.id,
                    revisionNumber = 1,
                    status = RegisterStatus.DRAFT,
                    isLocked = false,
                    changeSummary = "Initial draft created",
                    createdBy = membership.userId
                )
            )
            repository.appendAuditEvent(
                SiteAuditEvent(
                    projectId = project.id,
                    registerId = reg.id,
                    revisionNumber = 1,
                    userId = membership.userId,
                    userName = profile.fullName,
                    organizationType = membership.organizationType.dbValue,
                    assignedRole = membership.assignedRole.dbValue,
                    action = "REGISTER_CREATED",
                    afterSummary = "${reg.registerNumber} • ${reg.activityName} (${reg.chainageFrom}–${reg.chainageTo})"
                )
            )
            openRegister(reg.id)
            showBanner("Created ${reg.registerNumber} (${reg.activityName}).", isError = false)
        }
    }

    fun deleteActiveDraftRegister() {
        val reg = _uiState.value.activeRegister ?: return
        val uid = _uiState.value.session?.userId ?: return
        if (!ApprovalWorkflowEngine.canUserDeleteRegister(reg, uid, _uiState.value.activeMembership)) {
            showBanner("Approved, submitted, or reopened registers cannot be deleted.", isError = true)
            return
        }
        viewModelScope.launch {
            repository.deleteDraftRegister(reg.id)
            _uiState.update { it.copy(selectedRegisterId = null, currentSection = SiteRegisterNavSection.DASHBOARD) }
            showBanner("Draft ${reg.registerNumber} deleted.", isError = false)
        }
    }

    // =========================================================================
    // MEASUREMENTS, BC VEHICLE LAYING & GLASS GRID (WITH LOCK GUARD)
    // =========================================================================

    fun addSegmentToActiveRegister(segment: SiteSegmentRecord) {
        val reg = _uiState.value.activeRegister ?: return
        if (!_uiState.value.canEditActiveRegister) {
            showBanner("Register is locked (${reg.status.displayLabel}). Request PMC edit permission to modify.", isError = true)
            return
        }
        viewModelScope.launch {
            val nextIdx = (_uiState.value.activeRegisterSegments.maxOfOrNull { it.segmentIndex } ?: 0) + 1
            val enriched = segment.copy(
                registerId = reg.id,
                revisionNumber = reg.currentRevision,
                segmentIndex = nextIdx
            )
            repository.saveSegment(reg.projectId, enriched)
            recalculateRegisterTotals(reg, newSegments = _uiState.value.activeRegisterSegments + enriched)
            showBanner("Segment #$nextIdx saved (${enriched.fromChainage} → ${enriched.toChainage}).", isError = false)
        }
    }

    fun addBcEntryToActiveRegister(entry: BcLayingEntry) {
        val reg = _uiState.value.activeRegister ?: return
        if (!_uiState.value.canEditActiveRegister) {
            showBanner("Register is locked (${reg.status.displayLabel}). Request PMC edit permission to modify.", isError = true)
            return
        }
        viewModelScope.launch {
            val nextSl = (_uiState.value.activeRegisterBcEntries.maxOfOrNull { it.serialNumber } ?: 0) + 1
            val enriched = entry.copy(
                registerId = reg.id,
                revisionNumber = reg.currentRevision,
                serialNumber = nextSl
            )
            repository.saveBcEntry(reg.projectId, enriched)
            val allEntries = _uiState.value.activeRegisterBcEntries + enriched
            val summary = HighwayMeasurementEngine.calculateBcLayingSummary(allEntries)
            repository.saveRegister(
                reg.copy(
                    totalLengthM = summary.deduplicatedLaidLengthM,
                    totalAreaSqm = summary.deduplicatedLaidAreaSqm,
                    totalVolumeCum = summary.deduplicatedCompactedVolumeCum,
                    totalMaterialQty = summary.totalMeasuredMaterialMt,
                    totalMaterialUnit = MeasurementUnit.MT,
                    quantityClassification = QuantityClassification.MEASURED,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
            showBanner("Vehicle #${enriched.serialNumber} (${enriched.vehicleNumber}) recorded.", isError = false)
        }
    }

    fun addGlassGridEntryToActiveRegister(entry: GlassGridEntry) {
        val reg = _uiState.value.activeRegister ?: return
        if (!_uiState.value.canEditActiveRegister) {
            showBanner("Register is locked (${reg.status.displayLabel}). Request PMC edit permission to modify.", isError = true)
            return
        }
        viewModelScope.launch {
            val enriched = entry.copy(registerId = reg.id, revisionNumber = reg.currentRevision)
            repository.saveGlassGridEntry(reg.projectId, enriched)
            val all = _uiState.value.activeRegisterGlassGrid + enriched
            val totalLen = all.sumOf { it.applicationLengthM }
            val totalArea = all.sumOf { HighwayMeasurementEngine.calculateGlassGridCoverage(it).netApplicationAreaSqm }
            repository.saveRegister(
                reg.copy(
                    totalLengthM = totalLen,
                    totalAreaSqm = totalArea,
                    totalVolumeCum = 0.0,
                    totalMaterialUnit = MeasurementUnit.SQM,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
            showBanner("Glass Grid stretch recorded (Net Area: ${String.format(Locale.US, "%.2f", totalArea)} m²).", isError = false)
        }
    }

    private suspend fun recalculateRegisterTotals(reg: SiteRegisterRecord, newSegments: List<SiteSegmentRecord>) {
        val template = HighwayActivityCatalog.findByCode(reg.activityCode, _uiState.value.settings.customActivities)
        val summary = HighwayMeasurementEngine.calculateSegmentsWithOverlapGuard(
            segments = newSegments,
            volumeApplicable = template.volumeApplicable
        )
        val hasVerified = newSegments.any { it.verifiedThickness != null }
        repository.saveRegister(
            reg.copy(
                totalLengthM = summary.deduplicatedLengthM,
                totalAreaSqm = summary.totalAreaSqm,
                totalVolumeCum = if (template.volumeApplicable) summary.totalVolumeCum else 0.0,
                totalMaterialQty = summary.totalMaterialQty ?: reg.totalMaterialQty,
                quantityClassification = if (hasVerified) QuantityClassification.VERIFIED else QuantityClassification.MEASURED,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    // =========================================================================
    // QUALITY TESTS, TACK COAT SPRAY TEST, OCR & OBSERVATIONS
    // =========================================================================

    fun saveTackCoatTest(test: TackCoatTestRecord) {
        val project = _uiState.value.activeProject ?: return
        val userId = _uiState.value.session?.userId.orEmpty()
        viewModelScope.launch {
            val enriched = test.copy(
                projectId = project.id,
                projectName = project.projectName,
                contractorName = test.contractorName.ifBlank { project.contractorName },
                independentEngineer = test.independentEngineer.ifBlank { project.ieOrganization },
                pmcConsultant = test.pmcConsultant.ifBlank { project.pmcOrganization },
                registerId = test.registerId ?: _uiState.value.selectedRegisterId,
                createdBy = userId
            )
            repository.saveTackCoatTest(enriched)
            repository.appendAuditEvent(
                SiteAuditEvent(
                    projectId = project.id,
                    registerId = enriched.registerId,
                    userId = userId,
                    userName = _uiState.value.profile?.fullName.orEmpty(),
                    organizationType = _uiState.value.activeMembership?.organizationType?.dbValue.orEmpty(),
                    assignedRole = _uiState.value.activeMembership?.assignedRole?.dbValue.orEmpty(),
                    action = "TACKCOAT_SPRAY_TEST_SAVED",
                    afterSummary = "CH ${enriched.chainage} (${enriched.trays.size} trays)"
                )
            )
            showBanner("Tack Coat Spray Test saved (${enriched.trays.size} tray(s)).", isError = false)
        }
    }

    fun saveQualityTestReport(report: QualityTestReportRecord) {
        val project = _uiState.value.activeProject ?: return
        val userId = _uiState.value.session?.userId.orEmpty()
        viewModelScope.launch {
            val enriched = report.copy(
                projectId = project.id,
                registerId = report.registerId ?: _uiState.value.selectedRegisterId,
                createdBy = userId
            )
            repository.saveQualityTest(enriched)
            showBanner("Quality Test Report '${enriched.testTitle}' saved.", isError = false)
        }
    }

    fun runOcrFieldExtraction(rawText: String, isHandwritten: Boolean) {
        val result = QrCodeAndOcrHelper.extractStructuredFieldsFromText(rawText, isHandwritten)
        _uiState.update { it.copy(lastOcrResult = result) }
    }

    fun saveObservation(obs: SiteObservationRecord) {
        val project = _uiState.value.activeProject ?: return
        val membership = _uiState.value.activeMembership ?: return
        if (!membership.assignedRole.canAddObservation) {
            showBanner("Your role cannot add site observations.", isError = true)
            return
        }
        viewModelScope.launch {
            val nextSl = (_uiState.value.observations.maxOfOrNull { it.serialNumber } ?: 0) + 1
            val enriched = obs.copy(
                projectId = project.id,
                registerId = obs.registerId ?: _uiState.value.selectedRegisterId,
                serialNumber = if (obs.serialNumber > 0 && _uiState.value.observations.any { it.id == obs.id }) obs.serialNumber else nextSl,
                createdBy = membership.userId
            )
            repository.saveObservation(enriched)
            repository.appendAuditEvent(
                SiteAuditEvent(
                    projectId = project.id,
                    registerId = enriched.registerId,
                    userId = membership.userId,
                    userName = _uiState.value.profile?.fullName.orEmpty(),
                    organizationType = membership.organizationType.dbValue,
                    assignedRole = membership.assignedRole.dbValue,
                    action = "OBSERVATION_${enriched.status.dbValue}",
                    afterSummary = "#${enriched.serialNumber} ${enriched.category.label} (${enriched.severity.label})"
                )
            )
            showBanner("Observation #${enriched.serialNumber} saved (${enriched.status.label}).", isError = false)
        }
    }

    fun updateObservationRectification(
        obs: SiteObservationRecord,
        newStatus: RectificationStatus,
        correctiveAction: String,
        afterPhotos: List<String>,
        finalRemarks: String
    ) {
        val membership = _uiState.value.activeMembership ?: return
        if ((newStatus == RectificationStatus.VERIFIED || newStatus == RectificationStatus.CLOSED) &&
            !membership.assignedRole.canVerifyObservation
        ) {
            showBanner("Only PMC / IE Representative can verify or close observations.", isError = true)
            return
        }
        viewModelScope.launch {
            val updated = obs.copy(
                status = newStatus,
                correctiveAction = correctiveAction.trim(),
                afterPhotoUris = afterPhotos,
                finalRemarks = finalRemarks.trim(),
                verifiedByName = if (newStatus == RectificationStatus.VERIFIED || newStatus == RectificationStatus.CLOSED) {
                    _uiState.value.profile?.fullName.orEmpty()
                } else obs.verifiedByName,
                verificationDateMs = if (newStatus == RectificationStatus.VERIFIED || newStatus == RectificationStatus.CLOSED) {
                    System.currentTimeMillis()
                } else obs.verificationDateMs
            )
            repository.saveObservation(updated)
            showBanner("Observation #${obs.serialNumber} updated to ${newStatus.label}.", isError = false)
        }
    }

    // =========================================================================
    // DIGITAL SIGNATURES & LIVE READ-ONLY SHARING CODES
    // =========================================================================

    fun saveDigitalSignature(signaturePointsSerialized: String, remarks: String) {
        val reg = _uiState.value.activeRegister ?: return
        val membership = _uiState.value.activeMembership ?: return
        val profile = _uiState.value.profile ?: return
        if (!membership.assignedRole.canSignRegister) {
            showBanner("Your role (${membership.assignedRole.displayLabel}) does not have signature permissions.", isError = true)
            return
        }
        if (signaturePointsSerialized.isBlank()) {
            showBanner("Please draw your signature first.", isError = true)
            return
        }
        viewModelScope.launch {
            val sig = SiteSignatureRecord(
                registerId = reg.id,
                projectId = reg.projectId,
                revisionNumber = reg.currentRevision,
                signerUserId = membership.userId,
                signerName = profile.fullName,
                signerDesignation = membership.designation,
                signerOrganization = membership.organizationName,
                signerRole = membership.assignedRole,
                signaturePointsSerialized = signaturePointsSerialized,
                remarks = remarks.trim()
            )
            repository.saveSignature(sig)
            repository.appendAuditEvent(
                SiteAuditEvent(
                    projectId = reg.projectId,
                    registerId = reg.id,
                    revisionNumber = reg.currentRevision,
                    userId = membership.userId,
                    userName = profile.fullName,
                    organizationType = membership.organizationType.dbValue,
                    assignedRole = membership.assignedRole.dbValue,
                    action = "DIGITAL_SIGNATURE_RECORDED",
                    afterSummary = "Signed Rev ${reg.currentRevision} by ${profile.fullName} (${membership.assignedRole.displayLabel}). Note: Signature does not replace PMC Approval."
                )
            )
            showBanner("Digital signature recorded for Rev ${reg.currentRevision}.", isError = false)
        }
    }

    fun generateSharingCodeForActiveRegister(expiryHours: Int = _uiState.value.settings.sharingCodeExpiryHours) {
        val reg = _uiState.value.activeRegister ?: return
        val membership = _uiState.value.activeMembership ?: return
        viewModelScope.launch {
            val codeStr = ApprovalWorkflowEngine.generateUnpredictableCode("CTECS", reg.activityCode.take(4))
            val sharingCode = RegisterSharingCode(
                registerId = reg.id,
                projectId = reg.projectId,
                code = codeStr,
                expiresAtMs = if (expiryHours > 0) System.currentTimeMillis() + expiryHours * 3_600_000L else null,
                createdBy = membership.userId
            )
            repository.saveSharingCode(sharingCode)
            repository.appendAuditEvent(
                SiteAuditEvent(
                    projectId = reg.projectId,
                    registerId = reg.id,
                    revisionNumber = reg.currentRevision,
                    userId = membership.userId,
                    userName = _uiState.value.profile?.fullName.orEmpty(),
                    organizationType = membership.organizationType.dbValue,
                    assignedRole = membership.assignedRole.dbValue,
                    action = "SHARING_CODE_CREATED",
                    afterSummary = "Read-only code $codeStr (expires in ${expiryHours}h)"
                )
            )
            showBanner("Read-only Sharing Code created: $codeStr", isError = false)
        }
    }

    fun revokeSharingCode(code: RegisterSharingCode) {
        viewModelScope.launch {
            repository.saveSharingCode(code.copy(isRevoked = true))
            showBanner("Sharing code ${code.code} revoked.", isError = false)
        }
    }

    fun joinLiveRegisterBySharingCode(rawCode: String) {
        val check = ApprovalWorkflowEngine.validateSharingCodeReadOnly(
            rawCode = rawCode,
            codes = _uiState.value.sharingCodes,
            membership = _uiState.value.activeMembership
        )
        check.onSuccess { match ->
            openRegister(match.registerId, asReadOnlyShare = true, shareCodeLabel = match.code)
            showBanner("Opened LIVE Read-Only Register via ${match.code}.", isError = false)
        }.onFailure { err ->
            showBanner(err.message ?: "Invalid sharing code.", isError = true)
        }
    }

    // =========================================================================
    // PMC APPROVAL LOCK & CONTROLLED CREATOR-ONLY REOPENING WORKFLOW
    // =========================================================================

    fun submitActiveRegisterForPmcApproval() {
        val reg = _uiState.value.activeRegister ?: return
        val membership = _uiState.value.activeMembership ?: return
        if (!membership.assignedRole.canSubmitRegister && reg.createdBy != membership.userId) {
            showBanner("You do not have permission to submit this register.", isError = true)
            return
        }
        if (reg.status != RegisterStatus.DRAFT && reg.status != RegisterStatus.REJECTED) {
            showBanner("Only Draft or Rejected registers can be submitted.", isError = true)
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val updated = reg.copy(
                status = RegisterStatus.PENDING_PMC_APPROVAL,
                submittedAtMs = now,
                updatedAtMs = now
            )
            repository.saveRegister(updated)
            repository.appendAuditEvent(
                SiteAuditEvent(
                    projectId = reg.projectId,
                    registerId = reg.id,
                    revisionNumber = reg.currentRevision,
                    userId = membership.userId,
                    userName = _uiState.value.profile?.fullName.orEmpty(),
                    organizationType = membership.organizationType.dbValue,
                    assignedRole = membership.assignedRole.dbValue,
                    action = "REGISTER_SUBMITTED_FOR_PMC",
                    beforeSummary = reg.status.dbValue,
                    afterSummary = RegisterStatus.PENDING_PMC_APPROVAL.dbValue
                )
            )
            showBanner("Register submitted for PMC Approval (editing locked pending review).", isError = false)
        }
    }

    fun pmcDecisionOnActiveRegister(approve: Boolean, remarks: String) {
        val reg = _uiState.value.activeRegister ?: return
        val membership = _uiState.value.activeMembership
        val isSysAdmin = _uiState.value.profile?.isSystemAdmin == true
        val check = ApprovalWorkflowEngine.validatePmcRegisterDecision(reg, membership, isSysAdmin)
        if (check.isFailure) {
            showBanner(check.exceptionOrNull()?.message ?: "PMC approval denied.", isError = true)
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val actorId = _uiState.value.session?.userId.orEmpty()
            val actorName = _uiState.value.profile?.fullName.orEmpty()
            val nextStatus = if (approve) RegisterStatus.APPROVED_LOCKED else RegisterStatus.REJECTED

            repository.saveRegister(
                reg.copy(
                    status = nextStatus,
                    approvedAtMs = if (approve) now else reg.approvedAtMs,
                    approvedBy = if (approve) actorId else reg.approvedBy,
                    approvedByName = if (approve) actorName else reg.approvedByName,
                    remarks = if (remarks.isNotBlank()) "${reg.remarks}\n[PMC] $remarks".trim() else reg.remarks,
                    updatedAtMs = now
                )
            )

            // Seal the approved revision permanently as immutable snapshot
            val snapshot = JSONObject().apply {
                put("registerNumber", reg.registerNumber)
                put("revision", reg.currentRevision)
                put("totalLengthM", reg.totalLengthM)
                put("totalAreaSqm", reg.totalAreaSqm)
                put("totalVolumeCum", reg.totalVolumeCum)
                put("totalMaterialQty", reg.totalMaterialQty)
                put("segmentCount", _uiState.value.activeRegisterSegments.size)
                put("bcEntryCount", _uiState.value.activeRegisterBcEntries.size)
                put("pmcRemarks", remarks.trim())
            }.toString()

            val existingRev = _uiState.value.activeRegisterRevisions.firstOrNull { it.revisionNumber == reg.currentRevision }
            repository.saveRevision(
                (existingRev ?: SiteRegisterRevision(
                    registerId = reg.id,
                    projectId = reg.projectId,
                    revisionNumber = reg.currentRevision,
                    status = nextStatus,
                    isLocked = approve,
                    createdBy = reg.createdBy
                )).copy(
                    status = nextStatus,
                    isLocked = approve,
                    snapshotJson = snapshot,
                    approvedBy = if (approve) actorId else null,
                    approvedAtMs = if (approve) now else null
                )
            )

            repository.appendAuditEvent(
                SiteAuditEvent(
                    projectId = reg.projectId,
                    registerId = reg.id,
                    revisionNumber = reg.currentRevision,
                    userId = actorId,
                    userName = actorName,
                    organizationType = membership?.organizationType?.dbValue.orEmpty(),
                    assignedRole = membership?.assignedRole?.dbValue.orEmpty(),
                    action = if (approve) "PMC_APPROVED_LOCKED" else "PMC_REJECTED",
                    beforeSummary = reg.status.dbValue,
                    afterSummary = "${nextStatus.dbValue} | Remarks: ${remarks.ifBlank { "None" }}"
                )
            )
            showBanner(
                if (approve) "Rev ${reg.currentRevision} Approved – Locked permanently." else "Rev ${reg.currentRevision} Rejected and returned to creator.",
                isError = false
            )
        }
    }

    fun requestEditPermissionOnActiveRegister(reason: String, proposedCorrection: String, evidence: String) {
        val reg = _uiState.value.activeRegister ?: return
        val membership = _uiState.value.activeMembership ?: return
        val profile = _uiState.value.profile ?: return
        if (reg.createdBy != membership.userId) {
            showBanner("Only the original register creator can request temporary edit permission.", isError = true)
            return
        }
        if (reg.status != RegisterStatus.APPROVED_LOCKED && reg.status != RegisterStatus.EDIT_PERMISSION_EXPIRED) {
            showBanner("Edit permission can only be requested for an Approved – Locked register.", isError = true)
            return
        }
        if (reason.isBlank() || proposedCorrection.isBlank()) {
            showBanner("Reason and proposed correction summary are mandatory.", isError = true)
            return
        }
        viewModelScope.launch {
            val req = EditPermissionRequest(
                registerId = reg.id,
                projectId = reg.projectId,
                revisionNumber = reg.currentRevision,
                requestedBy = membership.userId,
                requestedByName = profile.fullName,
                reason = reason.trim(),
                proposedCorrection = proposedCorrection.trim(),
                supportingEvidence = evidence.trim()
            )
            repository.saveEditRequest(req)
            repository.saveRegister(reg.copy(status = RegisterStatus.EDIT_PERMISSION_REQUESTED, updatedAtMs = System.currentTimeMillis()))
            repository.appendAuditEvent(
                SiteAuditEvent(
                    projectId = reg.projectId,
                    registerId = reg.id,
                    revisionNumber = reg.currentRevision,
                    userId = membership.userId,
                    userName = profile.fullName,
                    organizationType = membership.organizationType.dbValue,
                    assignedRole = membership.assignedRole.dbValue,
                    action = "EDIT_PERMISSION_REQUESTED",
                    afterSummary = "Reason: ${reason.trim()}"
                )
            )
            showBanner("Edit permission request sent to PMC Representative.", isError = false)
        }
    }

    fun pmcGrantEditPermission(request: EditPermissionRequest, durationMinutes: Int, pmcRemarks: String) {
        val reg = _uiState.value.registers.firstOrNull { it.id == request.registerId } ?: return
        val membership = _uiState.value.activeMembership
        val isSysAdmin = _uiState.value.profile?.isSystemAdmin == true
        val check = ApprovalWorkflowEngine.validateGrantEditPermission(reg, request, membership, isSysAdmin)
        if (check.isFailure) {
            showBanner(check.exceptionOrNull()?.message ?: "Cannot grant edit permission.", isError = true)
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val pmcId = _uiState.value.session?.userId.orEmpty()
            val expiresAt = now + durationMinutes.coerceIn(5, 1440) * 60_000L
            repository.saveEditRequest(
                request.copy(
                    status = EditRequestStatus.GRANTED,
                    reviewedBy = pmcId,
                    pmcRemarks = pmcRemarks.trim()
                )
            )
            val grant = EditPermissionGrant(
                editRequestId = request.id,
                registerId = reg.id,
                projectId = reg.projectId,
                targetRevisionNumber = reg.currentRevision,
                authorizedUserId = reg.createdBy,
                grantedByPmcId = pmcId,
                grantedAtMs = now,
                expiresAtMs = expiresAt,
                isActive = true
            )
            repository.saveEditPermission(grant)
            repository.saveRegister(reg.copy(status = RegisterStatus.EDIT_PERMISSION_GRANTED, updatedAtMs = now))
            repository.appendAuditEvent(
                SiteAuditEvent(
                    projectId = reg.projectId,
                    registerId = reg.id,
                    revisionNumber = reg.currentRevision,
                    userId = pmcId,
                    userName = _uiState.value.profile?.fullName.orEmpty(),
                    organizationType = membership?.organizationType?.dbValue.orEmpty(),
                    assignedRole = membership?.assignedRole?.dbValue.orEmpty(),
                    action = "EDIT_PERMISSION_GRANTED",
                    afterSummary = "Creator authorized for ${durationMinutes} min. Remarks: ${pmcRemarks.ifBlank { "None" }}"
                )
            )
            showBanner("Temporary edit permission granted for $durationMinutes minutes (Creator only).", isError = false)
        }
    }

    fun pmcRejectEditRequest(request: EditPermissionRequest, pmcRemarks: String) {
        val reg = _uiState.value.registers.firstOrNull { it.id == request.registerId } ?: return
        val membership = _uiState.value.activeMembership ?: return
        if (!membership.assignedRole.canManageEditPermissions) return
        viewModelScope.launch {
            repository.saveEditRequest(
                request.copy(
                    status = EditRequestStatus.REJECTED,
                    reviewedBy = membership.userId,
                    pmcRemarks = pmcRemarks.trim()
                )
            )
            repository.saveRegister(reg.copy(status = RegisterStatus.APPROVED_LOCKED, updatedAtMs = System.currentTimeMillis()))
            showBanner("Edit request rejected; register remains Approved – Locked.", isError = false)
        }
    }

    fun pmcRevokeEditPermission(grant: EditPermissionGrant) {
        val reg = _uiState.value.registers.firstOrNull { it.id == grant.registerId } ?: return
        val membership = _uiState.value.activeMembership ?: return
        if (!membership.assignedRole.canManageEditPermissions) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.saveEditPermission(grant.copy(isActive = false, revokedAtMs = now))
            repository.saveRegister(reg.copy(status = RegisterStatus.APPROVED_LOCKED, updatedAtMs = now))
            repository.appendAuditEvent(
                SiteAuditEvent(
                    projectId = reg.projectId,
                    registerId = reg.id,
                    revisionNumber = reg.currentRevision,
                    userId = membership.userId,
                    userName = _uiState.value.profile?.fullName.orEmpty(),
                    organizationType = membership.organizationType.dbValue,
                    assignedRole = membership.assignedRole.dbValue,
                    action = "EDIT_PERMISSION_REVOKED",
                    afterSummary = "Temporary edit permission revoked immediately"
                )
            )
            showBanner("Temporary edit permission revoked immediately.", isError = false)
        }
    }

    fun submitCorrectedRevisionForActiveRegister(changeSummary: String) {
        val reg = _uiState.value.activeRegister ?: return
        val uid = _uiState.value.session?.userId ?: return
        val membership = _uiState.value.activeMembership ?: return
        val snapshot = JSONObject().apply {
            put("registerNumber", reg.registerNumber)
            put("totalLengthM", reg.totalLengthM)
            put("totalAreaSqm", reg.totalAreaSqm)
            put("totalVolumeCum", reg.totalVolumeCum)
            put("changeSummary", changeSummary.trim())
        }.toString()

        val result = ApprovalWorkflowEngine.createResubmittedRevision(
            register = reg,
            previousRevisions = _uiState.value.activeRegisterRevisions,
            changeSummary = changeSummary,
            snapshotJson = snapshot,
            actorUserId = uid,
            activePermissions = _uiState.value.editPermissions
        )
        result.onSuccess { (updatedReg, newRev) ->
            viewModelScope.launch {
                val now = System.currentTimeMillis()
                // Consume active grant so it cannot be reused
                _uiState.value.editPermissions
                    .filter { it.registerId == reg.id && it.isActive }
                    .forEach { repository.saveEditPermission(it.copy(isActive = false, consumedAtMs = now)) }
                repository.saveRevision(newRev)
                repository.saveRegister(updatedReg)
                repository.appendAuditEvent(
                    SiteAuditEvent(
                        projectId = reg.projectId,
                        registerId = reg.id,
                        revisionNumber = newRev.revisionNumber,
                        userId = uid,
                        userName = _uiState.value.profile?.fullName.orEmpty(),
                        organizationType = membership.organizationType.dbValue,
                        assignedRole = membership.assignedRole.dbValue,
                        action = "CORRECTED_REVISION_SUBMITTED",
                        beforeSummary = "Rev ${reg.currentRevision} preserved locked",
                        afterSummary = "Rev ${newRev.revisionNumber} submitted for fresh PMC approval: ${newRev.changeSummary}"
                    )
                )
                showBanner("Revision ${newRev.revisionNumber} submitted for fresh PMC Approval (Rev ${reg.currentRevision} preserved).", isError = false)
            }
        }.onFailure { err ->
            showBanner(err.message ?: "Unable to submit corrected revision.", isError = true)
        }
    }

    // =========================================================================
    // SYNC, DAILY EXCEL / PDF EXPORTS & SETTINGS
    // =========================================================================

    fun triggerManualSync() {
        viewModelScope.launch {
            _uiState.update { it.copy(isOnline = repository.isNetworkOnline()) }
            val summary = repository.synchronizePendingQueue(_uiState.value.session, _uiState.value.activeMembership)
            showBanner(summary.message, isError = summary.blockedByLockCount > 0 || summary.failedCount > 0)
        }
    }

    fun exportDailyReport(formatExcel: Boolean, reportDate: String, activityFilter: String) {
        val project = _uiState.value.activeProject ?: return
        val membership = _uiState.value.activeMembership
        if (membership != null && !membership.assignedRole.canGenerateReports) {
            showBanner("Your role cannot export site register reports.", isError = true)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, lastExportResult = null) }
            val res = withContext(Dispatchers.IO) {
                val state = _uiState.value
                val regs = if (reportDate.isBlank()) state.registers else state.registers.filter { it.workDate == reportDate }
                val segMap = mutableMapOf<String, List<SiteSegmentRecord>>()
                val bcMap = mutableMapOf<String, List<BcLayingEntry>>()
                val ggMap = mutableMapOf<String, List<GlassGridEntry>>()
                val sigMap = mutableMapOf<String, List<SiteSignatureRecord>>()
                regs.forEach { r ->
                    segMap[r.id] = dao.getByTypeAndParent(SiteRegisterRepository.TYPE_SEGMENT, r.id)
                        .mapNotNull { runCatching { parseSegmentFallback(it.payloadJson) }.getOrNull() }
                    bcMap[r.id] = dao.getByTypeAndParent(SiteRegisterRepository.TYPE_BC_ENTRY, r.id)
                        .mapNotNull { runCatching { parseBcFallback(it.payloadJson) }.getOrNull() }
                    ggMap[r.id] = dao.getByTypeAndParent(SiteRegisterRepository.TYPE_GLASS_GRID, r.id)
                        .mapNotNull { runCatching { parseGlassGridFallback(it.payloadJson) }.getOrNull() }
                    sigMap[r.id] = dao.getByTypeAndParent(SiteRegisterRepository.TYPE_SIGNATURE, r.id)
                        .mapNotNull { runCatching { parseSignatureFallback(it.payloadJson) }.getOrNull() }
                }

                val bundle = SiteRegisterExportEngine.ExportBundleInput(
                    project = project,
                    reportDate = reportDate.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) },
                    activityFilter = activityFilter,
                    registers = regs,
                    revisions = state.activeRegisterRevisions,
                    segmentsByRegister = segMap,
                    bcEntriesByRegister = bcMap,
                    glassGridByRegister = ggMap,
                    tackCoatTests = state.tackCoatTests,
                    qualityTests = state.qualityTests,
                    observations = state.observations,
                    signaturesByRegister = sigMap,
                    auditLogs = state.auditLogs,
                    pendingOfflineSyncCount = state.pendingSyncCount
                )
                if (formatExcel) {
                    SiteRegisterExportEngine.exportDailyExcelWorkbook(getApplication(), bundle)
                } else {
                    SiteRegisterExportEngine.exportDailyPdfReport(getApplication(), bundle)
                }
            }
            repository.appendAuditEvent(
                SiteAuditEvent(
                    projectId = project.id,
                    userId = _uiState.value.session?.userId.orEmpty(),
                    userName = _uiState.value.profile?.fullName.orEmpty(),
                    organizationType = membership?.organizationType?.dbValue.orEmpty(),
                    assignedRole = membership?.assignedRole?.dbValue.orEmpty(),
                    action = if (formatExcel) "EXPORT_EXCEL_GENERATED" else "EXPORT_PDF_GENERATED",
                    afterSummary = "${res.fileName} (${res.sheetOrPageCount} ${if (formatExcel) "sheets" else "pages"})"
                )
            )
            _uiState.update { it.copy(isExporting = false, lastExportResult = res) }
            showBanner("Exported ${res.fileName}", isError = false)
        }
    }

    fun buildShareIntent(result: SiteRegisterExportEngine.ExportResult): Intent {
        val uri = SiteRegisterExportEngine.getShareableUri(getApplication(), result.file)
        return Intent(Intent.ACTION_SEND).apply {
            type = result.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "CTECS Site Register – ${result.fileName}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun updateSettings(updated: SiteRegisterSettings) {
        viewModelScope.launch {
            repository.saveSettings(updated)
            _uiState.update { it.copy(settings = updated) }
            showBanner("Site Register preferences saved.", isError = false)
        }
    }

    fun addCustomActivityTemplate(name: String, primaryUnit: MeasurementUnit, volumeApplicable: Boolean) {
        if (name.isBlank()) return
        val code = "CUSTOM_" + name.uppercase(Locale.US).replace(Regex("[^A-Z0-9]+"), "_").take(24)
        val newTemplate = HighwayActivityTemplate(
            code = code,
            name = name.trim(),
            category = HighwayActivityCategory.CUSTOM,
            primaryUnit = primaryUnit,
            volumeApplicable = volumeApplicable,
            isCustom = true
        )
        val cur = _uiState.value.settings
        updateSettings(cur.copy(customActivities = cur.customActivities + newTemplate))
    }

    // Minimal JSON helpers for export aggregation from Room entities
    private fun parseSegmentFallback(raw: String): SiteSegmentRecord {
        val o = JSONObject(raw)
        return SiteSegmentRecord(
            id = o.optString("id"),
            registerId = o.optString("register_id"),
            revisionNumber = o.optInt("revision_number", 1),
            segmentIndex = o.optInt("segment_index", 1),
            fromChainage = o.optString("from_chainage"),
            toChainage = o.optString("to_chainage"),
            side = o.optString("side", "RHS"),
            lane = o.optString("lane", "MCW"),
            startWidthM = if (o.has("start_width_m")) o.optDouble("start_width_m") else null,
            middleWidthM = if (o.has("middle_width_m")) o.optDouble("middle_width_m") else null,
            endWidthM = if (o.has("end_width_m")) o.optDouble("end_width_m") else null,
            actualWidthM = if (o.has("actual_width_m")) o.optDouble("actual_width_m") else null,
            designThickness = if (o.has("design_thickness")) o.optDouble("design_thickness") else null,
            looseThickness = if (o.has("loose_thickness")) o.optDouble("loose_thickness") else null,
            actualCompactedThickness = if (o.has("actual_compacted_thickness")) o.optDouble("actual_compacted_thickness") else null,
            verifiedThickness = if (o.has("verified_thickness")) o.optDouble("verified_thickness") else null,
            thicknessUnit = MeasurementUnit.fromSymbol(o.optString("thickness_unit", "mm")),
            verifiedDensity = if (o.has("verified_density")) o.optDouble("verified_density") else null,
            remarks = o.optString("remarks")
        )
    }

    private fun parseBcFallback(raw: String): BcLayingEntry {
        val o = JSONObject(raw)
        return BcLayingEntry(
            id = o.optString("id"),
            registerId = o.optString("register_id"),
            revisionNumber = o.optInt("revision_number", 1),
            serialNumber = o.optInt("serial_number", 1),
            vehicleNumber = o.optString("vehicle_number"),
            bcType = o.optString("bc_type", "BC-II"),
            layer = o.optString("layer", "Wearing Course"),
            fromChainage = o.optString("from_chainage"),
            toChainage = o.optString("to_chainage"),
            side = o.optString("side", "RHS"),
            lane = o.optString("lane", "MCW"),
            startTime = o.optString("start_time"),
            endTime = o.optString("end_time"),
            layingTempC = if (o.has("laying_temp_c")) o.optDouble("laying_temp_c") else null,
            rollingTempC = if (o.has("rolling_temp_c")) o.optDouble("rolling_temp_c") else null,
            startWidthM = if (o.has("start_width_m")) o.optDouble("start_width_m") else null,
            middleWidthM = if (o.has("middle_width_m")) o.optDouble("middle_width_m") else null,
            endWidthM = if (o.has("end_width_m")) o.optDouble("end_width_m") else null,
            designThicknessMm = if (o.has("design_thickness_mm")) o.optDouble("design_thickness_mm") else null,
            looseThicknessMm = if (o.has("loose_thickness_mm")) o.optDouble("loose_thickness_mm") else null,
            compactedThicknessMm = if (o.has("compacted_thickness_mm")) o.optDouble("compacted_thickness_mm") else null,
            measuredMaterialMt = if (o.has("measured_material_mt")) o.optDouble("measured_material_mt") else null,
            remarks = o.optString("remarks")
        )
    }

    private fun parseGlassGridFallback(raw: String): GlassGridEntry {
        val o = JSONObject(raw)
        return GlassGridEntry(
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
            remarks = o.optString("remarks")
        )
    }

    private fun parseSignatureFallback(raw: String): SiteSignatureRecord {
        val o = JSONObject(raw)
        return SiteSignatureRecord(
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
            signedAtMs = o.optLong("signed_at_ms", System.currentTimeMillis()),
            remarks = o.optString("remarks")
        )
    }
}
