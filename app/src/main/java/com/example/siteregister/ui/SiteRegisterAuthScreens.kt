package com.example.siteregister.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.siteregister.model.DesignationCatalog
import com.example.siteregister.model.LoginAccessRoute
import com.example.siteregister.model.MembershipApprovalStatus
import com.example.siteregister.model.OrganizationType
import com.example.siteregister.model.ProjectInfo
import com.example.siteregister.model.ProjectMembership
import com.example.ui.theme.CtecsTheme

@Composable
fun SiteRegisterLoginScreen(
    state: SiteRegisterUiState,
    onLogin: (String, String) -> Unit,
    onForgotPassword: (String) -> Unit,
    onOpenRegister: () -> Unit,
    onOpenPmcBootstrap: () -> Unit,
    onBack: () -> Unit,
    onDismissBanner: () -> Unit
) {
    val c = CtecsTheme.colors
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
    ) {
        SrTopBar(
            title = "CTECS Site Register",
            subtitle = "Highway Quality & Progress Management",
            onBack = onBack
        )
        SrBannerStrip(state.bannerMessage, state.bannerIsError, onDismissBanner)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SrSurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = c.accent, modifier = Modifier.size(22.dp))
                    Text("Sign In to Project Workspace", color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = if (state.isSupabaseConfigured) {
                        "Connected to CTECS Supabase Serverless Backend."
                    } else {
                        "Offline-First Field Mode active. Add SUPABASE_PUBLISHABLE_KEY in AI Studio Secrets for cloud sync."
                    },
                    color = c.textSecondary,
                    fontSize = 12.sp
                )

                SrTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email Address",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    testTagName = "sr_login_email"
                )
                SrTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle password",
                                tint = c.textSecondary
                            )
                        }
                    },
                    testTagName = "sr_login_password"
                )

                SrPrimaryButton(
                    text = if (state.isLoading) "Signing In..." else "Login",
                    onClick = { onLogin(email, password) },
                    enabled = !state.isLoading,
                    icon = Icons.Default.Login,
                    modifier = Modifier.fillMaxWidth(),
                    testTagName = "sr_login_submit"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Forgot Password?",
                        color = c.accent,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { onForgotPassword(email) }
                            .padding(vertical = 8.dp)
                    )
                    Text(
                        text = "Create Account (Project Code)",
                        color = c.accentSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(onClick = onOpenRegister)
                            .padding(vertical = 8.dp)
                            .testTag("sr_open_register")
                    )
                }
            }

            SrSurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = c.warning, modifier = Modifier.size(20.dp))
                    Text("PMC Project Administrator Setup", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "Setting up a new highway project? Initialize your PMC Project workspace and generate an official Dynamic Project Code for team registration.",
                    color = c.textSecondary,
                    fontSize = 12.sp
                )
                SrTonalButton(
                    text = "Initialize New Highway Project (PMC)",
                    onClick = onOpenPmcBootstrap,
                    accent = c.warning,
                    icon = Icons.Default.Key,
                    modifier = Modifier.fillMaxWidth(),
                    testTagName = "sr_open_pmc_bootstrap"
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SiteRegisterRegistrationScreen(
    state: SiteRegisterUiState,
    isJoinAdditionalMode: Boolean = false,
    onVerifyCode: (String) -> Unit,
    onSubmitRegistration: (
        fullName: String,
        username: String,
        email: String,
        mobile: String,
        password: String,
        confirmPassword: String,
        orgType: OrganizationType,
        orgName: String,
        isUnlistedOrg: Boolean,
        designation: String
    ) -> Unit,
    onSubmitJoinAdditional: (
        orgType: OrganizationType,
        orgName: String,
        isUnlistedOrg: Boolean,
        designation: String
    ) -> Unit,
    onBack: () -> Unit,
    onDismissBanner: () -> Unit
) {
    val c = CtecsTheme.colors
    var projectCode by remember { mutableStateOf("") }
    var selectedOrgType by remember { mutableStateOf(OrganizationType.CONTRACTOR) }
    var orgName by remember { mutableStateOf("") }
    var isUnlistedOrg by remember { mutableStateOf(false) }
    var designationSearch by remember { mutableStateOf("") }
    var selectedDesignation by remember { mutableStateOf("") }
    var customDesignation by remember { mutableStateOf("") }

    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    val verifiedProject = state.verifiedCodeResult?.takeIf { it.isValid }?.project
    val permittedTypes = verifiedProject?.permittedOrganizationTypes ?: OrganizationType.entries

    // Auto-suggest official project organization name for chosen organization type
    val suggestedOrgName = remember(verifiedProject, selectedOrgType) {
        when (selectedOrgType) {
            OrganizationType.CONTRACTOR -> verifiedProject?.contractorName.orEmpty()
            OrganizationType.PMC -> verifiedProject?.pmcOrganization.orEmpty()
            OrganizationType.IE -> verifiedProject?.ieOrganization.orEmpty()
            OrganizationType.CONCESSIONAIRE -> verifiedProject?.concessionaireName.orEmpty()
            OrganizationType.CLIENT -> verifiedProject?.clientName.orEmpty()
        }
    }

    val designations = remember(selectedOrgType, designationSearch, verifiedProject) {
        val base = DesignationCatalog.forOrganizationType(
            selectedOrgType,
            verifiedProject?.customDesignationsByType?.get(selectedOrgType).orEmpty()
        )
        if (designationSearch.isBlank()) base else base.filter { it.contains(designationSearch, true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
    ) {
        SrTopBar(
            title = if (isJoinAdditionalMode) "Join Additional Project" else "Project-Code Registration",
            subtitle = "Mandatory PMC Verification & Approval",
            onBack = onBack
        )
        SrBannerStrip(state.bannerMessage, state.bannerIsError, onDismissBanner)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Step 1: Enter & Verify PMC Project Code
            SrSurfaceCard {
                SrSectionHeader("STEP 1 • PMC PROJECT CODE")
                SrTextField(
                    value = projectCode,
                    onValueChange = { projectCode = it.uppercase() },
                    label = "Enter PMC Project Code (e.g. CTECS-NH58-7K92P6)",
                    testTagName = "sr_reg_project_code"
                )
                SrPrimaryButton(
                    text = if (state.isVerifyingCode) "Verifying Code..." else "Verify Project Code",
                    onClick = { onVerifyCode(projectCode) },
                    enabled = projectCode.isNotBlank() && !state.isVerifyingCode,
                    icon = Icons.Default.Key,
                    modifier = Modifier.fillMaxWidth(),
                    testTagName = "sr_verify_code_btn"
                )
            }

            // Verified Read-Only Official Project Details Banner
            if (verifiedProject != null) {
                VerifiedProjectReadOnlyBanner(verifiedProject)

                // Step 2: Organization Type & Organization Name
                SrSurfaceCard {
                    SrSectionHeader("STEP 2 • ORGANIZATION & DESIGNATION", "Role assigned by PMC")
                    Text(
                        text = "Selecting an organization (including PMC) does NOT grant approval privileges. Your Project Role is assigned exclusively by PMC during registration review.",
                        color = c.warning,
                        fontSize = 11.5.sp
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        permittedTypes.forEach { type ->
                            SrPillChip(
                                label = type.displayLabel,
                                selected = selectedOrgType == type,
                                onClick = {
                                    selectedOrgType = type
                                    if (!isUnlistedOrg) {
                                        orgName = when (type) {
                                            OrganizationType.CONTRACTOR -> verifiedProject.contractorName
                                            OrganizationType.PMC -> verifiedProject.pmcOrganization
                                            OrganizationType.IE -> verifiedProject.ieOrganization
                                            OrganizationType.CONCESSIONAIRE -> verifiedProject.concessionaireName
                                            OrganizationType.CLIENT -> verifiedProject.clientName
                                        }
                                    }
                                    selectedDesignation = ""
                                }
                            )
                        }
                    }

                    if (!isUnlistedOrg && suggestedOrgName.isNotBlank()) {
                        SrTextField(
                            value = suggestedOrgName,
                            onValueChange = {},
                            label = "Verified Project ${selectedOrgType.displayLabel} Organization",
                            readOnly = true
                        )
                    } else {
                        SrTextField(
                            value = orgName,
                            onValueChange = { orgName = it },
                            label = "Organization Name (${if (isUnlistedOrg) "Unlisted – PMC Verification Required" else selectedOrgType.displayLabel})"
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isUnlistedOrg,
                            onCheckedChange = { isUnlistedOrg = it }
                        )
                        Text(
                            text = "Request Unlisted Organization Addition (requires PMC review)",
                            color = c.textSecondary,
                            fontSize = 12.sp
                        )
                    }

                    SrTextField(
                        value = designationSearch,
                        onValueChange = { designationSearch = it },
                        label = "Search ${selectedOrgType.displayLabel} Designations"
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        designations.forEach { d ->
                            SrPillChip(
                                label = d,
                                selected = selectedDesignation == d,
                                onClick = {
                                    selectedDesignation = d
                                    customDesignation = ""
                                }
                            )
                        }
                    }
                    SrTextField(
                        value = customDesignation,
                        onValueChange = {
                            customDesignation = it
                            if (it.isNotBlank()) selectedDesignation = it
                        },
                        label = "Or Enter Custom Designation (Subject to PMC Approval)"
                    )
                }

                // Step 3: Individual User Account Details (when creating new account)
                if (!isJoinAdditionalMode) {
                    SrSurfaceCard {
                        SrSectionHeader("STEP 3 • INDIVIDUAL ENGINEER ACCOUNT")
                        SrTextField(value = fullName, onValueChange = { fullName = it }, label = "Full Name *", testTagName = "sr_reg_fullname")
                        SrTextField(value = username, onValueChange = { username = it }, label = "Username *", testTagName = "sr_reg_username")
                        SrTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = "Email Address *",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            testTagName = "sr_reg_email"
                        )
                        SrTextField(
                            value = mobile,
                            onValueChange = { mobile = it },
                            label = "Mobile Number",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                        SrTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = "Password (min 8 characters) *",
                            visualTransformation = PasswordVisualTransformation(),
                            testTagName = "sr_reg_password"
                        )
                        SrTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = "Confirm Password *",
                            visualTransformation = PasswordVisualTransformation()
                        )
                    }
                }

                val effectiveOrgName = if (!isUnlistedOrg && suggestedOrgName.isNotBlank()) suggestedOrgName else orgName
                val effectiveDesignation = customDesignation.ifBlank { selectedDesignation }

                SrPrimaryButton(
                    text = if (isJoinAdditionalMode) "Submit Project Join Request for PMC Approval" else "Submit Registration for PMC Approval",
                    onClick = {
                        if (isJoinAdditionalMode) {
                            onSubmitJoinAdditional(selectedOrgType, effectiveOrgName, isUnlistedOrg, effectiveDesignation)
                        } else {
                            onSubmitRegistration(
                                fullName, username, email, mobile, password, confirmPassword,
                                selectedOrgType, effectiveOrgName, isUnlistedOrg, effectiveDesignation
                            )
                        }
                    },
                    icon = Icons.Default.PersonAdd,
                    modifier = Modifier.fillMaxWidth(),
                    testTagName = "sr_reg_submit"
                )
            }
        }
    }
}

@Composable
fun VerifiedProjectReadOnlyBanner(project: ProjectInfo) {
    val c = CtecsTheme.colors
    SrSurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = c.success, modifier = Modifier.size(20.dp))
            Text("Project Verified (Read-Only Official Metadata)", color = c.success, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
        }
        val rows = listOf(
            "Project" to project.projectName,
            "Highway / NH-SH" to listOf(project.highwayName, project.nhShNumber).filter { it.isNotBlank() }.joinToString(" • "),
            "Package / Location" to listOf(project.packageNumber, project.projectLocation, project.stateName).filter { it.isNotBlank() }.joinToString(" • "),
            "Contractor" to project.contractorName,
            "Concessionaire" to project.concessionaireName,
            "Client" to project.clientName,
            "PMC Organization" to project.pmcOrganization,
            "IE Organization" to project.ieOrganization,
            "Assigned PMC" to listOf(project.assignedPmcName, project.assignedPmcContact).filter { it.isNotBlank() }.joinToString(" • "),
            "Project ID / Status" to "${project.projectIdentification.ifBlank { project.id.take(8) }} • ${project.projectStatus} (${project.registrationStatus})"
        )
        rows.filter { it.second.isNotBlank() }.forEach { (k, v) ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(k, color = c.textMuted, fontSize = 11.5.sp)
                Text(v, color = c.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun SiteRegisterPmcBootstrapScreen(
    state: SiteRegisterUiState,
    onCreateProject: (
        projectName: String,
        highwayName: String,
        nhShNumber: String,
        packageNumber: String,
        location: String,
        stateName: String,
        contractor: String,
        concessionaire: String,
        client: String,
        pmcOrg: String,
        ieOrg: String,
        pmcName: String,
        pmcContact: String,
        projectIdCode: String,
        adminName: String,
        adminEmail: String
    ) -> Unit,
    onBack: () -> Unit,
    onDismissBanner: () -> Unit
) {
    val c = CtecsTheme.colors
    var projectName by remember { mutableStateOf("") }
    var highwayName by remember { mutableStateOf("") }
    var nhSh by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var stateName by remember { mutableStateOf("") }
    var contractor by remember { mutableStateOf("") }
    var concessionaire by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("") }
    var pmcOrg by remember { mutableStateOf("") }
    var ieOrg by remember { mutableStateOf("") }
    var pmcName by remember { mutableStateOf(state.profile?.fullName.orEmpty()) }
    var pmcContact by remember { mutableStateOf("") }
    var projectIdCode by remember { mutableStateOf("") }
    var adminEmail by remember { mutableStateOf(state.session?.email.orEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
    ) {
        SrTopBar(
            title = "Create Highway Project (PMC)",
            subtitle = "Generates Official Dynamic Project Code",
            onBack = onBack
        )
        SrBannerStrip(state.bannerMessage, state.bannerIsError, onDismissBanner)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SrSurfaceCard {
                SrSectionHeader("PROJECT IDENTIFICATION")
                SrTextField(projectName, { projectName = it }, "Project Name *", testTagName = "sr_pmc_project_name")
                SrTextField(highwayName, { highwayName = it }, "Highway Name")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SrTextField(nhSh, { nhSh = it }, "NH / SH No.", modifier = Modifier.weight(1f))
                    SrTextField(pkg, { pkg = it }, "Package No.", modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SrTextField(location, { location = it }, "Location", modifier = Modifier.weight(1f))
                    SrTextField(stateName, { stateName = it }, "State", modifier = Modifier.weight(1f))
                }
                SrTextField(projectIdCode, { projectIdCode = it }, "Project Reference / ID")
            }

            SrSurfaceCard {
                SrSectionHeader("OFFICIAL PROJECT ORGANIZATIONS")
                SrTextField(pmcOrg, { pmcOrg = it }, "PMC Organization *", testTagName = "sr_pmc_org")
                SrTextField(contractor, { contractor = it }, "Contractor Name")
                SrTextField(ieOrg, { ieOrg = it }, "IE Organization")
                SrTextField(concessionaire, { concessionaire = it }, "Concessionaire Name")
                SrTextField(client, { client = it }, "Client / Authority Name")
                SrTextField(pmcName, { pmcName = it }, "Assigned PMC Representative Name")
                SrTextField(pmcContact, { pmcContact = it }, "Assigned PMC Contact")
                if (state.session == null) {
                    SrTextField(adminEmail, { adminEmail = it }, "PMC Admin Email", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                }
            }

            SrPrimaryButton(
                text = "Create Project & Generate PMC Code",
                onClick = {
                    onCreateProject(
                        projectName, highwayName, nhSh, pkg, location, stateName,
                        contractor, concessionaire, client, pmcOrg, ieOrg,
                        pmcName, pmcContact, projectIdCode, pmcName, adminEmail
                    )
                },
                icon = Icons.Default.AdminPanelSettings,
                modifier = Modifier.fillMaxWidth(),
                testTagName = "sr_pmc_create_submit"
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Enforces exact post-login access gates per Specification Part 2.10:
 * COMPLETE_PROFILE, VERIFY_EMAIL, PENDING_APPROVAL, REJECTED_STATUS,
 * ACCESS_BLOCKED, JOIN_PROJECT_CODE, PROJECT_SELECTOR.
 */
@Composable
fun SiteRegisterAccessGateScreen(
    state: SiteRegisterUiState,
    onCompleteProfile: (String, String, String, String) -> Unit,
    onMarkEmailVerified: () -> Unit,
    onSelectProject: (ProjectMembership) -> Unit,
    onVerifyCode: (String) -> Unit,
    onJoinProject: (OrganizationType, String, Boolean, String) -> Unit,
    onOpenPmcBootstrap: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    onDismissBanner: () -> Unit
) {
    val c = CtecsTheme.colors
    when (state.accessRoute) {
        LoginAccessRoute.JOIN_PROJECT_CODE -> {
            SiteRegisterRegistrationScreen(
                state = state,
                isJoinAdditionalMode = true,
                onVerifyCode = onVerifyCode,
                onSubmitRegistration = { _, _, _, _, _, _, _, _, _, _ -> },
                onSubmitJoinAdditional = onJoinProject,
                onBack = onBack,
                onDismissBanner = onDismissBanner
            )
            return
        }
        else -> Unit
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
    ) {
        SrTopBar(
            title = "Site Register Access",
            subtitle = state.profile?.email.orEmpty(),
            onBack = onBack
        )
        SrBannerStrip(state.bannerMessage, state.bannerIsError, onDismissBanner)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (state.accessRoute) {
                LoginAccessRoute.COMPLETE_PROFILE -> {
                    var fullName by remember { mutableStateOf(state.profile?.fullName.orEmpty()) }
                    var username by remember { mutableStateOf(state.profile?.username.orEmpty()) }
                    var mobile by remember { mutableStateOf(state.profile?.mobileNumber.orEmpty()) }
                    var designation by remember { mutableStateOf(state.profile?.defaultDesignation.orEmpty()) }
                    SrSurfaceCard {
                        Text("Complete Your Profile", color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        SrTextField(fullName, { fullName = it }, "Full Name *")
                        SrTextField(username, { username = it }, "Username *")
                        SrTextField(mobile, { mobile = it }, "Mobile Number")
                        SrTextField(designation, { designation = it }, "Designation")
                        SrPrimaryButton(
                            text = "Save Profile",
                            onClick = { onCompleteProfile(fullName, username, mobile, designation) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                LoginAccessRoute.VERIFY_EMAIL -> {
                    SrSurfaceCard {
                        Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = c.accent, modifier = Modifier.size(32.dp))
                        Text("Verify Your Email Address", color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Please verify ${state.session?.email} before accessing project site registers.",
                            color = c.textSecondary,
                            fontSize = 13.sp
                        )
                        SrPrimaryButton("I Have Verified My Email", onClick = onMarkEmailVerified, modifier = Modifier.fillMaxWidth())
                    }
                }
                LoginAccessRoute.PENDING_APPROVAL -> {
                    val pendingMem = state.userMemberships.firstOrNull {
                        it.approvalStatus == MembershipApprovalStatus.PENDING ||
                            it.approvalStatus == MembershipApprovalStatus.INFO_REQUESTED
                    }
                    val proj = state.projects.firstOrNull { it.id == pendingMem?.projectId }
                    SrSurfaceCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.HourglassTop, contentDescription = null, tint = c.warning, modifier = Modifier.size(26.dp))
                            Text("Pending PMC Approval", color = c.warning, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "Your account is authenticated, but Site Register access is blocked until the assigned PMC Representative reviews and approves your membership and assigns your Project Role.",
                            color = c.textSecondary,
                            fontSize = 12.5.sp
                        )
                        if (pendingMem != null) {
                            Text("Project: ${pendingMem.projectName}", color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Organization: ${pendingMem.organizationName} (${pendingMem.organizationType.displayLabel})", color = c.textSecondary, fontSize = 12.sp)
                            Text("Requested Designation: ${pendingMem.designation}", color = c.textSecondary, fontSize = 12.sp)
                            Text("Status: ${pendingMem.approvalStatus.label}", color = c.warning, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (proj != null && (proj.assignedPmcName.isNotBlank() || proj.assignedPmcContact.isNotBlank())) {
                            Text(
                                "Assigned PMC Contact: ${proj.assignedPmcName} ${proj.assignedPmcContact}",
                                color = c.accent,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                LoginAccessRoute.REJECTED_STATUS -> {
                    val rej = state.userMemberships.firstOrNull { it.approvalStatus == MembershipApprovalStatus.REJECTED }
                    SrSurfaceCard {
                        Icon(Icons.Default.Block, contentDescription = null, tint = c.error, modifier = Modifier.size(28.dp))
                        Text("Registration Request Rejected", color = c.error, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "PMC Remarks: ${rej?.remarks.orEmpty().ifBlank { "Please contact your PMC Representative." }}",
                            color = c.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
                LoginAccessRoute.ACCESS_BLOCKED -> {
                    SrSurfaceCard {
                        Icon(Icons.Default.Block, contentDescription = null, tint = c.error, modifier = Modifier.size(28.dp))
                        Text("Project Access Suspended / Blocked", color = c.error, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Your account or project access is currently suspended or revoked. Contact the Project PMC Representative.",
                            color = c.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
                LoginAccessRoute.PROJECT_SELECTOR -> {
                    val approved = state.userMemberships.filter { it.approvalStatus == MembershipApprovalStatus.APPROVED }
                    SrSurfaceCard {
                        Text("Select Active Highway Project", color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Each project enforces its own independent assigned role and data isolation.", color = c.textSecondary, fontSize = 12.sp)
                        approved.forEach { m ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(c.secondarySurface)
                                    .clickable { onSelectProject(m) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(m.projectName.ifBlank { m.projectId.take(8) }, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${m.organizationName} • ${m.designation}", color = c.textSecondary, fontSize = 12.sp)
                                }
                                SrStatusBadge(m.assignedRole.displayLabel, c.accent)
                            }
                        }
                    }
                }
                else -> Unit
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SrTonalButton(
                    text = "New PMC Project",
                    onClick = onOpenPmcBootstrap,
                    accent = c.warning,
                    modifier = Modifier.weight(1f)
                )
                SrTonalButton(
                    text = "Logout",
                    onClick = onLogout,
                    accent = c.error,
                    modifier = Modifier.weight(1f),
                    testTagName = "sr_gate_logout"
                )
            }
        }
    }
}
