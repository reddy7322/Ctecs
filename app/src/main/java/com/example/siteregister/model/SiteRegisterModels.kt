package com.example.siteregister.model

import java.util.UUID

// ============================================================================
// 1. ORGANIZATION TYPES, DESIGNATIONS & PROJECT ROLES (STRICTLY SEPARATED)
// ============================================================================

enum class OrganizationType(val dbValue: String, val displayLabel: String) {
    CONTRACTOR("CONTRACTOR", "Contractor"),
    PMC("PMC", "PMC"),
    IE("IE", "IE"),
    CONCESSIONAIRE("CONCESSIONAIRE", "Concessionaire"),
    CLIENT("CLIENT", "Client");

    companion object {
        fun fromDb(value: String?): OrganizationType =
            entries.firstOrNull { it.dbValue.equals(value?.trim(), ignoreCase = true) } ?: CONTRACTOR
    }
}

/**
 * Project-based assigned roles — strictly independent from self-selected [OrganizationType].
 * Selecting PMC during registration NEVER automatically assigns [PMC_REPRESENTATIVE].
 */
enum class ProjectRole(
    val dbValue: String,
    val displayLabel: String,
    val canCreateRegister: Boolean,
    val canEditDraft: Boolean,
    val canSubmitRegister: Boolean,
    val canSignRegister: Boolean,
    val canAddObservation: Boolean,
    val canRespondObservation: Boolean,
    val canVerifyObservation: Boolean,
    val canApproveRegister: Boolean,
    val canManageEditPermissions: Boolean,
    val canManageProjectAndUsers: Boolean,
    val canViewAuditHistory: Boolean,
    val canGenerateReports: Boolean
) {
    SITE_ENGINEER(
        "SITE_ENGINEER", "Site Engineer",
        canCreateRegister = true, canEditDraft = true, canSubmitRegister = true,
        canSignRegister = true, canAddObservation = true, canRespondObservation = true,
        canVerifyObservation = false, canApproveRegister = false, canManageEditPermissions = false,
        canManageProjectAndUsers = false, canViewAuditHistory = false, canGenerateReports = true
    ),
    SITE_SUPERVISOR(
        "SITE_SUPERVISOR", "Site Supervisor",
        canCreateRegister = false, canEditDraft = true, canSubmitRegister = false,
        canSignRegister = false, canAddObservation = true, canRespondObservation = true,
        canVerifyObservation = false, canApproveRegister = false, canManageEditPermissions = false,
        canManageProjectAndUsers = false, canViewAuditHistory = false, canGenerateReports = false
    ),
    CONTRACTOR_SITE_ENGINEER(
        "CONTRACTOR_SITE_ENGINEER", "Contractor Site Engineer",
        canCreateRegister = true, canEditDraft = true, canSubmitRegister = true,
        canSignRegister = true, canAddObservation = true, canRespondObservation = true,
        canVerifyObservation = false, canApproveRegister = false, canManageEditPermissions = false,
        canManageProjectAndUsers = false, canViewAuditHistory = false, canGenerateReports = true
    ),
    CONTRACTOR_REPRESENTATIVE(
        "CONTRACTOR_REPRESENTATIVE", "Contractor Representative",
        canCreateRegister = false, canEditDraft = false, canSubmitRegister = false,
        canSignRegister = true, canAddObservation = true, canRespondObservation = true,
        canVerifyObservation = false, canApproveRegister = false, canManageEditPermissions = false,
        canManageProjectAndUsers = false, canViewAuditHistory = false, canGenerateReports = true
    ),
    IE_REPRESENTATIVE(
        "IE_REPRESENTATIVE", "IE Representative",
        canCreateRegister = false, canEditDraft = false, canSubmitRegister = false,
        canSignRegister = true, canAddObservation = true, canRespondObservation = false,
        canVerifyObservation = true, canApproveRegister = false, canManageEditPermissions = false,
        canManageProjectAndUsers = false, canViewAuditHistory = true, canGenerateReports = true
    ),
    PMC_REPRESENTATIVE(
        "PMC_REPRESENTATIVE", "PMC Representative",
        canCreateRegister = false, canEditDraft = false, canSubmitRegister = false,
        canSignRegister = true, canAddObservation = true, canRespondObservation = false,
        canVerifyObservation = true, canApproveRegister = true, canManageEditPermissions = true,
        canManageProjectAndUsers = true, canViewAuditHistory = true, canGenerateReports = true
    ),
    PROJECT_MANAGER(
        "PROJECT_MANAGER", "Project Manager",
        canCreateRegister = false, canEditDraft = false, canSubmitRegister = false,
        canSignRegister = true, canAddObservation = true, canRespondObservation = false,
        canVerifyObservation = false, canApproveRegister = false, canManageEditPermissions = false,
        canManageProjectAndUsers = false, canViewAuditHistory = true, canGenerateReports = true
    ),
    CLIENT_VIEWER(
        "CLIENT_VIEWER", "Client Viewer",
        canCreateRegister = false, canEditDraft = false, canSubmitRegister = false,
        canSignRegister = false, canAddObservation = false, canRespondObservation = false,
        canVerifyObservation = false, canApproveRegister = false, canManageEditPermissions = false,
        canManageProjectAndUsers = false, canViewAuditHistory = false, canGenerateReports = true
    ),
    PROJECT_ADMINISTRATOR(
        "PROJECT_ADMINISTRATOR", "Project Administrator",
        canCreateRegister = true, canEditDraft = true, canSubmitRegister = true,
        canSignRegister = true, canAddObservation = true, canRespondObservation = true,
        canVerifyObservation = true, canApproveRegister = true, canManageEditPermissions = true,
        canManageProjectAndUsers = true, canViewAuditHistory = true, canGenerateReports = true
    ),
    SYSTEM_ADMINISTRATOR(
        "SYSTEM_ADMINISTRATOR", "System Administrator",
        canCreateRegister = true, canEditDraft = true, canSubmitRegister = true,
        canSignRegister = true, canAddObservation = true, canRespondObservation = true,
        canVerifyObservation = true, canApproveRegister = true, canManageEditPermissions = true,
        canManageProjectAndUsers = true, canViewAuditHistory = true, canGenerateReports = true
    );

    val isPrivilegedPmcOrAdmin: Boolean
        get() = this == PMC_REPRESENTATIVE || this == PROJECT_ADMINISTRATOR || this == SYSTEM_ADMINISTRATOR

    companion object {
        fun fromDb(value: String?): ProjectRole =
            entries.firstOrNull { it.dbValue.equals(value?.trim(), ignoreCase = true) } ?: CLIENT_VIEWER
    }
}

object DesignationCatalog {
    private val map: Map<OrganizationType, List<String>> = mapOf(
        OrganizationType.CONTRACTOR to listOf(
            "Project Manager",
            "Construction Manager",
            "Site Engineer",
            "Site Supervisor",
            "Quality Engineer",
            "Material Engineer",
            "Contractor Representative"
        ),
        OrganizationType.PMC to listOf(
            "Resident Engineer",
            "Project Manager",
            "Highway Engineer",
            "Quality Engineer",
            "PMC Representative",
            "Site Engineer"
        ),
        OrganizationType.IE to listOf(
            "Team Leader",
            "Resident Engineer",
            "IE Representative",
            "Quality Engineer",
            "Site Inspector"
        ),
        OrganizationType.CONCESSIONAIRE to listOf(
            "Project Manager",
            "Highway Engineer",
            "Maintenance Engineer",
            "Site Supervisor"
        ),
        OrganizationType.CLIENT to listOf(
            "Client Representative",
            "Project Manager",
            "Authorized Viewer"
        )
    )

    fun forOrganizationType(
        orgType: OrganizationType,
        customDesignations: List<String> = emptyList()
    ): List<String> = (map[orgType].orEmpty() + customDesignations).distinct()
}

// ============================================================================
// 2. AUTHENTICATION, MEMBERSHIP, PROJECT CODE & ACCESS ROUTING
// ============================================================================

enum class AccountStatus(val dbValue: String) {
    ACTIVE("ACTIVE"),
    INCOMPLETE("INCOMPLETE"),
    SUSPENDED("SUSPENDED"),
    DEACTIVATED("DEACTIVATED");

    companion object {
        fun fromDb(v: String?) = entries.firstOrNull { it.dbValue.equals(v, true) } ?: ACTIVE
    }
}

enum class MembershipApprovalStatus(val dbValue: String, val label: String) {
    PENDING("PENDING", "Pending PMC Approval"),
    APPROVED("APPROVED", "Approved"),
    REJECTED("REJECTED", "Rejected"),
    INFO_REQUESTED("INFO_REQUESTED", "Additional Info Requested"),
    SUSPENDED("SUSPENDED", "Suspended"),
    REVOKED("REVOKED", "Revoked");

    companion object {
        fun fromDb(v: String?) = entries.firstOrNull { it.dbValue.equals(v, true) } ?: PENDING
    }
}

/**
 * Exact post-authentication routing rules per Specification Part 2.10.
 */
enum class LoginAccessRoute {
    UNAUTHENTICATED,
    COMPLETE_PROFILE,
    VERIFY_EMAIL,
    PENDING_APPROVAL,
    REJECTED_STATUS,
    ACCESS_BLOCKED,
    JOIN_PROJECT_CODE,
    PROJECT_SELECTOR,
    PROJECT_DASHBOARD
}

data class UserSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long,
    val userId: String,
    val email: String,
    val emailVerified: Boolean = true
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean = nowMs >= expiresAtEpochMs - 30_000L
}

data class UserProfile(
    val id: String,
    val fullName: String,
    val username: String,
    val email: String,
    val mobileNumber: String = "",
    val defaultDesignation: String = "",
    val defaultOrganizationType: OrganizationType = OrganizationType.CONTRACTOR,
    val defaultOrganizationName: String = "",
    val isSystemAdmin: Boolean = false,
    val accountStatus: AccountStatus = AccountStatus.ACTIVE,
    val emailVerified: Boolean = true,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class ProjectInfo(
    val id: String = UUID.randomUUID().toString(),
    val projectName: String,
    val highwayName: String = "",
    val nhShNumber: String = "",
    val packageNumber: String = "",
    val projectLocation: String = "",
    val stateName: String = "",
    val contractorName: String = "",
    val concessionaireName: String = "",
    val clientName: String = "",
    val pmcOrganization: String = "",
    val ieOrganization: String = "",
    val assignedPmcName: String = "",
    val assignedPmcContact: String = "",
    val projectIdentification: String = "",
    val projectStatus: String = "ACTIVE",
    val registrationStatus: String = "OPEN",
    val permittedOrganizationTypes: List<OrganizationType> = OrganizationType.entries,
    val organizationNamesByType: Map<OrganizationType, List<String>> = emptyMap(),
    val customDesignationsByType: Map<OrganizationType, List<String>> = emptyMap(),
    val createdBy: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

data class ProjectCodeRecord(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val projectCode: String,
    val permittedOrganizationTypes: List<OrganizationType> = OrganizationType.entries,
    val maxRegistrations: Int? = null,
    val currentRegistrations: Int = 0,
    val expiresAtMs: Long? = null,
    val isEnabled: Boolean = true,
    val isRevoked: Boolean = false,
    val createdBy: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
) {
    fun isCurrentlyUsable(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (isRevoked || !isEnabled) return false
        if (expiresAtMs != null && nowMs >= expiresAtMs) return false
        if (maxRegistrations != null && currentRegistrations >= maxRegistrations) return false
        return true
    }
}

data class VerifiedProjectCodeResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val project: ProjectInfo? = null,
    val codeRecord: ProjectCodeRecord? = null
)

data class ProjectMembership(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val userId: String,
    val projectName: String = "",
    val organizationType: OrganizationType,
    val organizationName: String,
    val organizationVerified: Boolean = false,
    val designation: String,
    val assignedRole: ProjectRole = ProjectRole.CLIENT_VIEWER,
    val approvalStatus: MembershipApprovalStatus = MembershipApprovalStatus.PENDING,
    val projectCodeUsed: String = "",
    val approvedBy: String? = null,
    val approvedAtMs: Long? = null,
    val remarks: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

data class RegistrationRequestRecord(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val projectName: String,
    val userId: String,
    val projectCodeUsed: String,
    val fullName: String,
    val username: String,
    val email: String,
    val mobileNumber: String,
    val requestedOrganizationType: OrganizationType,
    val requestedOrganizationName: String,
    val isUnlistedOrganization: Boolean = false,
    val organizationVerified: Boolean = false,
    val requestedDesignation: String,
    val status: MembershipApprovalStatus = MembershipApprovalStatus.PENDING,
    val assignedRole: ProjectRole? = null,
    val pmcRemarks: String = "",
    val reviewedBy: String? = null,
    val reviewedAtMs: Long? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)

// ============================================================================
// 3. ALL 41 HIGHWAY ACTIVITIES & DYNAMIC TEMPLATES
// ============================================================================

enum class HighwayActivityCategory(val dbValue: String, val displayLabel: String) {
    BITUMINOUS_WORKS("BITUMINOUS_WORKS", "A. Bituminous Works"),
    PAVEMENT_PREPARATION("PAVEMENT_PREPARATION", "B. Pavement Preparation & Strengthening"),
    EARTHWORK_GRANULAR("EARTHWORK_GRANULAR", "C. Earthwork & Granular Works"),
    CONCRETE_STRUCTURES("CONCRETE_STRUCTURES", "D. Concrete & Structures"),
    ROAD_SAFETY_FINISHING("ROAD_SAFETY_FINISHING", "E. Road Safety & Finishing"),
    CUSTOM("CUSTOM", "Custom Project Activities")
}

enum class MeasurementUnit(val symbol: String, val dimension: UnitDimension) {
    MM("mm", UnitDimension.LENGTH),
    CM("cm", UnitDimension.LENGTH),
    M("m", UnitDimension.LENGTH),
    KM("km", UnitDimension.LENGTH),
    SQM("m²", UnitDimension.AREA),
    CUM("m³", UnitDimension.VOLUME),
    KG("kg", UnitDimension.MASS),
    MT("MT", UnitDimension.MASS);

    companion object {
        fun fromSymbol(sym: String?): MeasurementUnit =
            entries.firstOrNull {
                it.symbol.equals(sym?.trim(), true) ||
                    it.name.equals(sym?.trim(), true) ||
                    (sym?.equals("m2", true) == true && it == SQM) ||
                    (sym?.equals("m3", true) == true && it == CUM)
            } ?: M
    }
}

enum class UnitDimension { LENGTH, AREA, VOLUME, MASS }

enum class QuantityClassification(val dbValue: String, val badgeLabel: String) {
    ESTIMATED("ESTIMATED", "Estimated"),
    MEASURED("MEASURED", "Measured"),
    VERIFIED("VERIFIED", "Verified");

    companion object {
        fun fromDb(v: String?) = entries.firstOrNull { it.dbValue.equals(v, true) } ?: MEASURED
    }
}

enum class MeasurementMethod(val dbValue: String, val displayLabel: String) {
    UNIFORM_SECTION("UNIFORM_SECTION", "Uniform Section (L × W × D)"),
    AVERAGE_END_AREA("AVERAGE_END_AREA", "Cross-Section Average End-Area"),
    IRREGULAR_SURVEYED("IRREGULAR_SURVEYED", "Irregular / Surveyed Area"),
    STRUCTURAL_UNITS("STRUCTURAL_UNITS", "Structural Element Units");

    companion object {
        fun fromDb(v: String?) = entries.firstOrNull { it.dbValue.equals(v, true) } ?: UNIFORM_SECTION
    }
}

data class HighwayActivityTemplate(
    val code: String,
    val name: String,
    val category: HighwayActivityCategory,
    val primaryUnit: MeasurementUnit,
    val defaultDepthUnit: MeasurementUnit = MeasurementUnit.MM,
    val volumeApplicable: Boolean = true,
    val supportsVehicleLaying: Boolean = false,
    val supportsTemperature: Boolean = false,
    val supportsSprayRate: Boolean = false,
    val supportsGlassGrid: Boolean = false,
    val supportsCrossSection: Boolean = false,
    val supportsStructuralSubtypes: List<String> = emptyList(),
    val specificFields: List<String> = emptyList(),
    val isCustom: Boolean = false,
    val templateVersion: Int = 1
)

object HighwayActivityCatalog {
    val standardActivities: List<HighwayActivityTemplate> = listOf(
        // A. Bituminous Works (1-10)
        HighwayActivityTemplate("BC_LAYING", "BC Laying – BM / BC(WC)", HighwayActivityCategory.BITUMINOUS_WORKS, MeasurementUnit.MT, MeasurementUnit.MM, true, supportsVehicleLaying = true, supportsTemperature = true, specificFields = listOf("Vehicle Number", "BC Type", "Layer", "Laying Temp (°C)", "Rolling Temp (°C)", "Loose Thickness (mm)", "Compacted Thickness (mm)")),
        HighwayActivityTemplate("DBM_LAYING", "DBM Laying", HighwayActivityCategory.BITUMINOUS_WORKS, MeasurementUnit.MT, MeasurementUnit.MM, true, supportsVehicleLaying = true, supportsTemperature = true, specificFields = listOf("Vehicle Number", "Laying Temp (°C)", "Rolling Temp (°C)", "Loose Thickness (mm)", "Compacted Thickness (mm)")),
        HighwayActivityTemplate("BM_LAYING", "BM Laying", HighwayActivityCategory.BITUMINOUS_WORKS, MeasurementUnit.MT, MeasurementUnit.MM, true, supportsVehicleLaying = true, supportsTemperature = true, specificFields = listOf("Vehicle Number", "Temperature (°C)", "Loose Thickness (mm)", "Compacted Thickness (mm)")),
        HighwayActivityTemplate("SDBC_LAYING", "SDBC Laying", HighwayActivityCategory.BITUMINOUS_WORKS, MeasurementUnit.SQM, MeasurementUnit.MM, true, supportsTemperature = true, supportsSprayRate = true, specificFields = listOf("Application Rate", "Material Type", "Layer", "Surface Condition")),
        HighwayActivityTemplate("TACK_COAT", "Tack Coat Application", HighwayActivityCategory.BITUMINOUS_WORKS, MeasurementUnit.SQM, MeasurementUnit.MM, volumeApplicable = false, supportsSprayRate = true, specificFields = listOf("Material Type", "Spray Rate (kg/m²)", "Estimated Requirement", "Actual Consumption", "Surface Condition", "Uniformity")),
        HighwayActivityTemplate("PRIME_COAT", "Prime Coat Application", HighwayActivityCategory.BITUMINOUS_WORKS, MeasurementUnit.SQM, MeasurementUnit.MM, volumeApplicable = false, supportsSprayRate = true, specificFields = listOf("Application Rate (kg/m²)", "Surface Preparation", "Weather")),
        HighwayActivityTemplate("SURFACE_DRESSING", "Surface Dressing", HighwayActivityCategory.BITUMINOUS_WORKS, MeasurementUnit.SQM, MeasurementUnit.MM, volumeApplicable = false, supportsSprayRate = true, specificFields = listOf("Application Rate", "Material Type", "Surface Condition")),
        HighwayActivityTemplate("BITUMINOUS_SURFACE_TREATMENT", "Bituminous Surface Treatment", HighwayActivityCategory.BITUMINOUS_WORKS, MeasurementUnit.SQM, MeasurementUnit.MM, volumeApplicable = false, supportsSprayRate = true, specificFields = listOf("Application Rate", "Material Type", "Layer")),
        HighwayActivityTemplate("MICROSURFACING", "Microsurfacing", HighwayActivityCategory.BITUMINOUS_WORKS, MeasurementUnit.SQM, MeasurementUnit.MM, volumeApplicable = false, supportsSprayRate = true, specificFields = listOf("Application Rate", "Material Type", "Surface Condition")),
        HighwayActivityTemplate("DENSE_GRADED_BITUMINOUS_MACADAM", "Dense Graded Bituminous Macadam", HighwayActivityCategory.BITUMINOUS_WORKS, MeasurementUnit.MT, MeasurementUnit.MM, true, supportsVehicleLaying = true, supportsTemperature = true, specificFields = listOf("Vehicle Number", "Laying Temp (°C)", "Rolling Temp (°C)", "Compacted Thickness (mm)")),

        // B. Pavement Preparation and Strengthening (11-18)
        HighwayActivityTemplate("MILLING", "Milling / Pavement Milling", HighwayActivityCategory.PAVEMENT_PREPARATION, MeasurementUnit.CUM, MeasurementUnit.MM, true, specificFields = listOf("Design Milling Depth (mm)", "Actual Milling Depth (mm)", "Milling Machine", "Disposal Details")),
        HighwayActivityTemplate("SURFACE_PREPARATION", "Surface Preparation", HighwayActivityCategory.PAVEMENT_PREPARATION, MeasurementUnit.SQM, MeasurementUnit.MM, volumeApplicable = false, specificFields = listOf("Existing Distressed Area (m²)", "Rectified Area (m²)", "Cleaning Status", "Dust Removal", "Water Logging")),
        HighwayActivityTemplate("CRACK_SEALING", "Crack Sealing", HighwayActivityCategory.PAVEMENT_PREPARATION, MeasurementUnit.M, MeasurementUnit.MM, true, specificFields = listOf("Crack Length (m)", "Crack Width (mm)", "Crack Depth (mm)", "Segments", "Sealant Type", "Sealant Qty")),
        HighwayActivityTemplate("POTHOLE_REPAIR", "Pothole Repair", HighwayActivityCategory.PAVEMENT_PREPARATION, MeasurementUnit.CUM, MeasurementUnit.MM, true, specificFields = listOf("Number of Potholes", "Individual L×W×D", "Material Type", "Before/After Photos")),
        HighwayActivityTemplate("GLASS_GRID", "Glass Grid Application", HighwayActivityCategory.PAVEMENT_PREPARATION, MeasurementUnit.SQM, MeasurementUnit.MM, volumeApplicable = false, supportsGlassGrid = true, specificFields = listOf("Roll Width (m)", "Number of Rolls", "Overlap Width (m)", "Effective Width (m)", "Tack Coat Status", "PTR Passes", "Wrinkles / Alignment")),
        HighwayActivityTemplate("GEOGRID_GEOSYNTHETIC", "Geogrid / Geosynthetic Application", HighwayActivityCategory.PAVEMENT_PREPARATION, MeasurementUnit.SQM, MeasurementUnit.MM, volumeApplicable = false, supportsGlassGrid = true, specificFields = listOf("Roll Width (m)", "Overlap Width (m)", "Number of Rolls", "Effective Coverage (m²)", "Layer")),
        HighwayActivityTemplate("EXISTING_PAVEMENT_RECTIFICATION", "Existing Pavement Rectification", HighwayActivityCategory.PAVEMENT_PREPARATION, MeasurementUnit.SQM, MeasurementUnit.MM, true, specificFields = listOf("Distressed Area (m²)", "Rectified Area (m²)", "Material Type")),
        HighwayActivityTemplate("ROAD_SHOULDER_WORK", "Road Shoulder Work", HighwayActivityCategory.PAVEMENT_PREPARATION, MeasurementUnit.CUM, MeasurementUnit.MM, true, specificFields = listOf("Layer Thickness (mm)", "Crossfall (%)", "Compaction")),

        // C. Earthwork and Granular Works (19-25)
        HighwayActivityTemplate("GSB", "GSB", HighwayActivityCategory.EARTHWORK_GRANULAR, MeasurementUnit.CUM, MeasurementUnit.MM, true, specificFields = listOf("Layer Number", "Loose Thickness (mm)", "Compacted Thickness (mm)", "Moisture Content (%)", "Roller Passes")),
        HighwayActivityTemplate("WMM", "WMM", HighwayActivityCategory.EARTHWORK_GRANULAR, MeasurementUnit.CUM, MeasurementUnit.MM, true, specificFields = listOf("Loose Thickness (mm)", "Compacted Thickness (mm)", "Moisture Content (%)", "Compaction (%)", "Roller Passes")),
        HighwayActivityTemplate("EARTHWORK_EMBANKMENT", "Earthwork / Embankment", HighwayActivityCategory.EARTHWORK_GRANULAR, MeasurementUnit.CUM, MeasurementUnit.M, true, supportsCrossSection = true, specificFields = listOf("Top Width (m)", "Bottom Width (m)", "Left/Right Side Slopes", "EGL / Formation Level", "Fill Height / Cut Depth", "Borrow Area")),
        HighwayActivityTemplate("SUBGRADE_PREPARATION", "Subgrade Preparation", HighwayActivityCategory.EARTHWORK_GRANULAR, MeasurementUnit.CUM, MeasurementUnit.M, true, supportsCrossSection = true, specificFields = listOf("Formation Width (m)", "Existing Level", "Proposed Level", "Cut/Fill Depth", "FDT Reference")),
        HighwayActivityTemplate("SOIL_STABILIZATION", "Soil Stabilization", HighwayActivityCategory.EARTHWORK_GRANULAR, MeasurementUnit.CUM, MeasurementUnit.MM, true, specificFields = listOf("Treatment Depth (mm)", "Soil Type", "Stabilizer Type", "Application Rate", "Moisture Content (%)")),
        HighwayActivityTemplate("GRANULAR_SHOULDER", "Granular Shoulder Construction", HighwayActivityCategory.EARTHWORK_GRANULAR, MeasurementUnit.CUM, MeasurementUnit.MM, true, specificFields = listOf("Layer Thickness (mm)", "Crossfall (%)", "Compaction")),
        HighwayActivityTemplate("DRAINAGE_WORK", "Drainage Work", HighwayActivityCategory.EARTHWORK_GRANULAR, MeasurementUnit.CUM, MeasurementUnit.M, true, supportsCrossSection = true, specificFields = listOf("Drain Type", "Internal W×D", "Excavation W×D", "Wall/Concrete Thickness", "Bottom Width", "Side Slope", "Cover Slab")),

        // D. Concrete and Structures (26-34)
        HighwayActivityTemplate("PQC", "PQC", HighwayActivityCategory.CONCRETE_STRUCTURES, MeasurementUnit.CUM, MeasurementUnit.MM, true, specificFields = listOf("Concrete Grade", "Batching Plant", "Transit Mixer No.", "Slump (mm)", "Cube Test Ref", "Joint Details", "Curing")),
        HighwayActivityTemplate("DLC", "DLC", HighwayActivityCategory.CONCRETE_STRUCTURES, MeasurementUnit.CUM, MeasurementUnit.MM, true, specificFields = listOf("Concrete Grade", "Batching Plant", "Transit Mixer No.", "Moisture/Compaction", "Cube Test Ref", "Curing")),
        HighwayActivityTemplate("CONCRETE_PAVEMENT", "Concrete Pavement", HighwayActivityCategory.CONCRETE_STRUCTURES, MeasurementUnit.CUM, MeasurementUnit.MM, true, specificFields = listOf("Concrete Grade", "Transit Mixer No.", "Slump", "Cube Test Ref", "Joints", "Curing")),
        HighwayActivityTemplate("KERB_LAYING", "Kerb Laying", HighwayActivityCategory.CONCRETE_STRUCTURES, MeasurementUnit.M, MeasurementUnit.MM, true, specificFields = listOf("Kerb Type", "Kerb Height/Width", "Foundation W×D", "Units", "Bedding Qty", "Concrete Qty", "Alignment")),
        HighwayActivityTemplate("RCC_WORK", "RCC Work", HighwayActivityCategory.CONCRETE_STRUCTURES, MeasurementUnit.CUM, MeasurementUnit.M, true, supportsStructuralSubtypes = listOf("Beam", "Column", "Slab", "Wall", "Foundation", "Other Structural"), specificFields = listOf("Structural Element", "Units", "Concrete Grade", "Reinforcement (kg/MT)", "Formwork Area (m²)", "Cube Test Ref")),
        HighwayActivityTemplate("CULVERT_CONSTRUCTION", "Culvert Construction", HighwayActivityCategory.CONCRETE_STRUCTURES, MeasurementUnit.CUM, MeasurementUnit.M, true, specificFields = listOf("Culvert Type", "Cells", "Internal L×W×H", "Wall/Slab Thickness", "Excavation Volume", "Reinforcement", "Formwork", "Backfill")),
        HighwayActivityTemplate("BRIDGE_WORK", "Minor Bridge / Major Bridge", HighwayActivityCategory.CONCRETE_STRUCTURES, MeasurementUnit.CUM, MeasurementUnit.M, true, supportsCrossSection = true, supportsStructuralSubtypes = listOf("Foundation", "Pile", "Pile Cap", "Pier", "Pier Cap", "Abutment", "Wing Wall", "Bearing", "Girder", "Deck Slab", "Approach Slab", "Protection Work"), specificFields = listOf("Bridge Component", "Cross-Section Area", "Concrete Volume (m³)", "Reinforcement (MT)", "Formwork Area (m²)")),
        HighwayActivityTemplate("RETAINING_WALL", "Retaining Wall", HighwayActivityCategory.CONCRETE_STRUCTURES, MeasurementUnit.CUM, MeasurementUnit.M, true, supportsCrossSection = true, specificFields = listOf("Base Width", "Top Width", "Wall Height", "Foundation Depth", "Excavation", "Reinforcement", "Backfill Volume")),
        HighwayActivityTemplate("PROTECTION_WORK", "Protection Work", HighwayActivityCategory.CONCRETE_STRUCTURES, MeasurementUnit.CUM, MeasurementUnit.M, true, specificFields = listOf("Protection Type", "Base/Top Width", "Height/Depth", "Stone/Concrete Volume")),

        // E. Road Safety and Finishing (35-41)
        HighwayActivityTemplate("ROAD_MARKING", "Road Marking", HighwayActivityCategory.ROAD_SAFETY_FINISHING, MeasurementUnit.SQM, MeasurementUnit.MM, volumeApplicable = false, supportsSprayRate = true, specificFields = listOf("Line Width (m)", "Thickness (mm)", "Number of Lines", "Application Rate", "Glass Bead Application")),
        HighwayActivityTemplate("THERMOPLASTIC_MARKING", "Thermoplastic Marking", HighwayActivityCategory.ROAD_SAFETY_FINISHING, MeasurementUnit.SQM, MeasurementUnit.MM, volumeApplicable = false, supportsSprayRate = true, specificFields = listOf("Line Width (m)", "Thickness (mm)", "Number of Lines", "Material Qty (kg)", "Glass Beads (kg)")),
        HighwayActivityTemplate("ROAD_STUD_INSTALLATION", "Road Stud Installation", HighwayActivityCategory.ROAD_SAFETY_FINISHING, MeasurementUnit.M, MeasurementUnit.MM, volumeApplicable = false, specificFields = listOf("Spacing (m)", "Number of Studs", "Stud Type", "Adhesive Qty (kg)")),
        HighwayActivityTemplate("CRASH_BARRIER", "Crash Barrier Installation", HighwayActivityCategory.ROAD_SAFETY_FINISHING, MeasurementUnit.M, MeasurementUnit.M, true, specificFields = listOf("Barrier Type", "Rail Length (m)", "Post Spacing (m)", "Posts", "Foundation Depth (m)", "Embedment (m)", "Concrete Qty (m³)")),
        HighwayActivityTemplate("SIGNBOARD_INSTALLATION", "Signboard Installation", HighwayActivityCategory.ROAD_SAFETY_FINISHING, MeasurementUnit.SQM, MeasurementUnit.M, true, specificFields = listOf("Board L×W", "Board Thickness", "Post Height (m)", "Foundation L×W×D", "Foundation Concrete (m³)")),
        HighwayActivityTemplate("ROAD_FURNITURE", "Road Furniture", HighwayActivityCategory.ROAD_SAFETY_FINISHING, MeasurementUnit.M, MeasurementUnit.M, volumeApplicable = false, specificFields = listOf("Item Type", "Units / Spacing", "Foundation Dimensions")),
        HighwayActivityTemplate("FINAL_FINISHING", "Final Finishing and Maintenance", HighwayActivityCategory.ROAD_SAFETY_FINISHING, MeasurementUnit.SQM, MeasurementUnit.MM, volumeApplicable = false, specificFields = listOf("Work Scope", "Area Covered (m²)", "Inspection Status"))
    )

    fun findByCode(code: String, custom: List<HighwayActivityTemplate> = emptyList()): HighwayActivityTemplate =
        (standardActivities + custom).firstOrNull { it.code.equals(code, true) } ?: standardActivities.first()
}

// ============================================================================
// 4. REGISTER LIFECYCLE, MEASUREMENTS, TESTS, OBSERVATIONS, SIGNATURES, AUDIT
// ============================================================================

enum class RegisterStatus(val dbValue: String, val displayLabel: String) {
    DRAFT("DRAFT", "Draft"),
    SUBMITTED("SUBMITTED", "Submitted"),
    PENDING_PMC_APPROVAL("PENDING_PMC_APPROVAL", "Pending PMC Approval"),
    APPROVED_LOCKED("APPROVED_LOCKED", "Approved – Locked"),
    EDIT_PERMISSION_REQUESTED("EDIT_PERMISSION_REQUESTED", "Edit Permission Requested"),
    EDIT_PERMISSION_GRANTED("EDIT_PERMISSION_GRANTED", "Edit Permission Granted"),
    EDIT_PERMISSION_EXPIRED("EDIT_PERMISSION_EXPIRED", "Edit Permission Expired"),
    REJECTED("REJECTED", "Rejected"),
    REOPENED("REOPENED", "Reopened"),
    RESUBMITTED("RESUBMITTED", "Resubmitted");

    val isLockedToNormalEdits: Boolean
        get() = this == SUBMITTED ||
            this == PENDING_PMC_APPROVAL ||
            this == APPROVED_LOCKED ||
            this == EDIT_PERMISSION_REQUESTED ||
            this == EDIT_PERMISSION_EXPIRED ||
            this == RESUBMITTED

    companion object {
        fun fromDb(v: String?) = entries.firstOrNull { it.dbValue.equals(v, true) } ?: DRAFT
    }
}

enum class SyncState(val label: String) {
    SYNCED("Synced"),
    PENDING_SYNC("Pending Sync"),
    CONFLICT("Sync Conflict"),
    FAILED("Sync Error")
}

data class SiteRegisterRecord(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val projectName: String = "",
    val contractorName: String = "",
    val highwayName: String = "",
    val packageLocation: String = "",
    val activityCode: String,
    val activityName: String,
    val activityCategory: HighwayActivityCategory,
    val templateVersion: Int = 1,
    val registerNumber: String,
    val workDate: String, // YYYY-MM-DD
    val shift: String = "DAY", // DAY / NIGHT
    val side: String = "RHS",
    val lane: String = "MCW",
    val chainageFrom: String = "",
    val chainageTo: String = "",
    val currentRevision: Int = 1,
    val status: RegisterStatus = RegisterStatus.DRAFT,
    val totalLengthM: Double = 0.0,
    val totalAreaSqm: Double = 0.0,
    val totalVolumeCum: Double = 0.0,
    val totalMaterialQty: Double? = null,
    val totalMaterialUnit: MeasurementUnit = MeasurementUnit.MT,
    val quantityClassification: QuantityClassification = QuantityClassification.MEASURED,
    val remarks: String = "",
    val createdBy: String,
    val createdByName: String = "",
    val submittedAtMs: Long? = null,
    val approvedAtMs: Long? = null,
    val approvedBy: String? = null,
    val approvedByName: String? = null,
    val syncState: SyncState = SyncState.PENDING_SYNC,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
)

data class SiteRegisterRevision(
    val id: String = UUID.randomUUID().toString(),
    val registerId: String,
    val projectId: String,
    val revisionNumber: Int,
    val status: RegisterStatus,
    val isLocked: Boolean,
    val changeSummary: String = "",
    val snapshotJson: String = "{}",
    val createdBy: String,
    val approvedBy: String? = null,
    val approvedAtMs: Long? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class SiteSegmentRecord(
    val id: String = UUID.randomUUID().toString(),
    val registerId: String,
    val revisionNumber: Int = 1,
    val segmentIndex: Int = 1,
    val fromChainage: String,
    val toChainage: String,
    val side: String = "RHS",
    val lane: String = "MCW",
    val startWidthM: Double? = null,
    val middleWidthM: Double? = null,
    val endWidthM: Double? = null,
    val actualWidthM: Double? = null,
    val designThickness: Double? = null,
    val looseThickness: Double? = null,
    val actualCompactedThickness: Double? = null,
    val verifiedThickness: Double? = null,
    val thicknessUnit: MeasurementUnit = MeasurementUnit.MM,
    val verifiedDensity: Double? = null, // MT/m³ or kg/m³ — never invented
    val densityUnit: String? = null,
    val measurementMethod: MeasurementMethod = MeasurementMethod.UNIFORM_SECTION,
    val crossSectionArea1Sqm: Double? = null,
    val crossSectionArea2Sqm: Double? = null,
    val surveyedAreaSqm: Double? = null,
    val structuralElementType: String = "",
    val unitCount: Int = 1,
    val remarks: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

data class BcLayingEntry(
    val id: String = UUID.randomUUID().toString(),
    val registerId: String,
    val revisionNumber: Int = 1,
    val serialNumber: Int,
    val vehicleNumber: String,
    val bcType: String = "BC-II",
    val layer: String = "Wearing Course",
    val fromChainage: String,
    val toChainage: String,
    val side: String = "RHS",
    val lane: String = "MCW",
    val startTime: String = "",
    val endTime: String = "",
    val layingTempC: Double? = null,
    val rollingTempC: Double? = null,
    val startWidthM: Double? = null,
    val middleWidthM: Double? = null,
    val endWidthM: Double? = null,
    val actualLaidLengthM: Double? = null,
    val designThicknessMm: Double? = null,
    val looseThicknessMm: Double? = null,
    val compactedThicknessMm: Double? = null,
    val measuredMaterialMt: Double? = null,
    val verifiedDensityMtPerCum: Double? = null,
    val remarks: String = "",
    val recordedAtMs: Long = System.currentTimeMillis()
)

data class GlassGridEntry(
    val id: String = UUID.randomUUID().toString(),
    val registerId: String,
    val revisionNumber: Int = 1,
    val fromChainage: String,
    val toChainage: String,
    val applicationLengthM: Double,
    val applicationWidthM: Double,
    val rollWidthM: Double,
    val numberOfRolls: Int,
    val overlapWidthM: Double,
    val applicationSide: String = "RHS",
    val startTime: String = "",
    val endTime: String = "",
    val surfacePreparation: String = "",
    val tackCoatStatus: String = "",
    val ptrPasses: Int? = null,
    val wrinklesAlignment: String = "",
    val remarks: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

enum class TestResultStatus(val dbValue: String, val label: String) {
    PASS("PASS", "Pass"),
    FAIL("FAIL", "Fail"),
    NEEDS_REVIEW("NEEDS_REVIEW", "Needs Review");

    companion object {
        fun fromDb(v: String?) = entries.firstOrNull { it.dbValue.equals(v, true) } ?: NEEDS_REVIEW
    }
}

data class TackCoatTrayRow(
    val id: String = UUID.randomUUID().toString(),
    val trayNumber: Int,
    val trayAreaSqm: Double,
    val weightBeforeKg: Double,
    val weightAfterKg: Double,
    val remarks: String = ""
)

data class TackCoatTestRecord(
    val id: String = UUID.randomUUID().toString(),
    val registerId: String? = null,
    val projectId: String,
    val projectName: String = "",
    val contractorName: String = "",
    val independentEngineer: String = "",
    val pmcConsultant: String = "",
    val projectManager: String = "",
    val location: String = "",
    val chainage: String = "",
    val side: String = "RHS",
    val materialType: String = "",
    val purpose: String = "",
    val testDate: String,
    val weather: String = "",
    val testedBy: String = "",
    val temperatureC: Double? = null,
    val specMinRateKgSqm: Double? = null, // Never hardcoded; supplied from project spec
    val specMaxRateKgSqm: Double? = null,
    val specReference: String = "",
    val trays: List<TackCoatTrayRow> = emptyList(),
    val photoUris: List<String> = emptyList(),
    val remarks: String = "",
    val createdBy: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

enum class QualityTestCategory(val label: String) {
    AGGREGATE("Aggregate Tests"),
    BITUMEN("Bitumen Tests"),
    TEMPERATURE("Temperature Records"),
    DENSITY("Density Tests"),
    FIELD_DENSITY("Field Density Tests (FDT)"),
    CORE("Core Tests"),
    CONCRETE("Concrete Tests"),
    CUBE("Cube Tests"),
    GRADATION("Gradation Tests"),
    MATERIAL_INSPECTION("Material Inspection"),
    CUSTOM("Custom Test Report")
}

data class QualityTestReportRecord(
    val id: String = UUID.randomUUID().toString(),
    val registerId: String? = null,
    val projectId: String,
    val category: QualityTestCategory,
    val testTitle: String,
    val testDate: String,
    val chainage: String = "",
    val side: String = "RHS",
    val sampleLocation: String = "",
    val testedBy: String = "",
    val measuredValues: Map<String, String> = emptyMap(),
    val specCriteria: String = "",
    val resultStatus: TestResultStatus = TestResultStatus.NEEDS_REVIEW,
    val photoUris: List<String> = emptyList(),
    val ocrExtractedText: String = "",
    val ocrConfidence: Float? = null,
    val ocrVerifiedManually: Boolean = false,
    val remarks: String = "",
    val createdBy: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

enum class ObservationCategory(val label: String) {
    SEGREGATION("Segregation"),
    UNDULATIONS("Undulations"),
    IMPROPER_COMPACTION("Improper Compaction"),
    TEMPERATURE_DEFICIENCY("Temperature Deficiency"),
    IMPROPER_TACK_COAT("Improper Tack Coat"),
    SURFACE_CONTAMINATION("Surface Contamination"),
    MILLING_DUST("Milling Dust"),
    WATER_LOGGING("Water Logging"),
    INCORRECT_CHAINAGE("Incorrect Chainage"),
    GLASS_GRID_WRINKLES("Glass Grid Wrinkles"),
    IMPROPER_ALIGNMENT("Improper Alignment"),
    INADEQUATE_ROLLING("Inadequate Rolling"),
    EQUIPMENT_DELAY("Equipment Delay"),
    MATERIAL_SUPPLY_DELAY("Material Supply Delay"),
    SAFETY_ISSUES("Safety Issues"),
    OTHER("Other")
}

enum class ObservationSeverity(val label: String) { LOW("Low"), MEDIUM("Medium"), HIGH("High"), CRITICAL("Critical") }

enum class RectificationStatus(val dbValue: String, val label: String) {
    OPEN("OPEN", "Open"),
    UNDER_RECTIFICATION("UNDER_RECTIFICATION", "Under Rectification"),
    RECTIFIED("RECTIFIED", "Rectified"),
    VERIFIED("VERIFIED", "Verified"),
    CLOSED("CLOSED", "Closed");

    companion object {
        fun fromDb(v: String?) = entries.firstOrNull { it.dbValue.equals(v, true) } ?: OPEN
    }
}

data class SiteObservationRecord(
    val id: String = UUID.randomUUID().toString(),
    val registerId: String? = null,
    val projectId: String,
    val serialNumber: Int = 1,
    val observedAtMs: Long = System.currentTimeMillis(),
    val activityName: String = "",
    val chainageFrom: String = "",
    val chainageTo: String = "",
    val side: String = "RHS",
    val lane: String = "MCW",
    val affectedLengthM: Double? = null,
    val affectedWidthM: Double? = null,
    val affectedDepthM: Double? = null,
    val affectedAreaSqm: Double? = null,
    val affectedVolumeCum: Double? = null,
    val category: ObservationCategory = ObservationCategory.OTHER,
    val description: String,
    val severity: ObservationSeverity = ObservationSeverity.MEDIUM,
    val beforePhotoUris: List<String> = emptyList(),
    val afterPhotoUris: List<String> = emptyList(),
    val correctiveAction: String = "",
    val responsibleContractor: String = "",
    val targetDate: String = "",
    val status: RectificationStatus = RectificationStatus.OPEN,
    val verifiedByName: String = "",
    val verificationDateMs: Long? = null,
    val finalRemarks: String = "",
    val createdBy: String = ""
)

enum class SignatureStatus(val dbValue: String, val label: String) {
    PENDING("PENDING", "Pending"),
    SIGNED("SIGNED", "Signed"),
    REJECTED("REJECTED", "Rejected"),
    VERIFICATION_REQUIRED("VERIFICATION_REQUIRED", "Verification Required");

    companion object {
        fun fromDb(v: String?) = entries.firstOrNull { it.dbValue.equals(v, true) } ?: PENDING
    }
}

data class SiteSignatureRecord(
    val id: String = UUID.randomUUID().toString(),
    val registerId: String,
    val projectId: String,
    val revisionNumber: Int,
    val signerUserId: String,
    val signerName: String,
    val signerDesignation: String,
    val signerOrganization: String,
    val signerRole: ProjectRole,
    val signaturePointsSerialized: String,
    val status: SignatureStatus = SignatureStatus.SIGNED,
    val signedAtMs: Long = System.currentTimeMillis(),
    val remarks: String = ""
)

data class RegisterSharingCode(
    val id: String = UUID.randomUUID().toString(),
    val registerId: String,
    val projectId: String,
    val code: String,
    val expiresAtMs: Long? = null,
    val isRevoked: Boolean = false,
    val authorizedUserNames: List<String> = emptyList(),
    val createdBy: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
) {
    fun isUsable(nowMs: Long = System.currentTimeMillis()): Boolean =
        !isRevoked && (expiresAtMs == null || nowMs < expiresAtMs)
}

enum class EditRequestStatus(val dbValue: String, val label: String) {
    PENDING("PENDING", "Pending PMC"),
    GRANTED("GRANTED", "Granted"),
    REJECTED("REJECTED", "Rejected"),
    EXPIRED("EXPIRED", "Expired"),
    REVOKED("REVOKED", "Revoked"),
    CONSUMED("CONSUMED", "Completed");

    companion object {
        fun fromDb(v: String?) = entries.firstOrNull { it.dbValue.equals(v, true) } ?: PENDING
    }
}

data class EditPermissionRequest(
    val id: String = UUID.randomUUID().toString(),
    val registerId: String,
    val projectId: String,
    val revisionNumber: Int,
    val requestedBy: String,
    val requestedByName: String,
    val reason: String,
    val proposedCorrection: String,
    val supportingEvidence: String = "",
    val status: EditRequestStatus = EditRequestStatus.PENDING,
    val reviewedBy: String? = null,
    val pmcRemarks: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

data class EditPermissionGrant(
    val id: String = UUID.randomUUID().toString(),
    val editRequestId: String,
    val registerId: String,
    val projectId: String,
    val targetRevisionNumber: Int,
    val authorizedUserId: String,
    val grantedByPmcId: String,
    val grantedAtMs: Long = System.currentTimeMillis(),
    val expiresAtMs: Long,
    val isActive: Boolean = true,
    val revokedAtMs: Long? = null,
    val consumedAtMs: Long? = null
) {
    fun isCurrentlyValidFor(
        userId: String,
        revisionNumber: Int,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (!isActive || revokedAtMs != null || consumedAtMs != null) return false
        if (authorizedUserId != userId) return false
        if (grantedByPmcId == userId) return false // Prevent creator self-authorization
        if (targetRevisionNumber != revisionNumber) return false
        return nowMs < expiresAtMs
    }
}

data class SiteAuditEvent(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val registerId: String? = null,
    val revisionNumber: Int? = null,
    val userId: String,
    val userName: String = "",
    val organizationType: String = "",
    val assignedRole: String = "",
    val action: String,
    val beforeSummary: String = "",
    val afterSummary: String = "",
    val timestampMs: Long = System.currentTimeMillis()
)

data class SiteRegisterSettings(
    val defaultProjectId: String = "",
    val defaultContractor: String = "",
    val defaultShift: String = "DAY",
    val defaultLane: String = "MCW",
    val frequentActivityCodes: List<String> = listOf("BC_LAYING", "DBM_LAYING", "TACK_COAT", "GLASS_GRID", "GSB", "WMM", "PQC"),
    val defaultLengthUnit: MeasurementUnit = MeasurementUnit.M,
    val defaultDepthUnit: MeasurementUnit = MeasurementUnit.MM,
    val decimalPrecision: Int = 3,
    val projectDensityByActivity: Map<String, Double> = emptyMap(), // Empty by default — never invent universal densities
    val specRefByActivity: Map<String, String> = emptyMap(),
    val sharingCodeExpiryHours: Int = 24,
    val includePhotosInPdf: Boolean = true,
    val customActivities: List<HighwayActivityTemplate> = emptyList(),
    val customTestTemplates: List<String> = emptyList()
)
