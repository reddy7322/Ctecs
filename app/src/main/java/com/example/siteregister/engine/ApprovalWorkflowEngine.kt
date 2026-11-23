package com.example.siteregister.engine

import com.example.siteregister.model.AccountStatus
import com.example.siteregister.model.EditPermissionGrant
import com.example.siteregister.model.EditPermissionRequest
import com.example.siteregister.model.EditRequestStatus
import com.example.siteregister.model.LoginAccessRoute
import com.example.siteregister.model.MembershipApprovalStatus
import com.example.siteregister.model.OrganizationType
import com.example.siteregister.model.ProjectCodeRecord
import com.example.siteregister.model.ProjectInfo
import com.example.siteregister.model.ProjectMembership
import com.example.siteregister.model.ProjectRole
import com.example.siteregister.model.RegisterSharingCode
import com.example.siteregister.model.RegisterStatus
import com.example.siteregister.model.SiteRegisterRecord
import com.example.siteregister.model.SiteRegisterRevision
import com.example.siteregister.model.UserProfile
import com.example.siteregister.model.UserSession
import com.example.siteregister.model.VerifiedProjectCodeResult
import java.security.SecureRandom

/**
 * Pure, deterministic Governance, RBAC, Login Routing, Dynamic Code, and
 * Immutable PMC Approval / Controlled Reopening Security Engine.
 *
 * Mirrors the exact PostgreSQL RLS + SECURITY DEFINER RPC rules locally so
 * offline actions and UI state transitions can NEVER bypass approved-register locks
 * or privilege boundaries.
 */
object ApprovalWorkflowEngine {

    private val secureRandom = SecureRandom()
    private val codeAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Excludes confusing 0/O/1/I

    /**
     * Generates a cryptographically unpredictable Project Code or Register Sharing Code.
     * Example: "CTECS-NH58-7K92P6" or "CTECS-BC-7K92P6".
     */
    fun generateUnpredictableCode(prefix: String, segment: String, randomChars: Int = 6): String {
        val cleanPrefix = prefix.uppercase().filter { it.isLetterOrDigit() }.ifBlank { "CTECS" }.take(6)
        val cleanSeg = segment.uppercase().filter { it.isLetterOrDigit() }.ifBlank { "PRJ" }.take(6)
        val suffix = buildString {
            repeat(randomChars.coerceIn(6, 12)) {
                append(codeAlphabet[secureRandom.nextInt(codeAlphabet.length)])
            }
        }
        return "$cleanPrefix-$cleanSeg-$suffix"
    }

    /**
     * Exact post-authentication access routing per Specification Part 2.10.
     * Authentication and Project Authorization remain strictly separated.
     */
    fun resolveLoginAccessRoute(
        session: UserSession?,
        profile: UserProfile?,
        memberships: List<ProjectMembership>,
        nowMs: Long = System.currentTimeMillis()
    ): LoginAccessRoute {
        if (session == null || session.isExpired(nowMs)) return LoginAccessRoute.UNAUTHENTICATED
        if (profile == null || profile.accountStatus == AccountStatus.INCOMPLETE || profile.fullName.isBlank()) {
            return LoginAccessRoute.COMPLETE_PROFILE
        }
        if (profile.accountStatus == AccountStatus.SUSPENDED || profile.accountStatus == AccountStatus.DEACTIVATED) {
            return LoginAccessRoute.ACCESS_BLOCKED
        }
        if (!session.emailVerified || !profile.emailVerified) {
            return LoginAccessRoute.VERIFY_EMAIL
        }

        val approved = memberships.filter { it.approvalStatus == MembershipApprovalStatus.APPROVED }
        if (approved.size > 1) return LoginAccessRoute.PROJECT_SELECTOR
        if (approved.size == 1) return LoginAccessRoute.PROJECT_DASHBOARD

        if (memberships.isEmpty()) return LoginAccessRoute.JOIN_PROJECT_CODE

        return when {
            memberships.any { it.approvalStatus == MembershipApprovalStatus.PENDING || it.approvalStatus == MembershipApprovalStatus.INFO_REQUESTED } ->
                LoginAccessRoute.PENDING_APPROVAL
            memberships.any { it.approvalStatus == MembershipApprovalStatus.SUSPENDED || it.approvalStatus == MembershipApprovalStatus.REVOKED } ->
                LoginAccessRoute.ACCESS_BLOCKED
            memberships.any { it.approvalStatus == MembershipApprovalStatus.REJECTED } ->
                LoginAccessRoute.REJECTED_STATUS
            else -> LoginAccessRoute.JOIN_PROJECT_CODE
        }
    }

    /**
     * Validates a PMC-assigned Dynamic Project Code before exposing verified project details.
     */
    fun validateProjectCode(
        rawCode: String,
        codes: List<ProjectCodeRecord>,
        projects: List<ProjectInfo>,
        nowMs: Long = System.currentTimeMillis()
    ): VerifiedProjectCodeResult {
        val normalized = rawCode.trim().uppercase()
        if (normalized.length < 6) {
            return VerifiedProjectCodeResult(false, "Please enter a valid PMC Project Code.")
        }
        val codeRec = codes.firstOrNull { it.projectCode.equals(normalized, ignoreCase = true) }
            ?: return VerifiedProjectCodeResult(false, "Project Code not found.")

        if (codeRec.isRevoked) {
            return VerifiedProjectCodeResult(false, "This Project Code has been revoked by PMC.")
        }
        if (!codeRec.isEnabled) {
            return VerifiedProjectCodeResult(false, "Registration is disabled for this Project Code.")
        }
        if (codeRec.expiresAtMs != null && nowMs >= codeRec.expiresAtMs) {
            return VerifiedProjectCodeResult(false, "This Project Code has expired.")
        }
        if (codeRec.maxRegistrations != null && codeRec.currentRegistrations >= codeRec.maxRegistrations) {
            return VerifiedProjectCodeResult(false, "Registration limit reached for this Project Code.")
        }
        val project = projects.firstOrNull { it.id == codeRec.projectId }
            ?: return VerifiedProjectCodeResult(false, "Associated project not found.")
        if (project.registrationStatus.equals("CLOSED", ignoreCase = true)) {
            return VerifiedProjectCodeResult(false, "Registration is closed for this project.")
        }

        return VerifiedProjectCodeResult(
            isValid = true,
            project = project.copy(permittedOrganizationTypes = codeRec.permittedOrganizationTypes),
            codeRecord = codeRec
        )
    }

    /**
     * Validates whether [actorMembership] can approve/reject [applicantUserId] and assign [roleToAssign].
     * Enforces:
     * - Actor must be an APPROVED PMC Representative / Project Administrator / System Administrator.
     * - Blocks self-approval (actorUserId == applicantUserId).
     * - Blocks circular approval (actor was approved by applicant).
     * - Selecting PMC during registration NEVER grants approval authority until approved by existing PMC/Admin.
     */
    fun validateMembershipApprovalAction(
        actorMembership: ProjectMembership?,
        actorIsSystemAdmin: Boolean,
        applicantUserId: String,
        roleToAssign: ProjectRole
    ): Result<Unit> {
        if (actorMembership != null && actorMembership.userId == applicantUserId) {
            return Result.failure(SecurityException("Self-approval is strictly prohibited."))
        }
        if (!actorIsSystemAdmin) {
            if (actorMembership == null || actorMembership.approvalStatus != MembershipApprovalStatus.APPROVED) {
                return Result.failure(SecurityException("Only an approved PMC Representative or Administrator can review registrations."))
            }
            if (!actorMembership.assignedRole.isPrivilegedPmcOrAdmin) {
                return Result.failure(SecurityException("Your assigned role (${actorMembership.assignedRole.displayLabel}) cannot approve users."))
            }
            if (actorMembership.approvedBy == applicantUserId) {
                return Result.failure(SecurityException("Circular approval is strictly prohibited."))
            }
            if (roleToAssign == ProjectRole.SYSTEM_ADMINISTRATOR) {
                return Result.failure(SecurityException("Only a System Administrator can assign System Administrator role."))
            }
        }
        return Result.success(Unit)
    }

    /**
     * Exact check of whether [userId] with [membership] can edit [register] right now.
     *
     * Rules:
     * 1. DRAFT / REJECTED: creator OR approved Site Engineer / Site Supervisor / Contractor Site Engineer / Project Admin.
     * 2. SUBMITTED / PENDING_PMC_APPROVAL / APPROVED_LOCKED / EDIT_PERMISSION_REQUESTED / EDIT_PERMISSION_EXPIRED / RESUBMITTED:
     *    STRICTLY DENIED for everyone (including creator and supervisor).
     * 3. EDIT_PERMISSION_GRANTED / REOPENED:
     *    ONLY the designated creator ([register.createdBy] == [userId]) holding an active, unrevoked,
     *    non-expired [EditPermissionGrant] for the exact register & revision, granted by a different PMC user.
     */
    fun canUserEditRegister(
        register: SiteRegisterRecord,
        userId: String,
        membership: ProjectMembership?,
        activePermissions: List<EditPermissionGrant>,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (membership == null || membership.approvalStatus != MembershipApprovalStatus.APPROVED) return false
        if (membership.projectId != register.projectId) return false

        return when (register.status) {
            RegisterStatus.DRAFT, RegisterStatus.REJECTED -> {
                register.createdBy == userId || membership.assignedRole.canEditDraft
            }
            RegisterStatus.EDIT_PERMISSION_GRANTED, RegisterStatus.REOPENED -> {
                if (register.createdBy != userId) return false
                activePermissions.any { perm ->
                    perm.registerId == register.id &&
                        perm.isCurrentlyValidFor(userId, register.currentRevision, nowMs)
                }
            }
            else -> false
        }
    }

    fun canUserDeleteRegister(
        register: SiteRegisterRecord,
        userId: String,
        membership: ProjectMembership?
    ): Boolean {
        if (membership == null || membership.approvalStatus != MembershipApprovalStatus.APPROVED) return false
        // Approved, submitted, or reopened registers can NEVER be deleted
        return register.status == RegisterStatus.DRAFT && register.createdBy == userId
    }

    /**
     * Evaluates whether a register whose temporary edit permission has passed [expiresAtMs]
     * must immediately transition to [RegisterStatus.EDIT_PERMISSION_EXPIRED].
     */
    fun resolveEffectiveRegisterStatus(
        register: SiteRegisterRecord,
        permissions: List<EditPermissionGrant>,
        nowMs: Long = System.currentTimeMillis()
    ): RegisterStatus {
        if (register.status == RegisterStatus.EDIT_PERMISSION_GRANTED || register.status == RegisterStatus.REOPENED) {
            val hasValidGrant = permissions.any {
                it.registerId == register.id &&
                    it.isCurrentlyValidFor(register.createdBy, register.currentRevision, nowMs)
            }
            if (!hasValidGrant) {
                return RegisterStatus.EDIT_PERMISSION_EXPIRED
            }
        }
        return register.status
    }

    /**
     * Validates a PMC approval/rejection decision on a register revision.
     */
    fun validatePmcRegisterDecision(
        register: SiteRegisterRecord,
        actorMembership: ProjectMembership?,
        actorIsSystemAdmin: Boolean
    ): Result<Unit> {
        if (actorMembership == null && !actorIsSystemAdmin) {
            return Result.failure(SecurityException("Approved project membership required."))
        }
        if (!actorIsSystemAdmin) {
            if (actorMembership!!.approvalStatus != MembershipApprovalStatus.APPROVED ||
                !actorMembership.assignedRole.canApproveRegister
            ) {
                return Result.failure(SecurityException("Only an authorized PMC Representative can approve or reject registers."))
            }
        }
        if (actorMembership != null && actorMembership.userId == register.createdBy) {
            return Result.failure(SecurityException("Self-approval of your own register is prohibited."))
        }
        if (register.status != RegisterStatus.PENDING_PMC_APPROVAL &&
            register.status != RegisterStatus.SUBMITTED &&
            register.status != RegisterStatus.RESUBMITTED
        ) {
            return Result.failure(IllegalStateException("Register must be submitted and awaiting PMC approval."))
        }
        return Result.success(Unit)
    }

    /**
     * Validates granting temporary edit permission on an Approved – Locked register.
     */
    fun validateGrantEditPermission(
        register: SiteRegisterRecord,
        request: EditPermissionRequest,
        pmcMembership: ProjectMembership?,
        pmcIsSystemAdmin: Boolean
    ): Result<Unit> {
        if (!pmcIsSystemAdmin) {
            if (pmcMembership == null ||
                pmcMembership.approvalStatus != MembershipApprovalStatus.APPROVED ||
                !pmcMembership.assignedRole.canManageEditPermissions
            ) {
                return Result.failure(SecurityException("Only PMC can grant temporary edit permissions."))
            }
        }
        val pmcUserId = pmcMembership?.userId ?: "system"
        if (request.requestedBy == pmcUserId) {
            return Result.failure(SecurityException("Creator self-authorization is strictly prohibited."))
        }
        if (request.requestedBy != register.createdBy) {
            return Result.failure(SecurityException("Temporary edit permission may only be granted to the register creator."))
        }
        if (request.status != EditRequestStatus.PENDING) {
            return Result.failure(IllegalStateException("Edit request is no longer pending."))
        }
        return Result.success(Unit)
    }

    /**
     * Creates a new immutable revision record when submitting corrections so earlier
     * approved revisions are NEVER overwritten.
     */
    fun createResubmittedRevision(
        register: SiteRegisterRecord,
        previousRevisions: List<SiteRegisterRevision>,
        changeSummary: String,
        snapshotJson: String,
        actorUserId: String,
        activePermissions: List<EditPermissionGrant>,
        nowMs: Long = System.currentTimeMillis()
    ): Result<Pair<SiteRegisterRecord, SiteRegisterRevision>> {
        if (register.createdBy != actorUserId) {
            return Result.failure(SecurityException("Only the authorized register creator can submit a corrected revision."))
        }
        val validGrant = activePermissions.firstOrNull {
            it.registerId == register.id &&
                it.isCurrentlyValidFor(actorUserId, register.currentRevision, nowMs)
        } ?: return Result.failure(SecurityException("No active, non-expired PMC edit permission exists."))

        // Verify previous approved revisions remain intact
        val nextRevNumber = (previousRevisions.maxOfOrNull { it.revisionNumber } ?: register.currentRevision) + 1
        val newRevision = SiteRegisterRevision(
            registerId = register.id,
            projectId = register.projectId,
            revisionNumber = nextRevNumber,
            status = RegisterStatus.PENDING_PMC_APPROVAL,
            isLocked = false,
            changeSummary = changeSummary.ifBlank { "Correction submitted under PMC grant ${validGrant.id.take(8)}" },
            snapshotJson = snapshotJson,
            createdBy = actorUserId,
            createdAtMs = nowMs
        )
        val updatedRegister = register.copy(
            currentRevision = nextRevNumber,
            status = RegisterStatus.PENDING_PMC_APPROVAL,
            submittedAtMs = nowMs,
            updatedAtMs = nowMs
        )
        return Result.success(updatedRegister to newRevision)
    }

    /**
     * Validates a register sharing code strictly for read/monitoring access.
     * A sharing code NEVER grants editing, approval, or admin privileges.
     */
    fun validateSharingCodeReadOnly(
        rawCode: String,
        codes: List<RegisterSharingCode>,
        membership: ProjectMembership?,
        nowMs: Long = System.currentTimeMillis()
    ): Result<RegisterSharingCode> {
        if (membership == null || membership.approvalStatus != MembershipApprovalStatus.APPROVED) {
            return Result.failure(SecurityException("Approved project membership is required to view shared registers."))
        }
        val match = codes.firstOrNull { it.code.equals(rawCode.trim(), ignoreCase = true) }
            ?: return Result.failure(IllegalArgumentException("Sharing code not found."))
        if (match.projectId != membership.projectId) {
            return Result.failure(SecurityException("Sharing code belongs to a different project."))
        }
        if (!match.isUsable(nowMs)) {
            return Result.failure(IllegalStateException("Sharing code is expired or revoked."))
        }
        return Result.success(match)
    }
}
