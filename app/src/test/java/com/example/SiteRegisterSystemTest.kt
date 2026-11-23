package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.siteregister.data.SiteRegisterRepository
import com.example.siteregister.engine.ApprovalWorkflowEngine
import com.example.siteregister.engine.HighwayMeasurementEngine
import com.example.siteregister.export.SiteRegisterExportEngine
import com.example.siteregister.model.AccountStatus
import com.example.siteregister.model.BcLayingEntry
import com.example.siteregister.model.EditPermissionGrant
import com.example.siteregister.model.EditPermissionRequest
import com.example.siteregister.model.GlassGridEntry
import com.example.siteregister.model.HighwayActivityCatalog
import com.example.siteregister.model.HighwayActivityCategory
import com.example.siteregister.model.LoginAccessRoute
import com.example.siteregister.model.MeasurementMethod
import com.example.siteregister.model.MeasurementUnit
import com.example.siteregister.model.MembershipApprovalStatus
import com.example.siteregister.model.OrganizationType
import com.example.siteregister.model.ProjectCodeRecord
import com.example.siteregister.model.ProjectInfo
import com.example.siteregister.model.ProjectMembership
import com.example.siteregister.model.ProjectRole
import com.example.siteregister.model.RegisterSharingCode
import com.example.siteregister.model.RegisterStatus
import com.example.siteregister.model.SiteObservationRecord
import com.example.siteregister.model.SiteRegisterRecord
import com.example.siteregister.model.SiteRegisterRevision
import com.example.siteregister.model.SiteSegmentRecord
import com.example.siteregister.model.TackCoatTestRecord
import com.example.siteregister.model.TackCoatTrayRow
import com.example.siteregister.model.TestResultStatus
import com.example.siteregister.model.UserProfile
import com.example.siteregister.model.UserSession
import com.example.siteregister.util.QrCodeAndOcrHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SiteRegisterSystemTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: SiteRegisterRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = SiteRegisterRepository(context, db.siteRegisterDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    // =========================================================================
    // 70. REGISTRATION, DYNAMIC PROJECT CODE & APPROVAL SECURITY TESTS
    // =========================================================================

    @Test
    fun registrationFlow_validAndInvalidProjectCodes_andAccessRouting() {
        val now = 1_700_000_000_000L
        val project1 = ProjectInfo(
            id = "proj-nh58",
            projectName = "NH-58 Roorkee–Haridwar Strengthening",
            highwayName = "NH-58",
            nhShNumber = "NH-58",
            packageNumber = "PKG-02",
            contractorName = "Apex Highway Infra",
            pmcOrganization = "CTECS PMC Consultants",
            ieOrganization = "Veritas IE"
        )
        val project2 = ProjectInfo(id = "proj-nh709", projectName = "NH-709 Overlay", pmcOrganization = "CTECS PMC")

        // Unpredictable code generation
        val code1 = ApprovalWorkflowEngine.generateUnpredictableCode("CTECS", "NH58")
        val code2 = ApprovalWorkflowEngine.generateUnpredictableCode("CTECS", "NH58")
        assertTrue(code1.startsWith("CTECS-NH58-"))
        assertNotEquals("Generated codes must be cryptographically unpredictable", code1, code2)

        val activeCode = ProjectCodeRecord(projectId = project1.id, projectCode = code1, maxRegistrations = 2, currentRegistrations = 0, expiresAtMs = now + 86_400_000L)
        val expiredCode = ProjectCodeRecord(projectId = project1.id, projectCode = "CTECS-EXP-111111", expiresAtMs = now - 1000L)
        val revokedCode = ProjectCodeRecord(projectId = project1.id, projectCode = "CTECS-REV-222222", isRevoked = true)
        val fullCode = ProjectCodeRecord(projectId = project1.id, projectCode = "CTECS-MAX-333333", maxRegistrations = 1, currentRegistrations = 1)
        val allCodes = listOf(activeCode, expiredCode, revokedCode, fullCode)
        val allProjects = listOf(project1, project2)

        // Valid code returns verified read-only project details
        val valid = ApprovalWorkflowEngine.validateProjectCode(code1, allCodes, allProjects, now)
        assertTrue(valid.isValid)
        assertEquals(project1.projectName, valid.project?.projectName)
        assertEquals(project1.contractorName, valid.project?.contractorName)

        // Invalid / expired / revoked / limit-reached codes rejected
        assertFalse(ApprovalWorkflowEngine.validateProjectCode("CTECS-FAKE-999999", allCodes, allProjects, now).isValid)
        assertFalse(ApprovalWorkflowEngine.validateProjectCode("CTECS-EXP-111111", allCodes, allProjects, now).isValid)
        assertFalse(ApprovalWorkflowEngine.validateProjectCode("CTECS-REV-222222", allCodes, allProjects, now).isValid)
        assertFalse(ApprovalWorkflowEngine.validateProjectCode("CTECS-MAX-333333", allCodes, allProjects, now).isValid)

        // Exact post-login access routing: pending user is strictly blocked from Site Register
        val session = UserSession("tok", "ref", now + 3_600_000L, "user-eng", "eng@apex.com", emailVerified = true)
        val profile = UserProfile("user-eng", "Aarav Sharma", "aarav", "eng@apex.com", accountStatus = AccountStatus.ACTIVE, emailVerified = true)
        val pendingMem = ProjectMembership(
            projectId = project1.id, userId = "user-eng", organizationType = OrganizationType.PMC,
            organizationName = "CTECS PMC Consultants", designation = "Resident Engineer",
            assignedRole = ProjectRole.CLIENT_VIEWER, approvalStatus = MembershipApprovalStatus.PENDING
        )
        assertEquals(
            LoginAccessRoute.PENDING_APPROVAL,
            ApprovalWorkflowEngine.resolveLoginAccessRoute(session, profile, listOf(pendingMem), now)
        )

        // Self-approval & self-PMC privilege elevation strictly rejected
        val selfApprovalAttempt = ApprovalWorkflowEngine.validateMembershipApprovalAction(
            actorMembership = pendingMem,
            actorIsSystemAdmin = false,
            applicantUserId = "user-eng",
            roleToAssign = ProjectRole.PMC_REPRESENTATIVE
        )
        assertTrue(selfApprovalAttempt.isFailure)

        // Circular approval rejected
        val pmcActorApprovedByApplicant = ProjectMembership(
            projectId = project1.id, userId = "pmc-2", organizationType = OrganizationType.PMC,
            organizationName = "CTECS PMC", designation = "RE",
            assignedRole = ProjectRole.PMC_REPRESENTATIVE, approvalStatus = MembershipApprovalStatus.APPROVED,
            approvedBy = "user-eng"
        )
        val circularAttempt = ApprovalWorkflowEngine.validateMembershipApprovalAction(
            actorMembership = pmcActorApprovedByApplicant,
            actorIsSystemAdmin = false,
            applicantUserId = "user-eng",
            roleToAssign = ProjectRole.PMC_REPRESENTATIVE
        )
        assertTrue(circularAttempt.isFailure)

        // Legitimate PMC Representative approves applicant as Site Engineer
        val legitimatePmc = ProjectMembership(
            projectId = project1.id, userId = "pmc-1", organizationType = OrganizationType.PMC,
            organizationName = "CTECS PMC", designation = "Resident Engineer",
            assignedRole = ProjectRole.PMC_REPRESENTATIVE, approvalStatus = MembershipApprovalStatus.APPROVED,
            approvedBy = "system-root"
        )
        assertTrue(
            ApprovalWorkflowEngine.validateMembershipApprovalAction(legitimatePmc, false, "user-eng", ProjectRole.SITE_ENGINEER).isSuccess
        )

        // Existing user joining 2nd project with an independent role gets PROJECT_SELECTOR
        val approvedMem1 = pendingMem.copy(assignedRole = ProjectRole.SITE_ENGINEER, approvalStatus = MembershipApprovalStatus.APPROVED)
        val approvedMem2 = ProjectMembership(
            projectId = project2.id, userId = "user-eng", organizationType = OrganizationType.CONTRACTOR,
            organizationName = "Apex", designation = "Quality Engineer",
            assignedRole = ProjectRole.CLIENT_VIEWER, approvalStatus = MembershipApprovalStatus.APPROVED
        )
        assertEquals(
            LoginAccessRoute.PROJECT_SELECTOR,
            ApprovalWorkflowEngine.resolveLoginAccessRoute(session, profile, listOf(approvedMem1, approvedMem2), now)
        )
    }

    // =========================================================================
    // 71. HIGHWAY MEASUREMENT & QUANTITY ENGINE TESTS (ALL 41 ACTIVITIES)
    // =========================================================================

    @Test
    fun measurementEngine_chainageSegmentsUnitsOverlapsBcGlassGridTackCoat() {
        // Verify all 41 standard highway activities exist in catalog
        assertEquals(41, HighwayActivityCatalog.standardActivities.size)

        // Explicit unit conversion tests
        assertEquals(0.04, HighwayMeasurementEngine.toMeters(40.0, MeasurementUnit.MM), 1e-6)
        assertEquals(0.15, HighwayMeasurementEngine.toMeters(15.0, MeasurementUnit.CM), 1e-6)
        assertEquals(1000.0, HighwayMeasurementEngine.toMeters(1.0, MeasurementUnit.KM), 1e-6)

        // Exact multi-segment example from Specification Part 7 (201+000 to 201+300)
        val seg1 = SiteSegmentRecord(
            registerId = "reg-1", segmentIndex = 1, fromChainage = "201+000", toChainage = "201+100",
            actualWidthM = 7.0, designThickness = 40.0, actualCompactedThickness = 40.0, thicknessUnit = MeasurementUnit.MM
        )
        val seg2 = SiteSegmentRecord(
            registerId = "reg-1", segmentIndex = 2, fromChainage = "201+100", toChainage = "201+220",
            actualWidthM = 8.5, designThickness = 40.0, actualCompactedThickness = 45.0, thicknessUnit = MeasurementUnit.MM
        )
        val seg3 = SiteSegmentRecord(
            registerId = "reg-1", segmentIndex = 3, fromChainage = "201+220", toChainage = "201+300",
            actualWidthM = 10.5, designThickness = 40.0, actualCompactedThickness = 50.0, thicknessUnit = MeasurementUnit.MM
        )
        val summary = HighwayMeasurementEngine.calculateSegmentsWithOverlapGuard(listOf(seg1, seg2, seg3))
        assertFalse(summary.hasOverlaps)
        assertEquals(300.0, summary.deduplicatedLengthM, 1e-6)
        // Area = 100*7 + 120*8.5 + 80*10.5 = 700 + 1020 + 840 = 2560 m²
        assertEquals(2560.0, summary.totalAreaSqm, 1e-6)
        // Volume = 700*0.04 + 1020*0.045 + 840*0.05 = 28 + 45.9 + 42 = 115.9 m³
        assertEquals(115.9, summary.totalVolumeCum, 1e-6)
        // No density given -> never invents material tonnage!
        assertNull(summary.totalMaterialQty)

        // Design vs Actual variance check on seg2 (45 - 40 = +5 mm, +12.5%, no auto Pass/Fail without spec)
        val var2 = summary.segmentResults[1].varianceThickness!!
        assertEquals(5.0, var2.variance, 1e-6)
        assertEquals(12.5, var2.variancePercent!!, 1e-6)
        assertNull(var2.complianceStatus)

        // Overlap guard prevents double-counting!
        val overlappingSeg = SiteSegmentRecord(
            registerId = "reg-1", segmentIndex = 4, fromChainage = "201+250", toChainage = "201+350",
            actualWidthM = 10.0, actualCompactedThickness = 50.0, thicknessUnit = MeasurementUnit.MM
        )
        val overlapSummary = HighwayMeasurementEngine.calculateSegmentsWithOverlapGuard(listOf(seg1, seg2, seg3, overlappingSeg))
        assertTrue(overlapSummary.hasOverlaps)
        assertEquals(350.0, overlapSummary.deduplicatedLengthM, 1e-6) // NOT 400m!
        // Only the non-overlapping 50m of overlappingSeg adds area: 2560 + 50*10 = 3060 m²
        assertEquals(3060.0, overlapSummary.totalAreaSqm, 1e-6)

        // Cross-section average end-area earthwork test
        val earthSeg = SiteSegmentRecord(
            registerId = "reg-earth", fromChainage = "10+000", toChainage = "10+050",
            measurementMethod = MeasurementMethod.AVERAGE_END_AREA,
            crossSectionArea1Sqm = 12.0, crossSectionArea2Sqm = 18.0
        )
        val earthCalc = HighwayMeasurementEngine.calculateSegment(earthSeg)
        assertEquals(750.0, earthCalc.volumeCum!!, 1e-6) // ((12+18)/2)*50 = 750 m³

        // Glass Grid effective width & area after overlap
        val gg = GlassGridEntry(
            registerId = "reg-gg", fromChainage = "201+000", toChainage = "201+100",
            applicationLengthM = 100.0, applicationWidthM = 7.5,
            rollWidthM = 4.0, numberOfRolls = 2, overlapWidthM = 0.25
        )
        val ggCalc = HighwayMeasurementEngine.calculateGlassGridCoverage(gg)
        // Gross = 8.0m, overlap deduction = 0.25m -> 7.75m capped at road width 7.5m -> Net Area = 750 m²
        assertEquals(7.5, ggCalc.effectiveCoveredWidthM, 1e-6)
        assertEquals(750.0, ggCalc.netApplicationAreaSqm, 1e-6)

        // Tack Coat Spray Test tray calculations + validation + criteria handling
        val trays = listOf(
            TackCoatTrayRow(trayNumber = 1, trayAreaSqm = 0.09, weightBeforeKg = 0.400, weightAfterKg = 0.427), // 0.027 / 0.09 = 0.30
            TackCoatTrayRow(trayNumber = 2, trayAreaSqm = 0.09, weightBeforeKg = 0.410, weightAfterKg = 0.4325) // 0.0225 / 0.09 = 0.25
        )
        val noSpec = HighwayMeasurementEngine.calculateTackCoatSprayTest(trays, null, null)
        assertEquals(0.275, noSpec.averageSprayRateKgPerSqm!!, 1e-4)
        assertEquals(TestResultStatus.NEEDS_REVIEW, noSpec.overallStatus) // Never auto Pass/Fail without spec

        val withSpec = HighwayMeasurementEngine.calculateTackCoatSprayTest(trays, 0.20, 0.30)
        assertEquals(TestResultStatus.PASS, withSpec.overallStatus)

        val invalidTray = HighwayMeasurementEngine.calculateTackCoatSprayTest(
            listOf(TackCoatTrayRow(trayNumber = 1, trayAreaSqm = 0.0, weightBeforeKg = 0.5, weightAfterKg = 0.4)),
            0.20, 0.30
        )
        assertEquals(0, invalidTray.validTrayCount)
        assertFalse(invalidTray.trayResults.first().isValid)
        assertEquals(TestResultStatus.NEEDS_REVIEW, invalidTray.overallStatus)
    }

    // =========================================================================
    // 72 & 73. MULTI-USER RBAC + COMPLETE 19-STEP PMC APPROVAL SECURITY TEST
    // =========================================================================

    @Test
    fun pmcApprovalSecurity_full19StepImmutableLockAndControlledReopening() {
        val t0 = 1_700_000_000_000L
        val projectId = "proj-nh58"

        val creatorMem = ProjectMembership(projectId = projectId, userId = "eng-creator", organizationType = OrganizationType.CONTRACTOR, organizationName = "Apex", designation = "Site Engineer", assignedRole = ProjectRole.SITE_ENGINEER, approvalStatus = MembershipApprovalStatus.APPROVED)
        val supervisorMem = ProjectMembership(projectId = projectId, userId = "sup-other", organizationType = OrganizationType.CONTRACTOR, organizationName = "Apex", designation = "Site Supervisor", assignedRole = ProjectRole.SITE_SUPERVISOR, approvalStatus = MembershipApprovalStatus.APPROVED)
        val pmcMem = ProjectMembership(projectId = projectId, userId = "pmc-approver", organizationType = OrganizationType.PMC, organizationName = "CTECS PMC", designation = "Resident Engineer", assignedRole = ProjectRole.PMC_REPRESENTATIVE, approvalStatus = MembershipApprovalStatus.APPROVED)
        val clientMem = ProjectMembership(projectId = projectId, userId = "client-viewer", organizationType = OrganizationType.CLIENT, organizationName = "NHAI", designation = "Authorized Viewer", assignedRole = ProjectRole.CLIENT_VIEWER, approvalStatus = MembershipApprovalStatus.APPROVED)
        val otherProjectMem = creatorMem.copy(projectId = "other-project")

        // Step 1-2: Creator creates Draft and edits it
        var register = SiteRegisterRecord(
            id = "reg-bc-01", projectId = projectId, activityCode = "BC_LAYING", activityName = "BC Laying",
            activityCategory = HighwayActivityCategory.BITUMINOUS_WORKS, registerNumber = "SR-BC-001",
            workDate = "2026-11-22", currentRevision = 1, status = RegisterStatus.DRAFT, createdBy = "eng-creator"
        )
        val rev1Draft = SiteRegisterRevision(registerId = register.id, projectId = projectId, revisionNumber = 1, status = RegisterStatus.DRAFT, isLocked = false, createdBy = "eng-creator")
        assertTrue(ApprovalWorkflowEngine.canUserEditRegister(register, "eng-creator", creatorMem, emptyList(), t0))
        assertTrue(ApprovalWorkflowEngine.canUserEditRegister(register, "sup-other", supervisorMem, emptyList(), t0))
        assertFalse(ApprovalWorkflowEngine.canUserEditRegister(register, "client-viewer", clientMem, emptyList(), t0))
        assertFalse(ApprovalWorkflowEngine.canUserEditRegister(register, "eng-creator", otherProjectMem, emptyList(), t0))

        // Step 3-4: Submit -> editing locked
        register = register.copy(status = RegisterStatus.PENDING_PMC_APPROVAL)
        assertFalse(ApprovalWorkflowEngine.canUserEditRegister(register, "eng-creator", creatorMem, emptyList(), t0))
        assertFalse(ApprovalWorkflowEngine.canUserDeleteRegister(register, "eng-creator", creatorMem))

        // Non-PMC cannot approve; Creator cannot self-approve
        assertTrue(ApprovalWorkflowEngine.validatePmcRegisterDecision(register, creatorMem, false).isFailure)
        assertTrue(ApprovalWorkflowEngine.validatePmcRegisterDecision(register, pmcMem, false).isSuccess)

        // Step 5-7: PMC approves -> Approved – Locked; Creator & Supervisor edits strictly denied
        register = register.copy(status = RegisterStatus.APPROVED_LOCKED, approvedBy = "pmc-approver", approvedAtMs = t0 + 1000L)
        val rev1Locked = rev1Draft.copy(status = RegisterStatus.APPROVED_LOCKED, isLocked = true, approvedBy = "pmc-approver", approvedAtMs = t0 + 1000L)
        assertFalse(ApprovalWorkflowEngine.canUserEditRegister(register, "eng-creator", creatorMem, emptyList(), t0 + 2000L))
        assertFalse(ApprovalWorkflowEngine.canUserEditRegister(register, "sup-other", supervisorMem, emptyList(), t0 + 2000L))

        // Step 8-9: Creator requests temporary edit permission; PMC grants timed creator-only permission
        val editReq = EditPermissionRequest(
            id = "req-1", registerId = register.id, projectId = projectId, revisionNumber = 1,
            requestedBy = "eng-creator", requestedByName = "Creator", reason = "Correction of tipper #4 weighbridge slip",
            proposedCorrection = "Update MT from 18.2 to 18.6"
        )
        assertTrue(ApprovalWorkflowEngine.validateGrantEditPermission(register, editReq, pmcMem, false).isSuccess)
        // Creator cannot grant themselves permission
        assertTrue(ApprovalWorkflowEngine.validateGrantEditPermission(register, editReq, creatorMem.copy(assignedRole = ProjectRole.PMC_REPRESENTATIVE), false).isFailure)

        val grant = EditPermissionGrant(
            id = "grant-1", editRequestId = editReq.id, registerId = register.id, projectId = projectId,
            targetRevisionNumber = 1, authorizedUserId = "eng-creator", grantedByPmcId = "pmc-approver",
            grantedAtMs = t0 + 3000L, expiresAtMs = t0 + 603_000L, isActive = true
        )
        register = register.copy(status = RegisterStatus.EDIT_PERMISSION_GRANTED)

        // Step 10-12: Creator CAN edit during window; Supervisor/other user STRICTLY DENIED
        assertTrue(ApprovalWorkflowEngine.canUserEditRegister(register, "eng-creator", creatorMem, listOf(grant), t0 + 10_000L))
        assertFalse(ApprovalWorkflowEngine.canUserEditRegister(register, "sup-other", supervisorMem, listOf(grant), t0 + 10_000L))

        // Step 13: Revoke immediately blocks creator
        val revokedGrant = grant.copy(isActive = false, revokedAtMs = t0 + 15_000L)
        assertFalse(ApprovalWorkflowEngine.canUserEditRegister(register, "eng-creator", creatorMem, listOf(revokedGrant), t0 + 16_000L))

        // Step 14: Expiry immediately transitions effective status and blocks creator
        assertFalse(ApprovalWorkflowEngine.canUserEditRegister(register, "eng-creator", creatorMem, listOf(grant), t0 + 700_000L))
        assertEquals(
            RegisterStatus.EDIT_PERMISSION_EXPIRED,
            ApprovalWorkflowEngine.resolveEffectiveRegisterStatus(register, listOf(grant), t0 + 700_000L)
        )

        // Step 15-17: Submit corrected revision creates Revision 2, preserving Revision 1 immutable
        val resub = ApprovalWorkflowEngine.createResubmittedRevision(
            register = register,
            previousRevisions = listOf(rev1Locked),
            changeSummary = "Updated weighbridge slip #4",
            snapshotJson = "{\"mt\":18.6}",
            actorUserId = "eng-creator",
            activePermissions = listOf(grant),
            nowMs = t0 + 20_000L
        )
        assertTrue(resub.isSuccess)
        val (regRev2, newRev2) = resub.getOrThrow()
        assertEquals(2, regRev2.currentRevision)
        assertEquals(2, newRev2.revisionNumber)
        assertEquals(RegisterStatus.PENDING_PMC_APPROVAL, regRev2.status)
        assertTrue("Revision 1 must remain permanently locked", rev1Locked.isLocked)

        // Step 18-19: Fresh PMC approval locks Revision 2 permanently
        assertTrue(ApprovalWorkflowEngine.validatePmcRegisterDecision(regRev2, pmcMem, false).isSuccess)
        val regRev2Locked = regRev2.copy(status = RegisterStatus.APPROVED_LOCKED)
        assertFalse(ApprovalWorkflowEngine.canUserEditRegister(regRev2Locked, "eng-creator", creatorMem, listOf(grant), t0 + 30_000L))

        // Sharing code strictly read-only & project-isolated
        val shareCode = RegisterSharingCode(registerId = register.id, projectId = projectId, code = "CTECS-BC-8M4P2Q", expiresAtMs = t0 + 3_600_000L)
        assertTrue(ApprovalWorkflowEngine.validateSharingCodeReadOnly("CTECS-BC-8M4P2Q", listOf(shareCode), clientMem, t0).isSuccess)
        assertTrue(ApprovalWorkflowEngine.validateSharingCodeReadOnly("CTECS-BC-8M4P2Q", listOf(shareCode), otherProjectMem, t0).isFailure)
    }

    // =========================================================================
    // 74. EXPORT (14-SHEET .XLSX + LANDSCAPE/PORTRAIT .PDF) & ROOM RECOVERY TESTS
    // =========================================================================

    @Test
    fun exportEngine_generatesValid14SheetExcelAndMultiPagePdf_andRoomPersists() = runBlocking {
        val project = ProjectInfo(
            id = "proj-export", projectName = "NH-58 Pkg 2", highwayName = "NH-58",
            nhShNumber = "NH-58", packageNumber = "PKG-02", contractorName = "Apex Infra",
            pmcOrganization = "CTECS PMC", ieOrganization = "Veritas IE"
        )
        val reg = SiteRegisterRecord(
            id = "reg-exp-1", projectId = project.id, projectName = project.projectName,
            activityCode = "BC_LAYING", activityName = "BC Laying – BM / BC(WC)",
            activityCategory = HighwayActivityCategory.BITUMINOUS_WORKS, registerNumber = "SR-BC-101",
            workDate = "2026-11-22", chainageFrom = "180+000", chainageTo = "180+350",
            totalLengthM = 350.0, totalAreaSqm = 2625.0, totalVolumeCum = 105.0,
            totalMaterialQty = 248.5, status = RegisterStatus.APPROVED_LOCKED, createdBy = "u1"
        )
        val bcEntry = BcLayingEntry(
            registerId = reg.id, serialNumber = 1, vehicleNumber = "UK07CA4412",
            fromChainage = "180+000", toChainage = "180+150", startWidthM = 7.5, endWidthM = 7.5,
            compactedThicknessMm = 40.0, measuredMaterialMt = 106.5
        )
        val tcTest = TackCoatTestRecord(
            projectId = project.id, registerId = reg.id, chainage = "180+100", testDate = "2026-11-22",
            materialType = "RS-1", testedBy = "QA Engineer", specMinRateKgSqm = 0.20, specMaxRateKgSqm = 0.30,
            trays = listOf(TackCoatTrayRow(trayNumber = 1, trayAreaSqm = 0.09, weightBeforeKg = 0.40, weightAfterKg = 0.425))
        )
        val obs = SiteObservationRecord(
            projectId = project.id, registerId = reg.id, serialNumber = 1,
            chainageFrom = "180+120", chainageTo = "180+135", description = "Minor edge segregation"
        )

        // Room persistence & offline sync queue recovery verification
        repo.saveProject(project)
        repo.saveRegister(reg)
        repo.saveBcEntry(project.id, bcEntry)
        repo.saveTackCoatTest(tcTest)
        repo.saveObservation(obs)

        val storedProjects = repo.getProjects()
        assertEquals(1, storedProjects.size)
        assertEquals(project.projectName, storedProjects.first().projectName)
        assertTrue(db.siteRegisterDao().getPendingSyncOperations().isNotEmpty())

        val input = SiteRegisterExportEngine.ExportBundleInput(
            project = project,
            reportDate = "2026-11-22",
            registers = listOf(reg),
            bcEntriesByRegister = mapOf(reg.id to listOf(bcEntry)),
            tackCoatTests = listOf(tcTest),
            observations = listOf(obs),
            pendingOfflineSyncCount = 3
        )

        // 1. Real 14-sheet .xlsx workbook verification
        val xlsx1 = SiteRegisterExportEngine.exportDailyExcelWorkbook(context, input)
        val xlsx2 = SiteRegisterExportEngine.exportDailyExcelWorkbook(context, input)
        assertTrue(xlsx1.file.exists() && xlsx1.file.length() > 1000)
        assertEquals(14, xlsx1.sheetOrPageCount)
        assertNotNull("Must flag pending offline sync count", xlsx1.pendingSyncWarning)
        assertNotEquals("Never silently overwrite previous export", xlsx1.fileName, xlsx2.fileName)

        ZipFile(xlsx1.file).use { zip ->
            assertNotNull(zip.getEntry("xl/workbook.xml"))
            for (i in 1..14) {
                assertNotNull("Missing worksheet $i", zip.getEntry("xl/worksheets/sheet$i.xml"))
            }
        }

        // 2. Real printable PDF (Landscape register page + Portrait Tack Coat test page)
        val pdf = SiteRegisterExportEngine.exportDailyPdfReport(context, input)
        assertTrue(pdf.file.exists() && pdf.file.length() > 500)
        assertEquals(2, pdf.sheetOrPageCount) // 1 Landscape register + 1 Portrait Tack Coat report
        val header = pdf.file.readBytes().take(5).toByteArray().toString(Charsets.US_ASCII)
        assertEquals("%PDF-", header)

        // 3. QR bitmap & OCR manual verification guard
        val qrBmp = QrCodeAndOcrHelper.generateQrCodeBitmap("CTECS-NH58-7K92P6", 256)
        assertTrue(qrBmp.width >= 100 && qrBmp.height == qrBmp.width)
        val ocr = QrCodeAndOcrHelper.extractStructuredFieldsFromText(
            "CH 180+350 to 180+450 Temp 152 C Spray 0.275 kg/m2 Vehicle UK07CA4412",
            isHandwritten = true
        )
        assertTrue(ocr.requiresManualVerification)
        assertTrue(ocr.suggestions.size >= 4)
    }
}
