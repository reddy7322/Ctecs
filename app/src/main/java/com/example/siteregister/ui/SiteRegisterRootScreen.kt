package com.example.siteregister.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.siteregister.model.LoginAccessRoute
import com.example.ui.theme.CtecsTheme

/**
 * Top-level entry composable for the CTECS Digital Highway Site Register & Construction Quality
 * Management System. Routes strictly according to authenticated session, PMC Project Code
 * approval status, multi-project role assignment, and handles Android hardware back navigation.
 */
@Composable
fun SiteRegisterRootScreen(
    defaultChainage: String,
    defaultSide: String,
    recentCtecsPhotoUris: List<String>,
    onLaunchCtecsCamera: () -> Unit,
    onExitToHome: () -> Unit,
    srViewModel: SiteRegisterViewModel = viewModel()
) {
    val state by srViewModel.uiState.collectAsStateWithLifecycle()

    // Handle sub-screen back navigation before exiting to CTECS Home
    val canPopInternal = state.showCreatePmcProjectBootstrap ||
        state.showRegistrationScreen ||
        (state.accessRoute == LoginAccessRoute.PROJECT_DASHBOARD && state.currentSection != SiteRegisterNavSection.DASHBOARD)

    BackHandler(enabled = canPopInternal) {
        when {
            state.showCreatePmcProjectBootstrap -> srViewModel.showCreatePmcBootstrap(false)
            state.showRegistrationScreen -> srViewModel.showRegistrationFlow(false)
            state.currentSection != SiteRegisterNavSection.DASHBOARD ->
                srViewModel.navigateTo(SiteRegisterNavSection.DASHBOARD)
        }
    }

    if (state.showCreatePmcProjectBootstrap) {
        SiteRegisterPmcBootstrapScreen(
            state = state,
            onCreateProject = { pName, hwy, nhSh, pkg, loc, st, cont, conc, cli, pmcOrg, ieOrg, pmcName, pmcContact, pid, adminName, adminEmail ->
                srViewModel.createProjectAsPmcAdmin(
                    projectName = pName, highwayName = hwy, nhShNumber = nhSh, packageNumber = pkg,
                    projectLocation = loc, stateName = st, contractorName = cont, concessionaireName = conc,
                    clientName = cli, pmcOrganization = pmcOrg, ieOrganization = ieOrg,
                    assignedPmcName = pmcName, assignedPmcContact = pmcContact,
                    projectIdentification = pid, adminFullName = adminName, adminEmail = adminEmail
                )
            },
            onBack = { srViewModel.showCreatePmcBootstrap(false) },
            onDismissBanner = { srViewModel.clearBanner() }
        )
        return
    }

    if (state.accessRoute == LoginAccessRoute.UNAUTHENTICATED) {
        if (state.showRegistrationScreen) {
            SiteRegisterRegistrationScreen(
                state = state,
                isJoinAdditionalMode = false,
                onVerifyCode = { srViewModel.verifyProjectCode(it) },
                onSubmitRegistration = { full, user, email, mob, pw, cpw, orgT, orgN, unlisted, desig ->
                    srViewModel.registerWithVerifiedProjectCode(full, user, email, mob, pw, cpw, orgT, orgN, unlisted, desig)
                },
                onSubmitJoinAdditional = { _, _, _, _ -> },
                onBack = { srViewModel.showRegistrationFlow(false) },
                onDismissBanner = { srViewModel.clearBanner() }
            )
        } else {
            SiteRegisterLoginScreen(
                state = state,
                onLogin = { e, p -> srViewModel.login(e, p) },
                onForgotPassword = { srViewModel.sendPasswordReset(it) },
                onOpenRegister = { srViewModel.showRegistrationFlow(true) },
                onOpenPmcBootstrap = { srViewModel.showCreatePmcBootstrap(true) },
                onBack = onExitToHome,
                onDismissBanner = { srViewModel.clearBanner() }
            )
        }
        return
    }

    if (state.accessRoute != LoginAccessRoute.PROJECT_DASHBOARD) {
        SiteRegisterAccessGateScreen(
            state = state,
            onCompleteProfile = { f, u, m, d -> srViewModel.completeProfile(f, u, m, d) },
            onMarkEmailVerified = { srViewModel.markEmailVerifiedRefresh() },
            onSelectProject = { srViewModel.selectActiveProject(it) },
            onVerifyCode = { srViewModel.verifyProjectCode(it) },
            onJoinProject = { orgT, orgN, unlisted, desig ->
                srViewModel.joinAdditionalProject(orgT, orgN, unlisted, desig)
            },
            onOpenPmcBootstrap = { srViewModel.showCreatePmcBootstrap(true) },
            onLogout = { srViewModel.logout() },
            onBack = onExitToHome,
            onDismissBanner = { srViewModel.clearBanner() }
        )
        return
    }

    when (state.currentSection) {
        SiteRegisterNavSection.DASHBOARD -> SiteRegisterDashboardScreen(
            state = state,
            onBack = onExitToHome,
            onNavigateSection = { srViewModel.navigateTo(it) },
            onOpenRegister = { srViewModel.openRegister(it) },
            onSelectProject = { srViewModel.selectActiveProject(it) },
            onTriggerSync = { srViewModel.triggerManualSync() },
            onJoinSharedCode = { srViewModel.joinLiveRegisterBySharingCode(it) },
            onShiftFilter = { srViewModel.setShiftFilter(it) },
            onDateFilter = { srViewModel.setDateFilter(it) },
            onSearchQuery = { srViewModel.setSearchQuery(it) },
            onLogout = { srViewModel.logout() },
            onDismissBanner = { srViewModel.clearBanner() }
        )
        SiteRegisterNavSection.ACTIVITY_PICKER -> SiteRegisterActivityPickerScreen(
            state = state,
            defaultChainage = defaultChainage,
            defaultSide = defaultSide,
            onCreateRegister = { tpl, dt, sh, sd, ln, cf, ct, rm ->
                srViewModel.createRegisterForActivity(tpl, dt, sh, sd, ln, cf, ct, rm)
            },
            onAddCustomActivity = { name, unit, vol -> srViewModel.addCustomActivityTemplate(name, unit, vol) },
            onBack = { srViewModel.navigateTo(SiteRegisterNavSection.DASHBOARD) }
        )
        SiteRegisterNavSection.REGISTER_DETAIL -> SiteRegisterDetailScreen(
            state = state,
            vm = srViewModel,
            recentCtecsPhotoUris = recentCtecsPhotoUris,
            onLaunchCtecsCamera = onLaunchCtecsCamera,
            onBack = { srViewModel.navigateTo(SiteRegisterNavSection.DASHBOARD) }
        )
        SiteRegisterNavSection.QUALITY_TESTS -> StandaloneSectionWrapper(
            title = "Quality Tests & Spray Rate",
            subtitle = state.activeProject?.projectName.orEmpty(),
            state = state,
            onDismissBanner = { srViewModel.clearBanner() },
            onBack = { srViewModel.navigateTo(SiteRegisterNavSection.DASHBOARD) }
        ) {
            TestsAndOcrTab(state, srViewModel, recentCtecsPhotoUris, onLaunchCtecsCamera)
        }
        SiteRegisterNavSection.OBSERVATIONS -> StandaloneSectionWrapper(
            title = "Site Observations & Rectification",
            subtitle = state.activeProject?.projectName.orEmpty(),
            state = state,
            onDismissBanner = { srViewModel.clearBanner() },
            onBack = { srViewModel.navigateTo(SiteRegisterNavSection.DASHBOARD) }
        ) {
            ObservationsInlineTab(state, srViewModel, recentCtecsPhotoUris, onLaunchCtecsCamera)
        }
        SiteRegisterNavSection.PMC_ADMIN -> SiteRegisterPmcAdminScreen(
            state = state,
            vm = srViewModel,
            onCreateNewProject = { srViewModel.showCreatePmcBootstrap(true) },
            onBack = { srViewModel.navigateTo(SiteRegisterNavSection.DASHBOARD) }
        )
        SiteRegisterNavSection.AUDIT_HISTORY -> SiteRegisterAuditHistoryScreen(
            state = state,
            onBack = { srViewModel.navigateTo(SiteRegisterNavSection.DASHBOARD) }
        )
        SiteRegisterNavSection.EXPORT_CENTER -> SiteRegisterExportScreen(
            state = state,
            vm = srViewModel,
            onBack = { srViewModel.navigateTo(SiteRegisterNavSection.DASHBOARD) }
        )
        SiteRegisterNavSection.SETTINGS -> SiteRegisterSettingsScreen(
            state = state,
            vm = srViewModel,
            onBack = { srViewModel.navigateTo(SiteRegisterNavSection.DASHBOARD) }
        )
    }
}

@Composable
private fun StandaloneSectionWrapper(
    title: String,
    subtitle: String,
    state: SiteRegisterUiState,
    onDismissBanner: () -> Unit,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    val c = CtecsTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
    ) {
        SrTopBar(title = title, subtitle = subtitle, onBack = onBack)
        SrBannerStrip(state.bannerMessage, state.bannerIsError, onDismissBanner)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }
    }
}
