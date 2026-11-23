-- ============================================================================
-- CTECS Digital Highway Site Register & Construction Quality Management System
-- Migration 002: PostgreSQL Row Level Security (RLS) Policies on All 28 Tables
-- Enforces:
--   - Separate SELECT, INSERT, UPDATE, DELETE policies on every exposed table
--   - Project-scoped access strictly for APPROVED project members
--   - Separation of Organization Type and Assigned Project Role
--   - Immutable approved revisions & time-bounded creator-only temporary editing
--   - Append-only permanent audit history
-- ============================================================================

-- Helper security functions (SECURITY DEFINER to avoid recursive RLS lookups)
CREATE OR REPLACE FUNCTION public.ctecs_is_system_admin(p_uid UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = p_uid AND is_system_admin = TRUE AND account_status = 'ACTIVE'
    );
$$;

CREATE OR REPLACE FUNCTION public.ctecs_is_approved_member(p_project_id UUID, p_uid UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT public.ctecs_is_system_admin(p_uid) OR EXISTS (
        SELECT 1 FROM public.project_members
        WHERE project_id = p_project_id
          AND user_id = p_uid
          AND approval_status = 'APPROVED'
    );
$$;

CREATE OR REPLACE FUNCTION public.ctecs_has_project_role(p_project_id UUID, p_uid UUID, p_roles TEXT[])
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT public.ctecs_is_system_admin(p_uid) OR EXISTS (
        SELECT 1 FROM public.project_members
        WHERE project_id = p_project_id
          AND user_id = p_uid
          AND approval_status = 'APPROVED'
          AND assigned_role = ANY(p_roles)
    );
$$;

-- Checks whether p_uid currently has database-verified write permission to a register & revision:
--   1. In DRAFT or REJECTED state: creator or approved Site Engineer / Site Supervisor / Contractor Site Engineer.
--   2. In APPROVED_LOCKED / SUBMITTED / PENDING_PMC_APPROVAL: NOBODY (direct API updates denied!).
--   3. In EDIT_PERMISSION_GRANTED / REOPENED: ONLY the designated creator holding an active,
--      unrevoked, non-expired site_edit_permissions row granted by a different PMC user.
CREATE OR REPLACE FUNCTION public.ctecs_can_edit_register(p_register_id UUID, p_uid UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_reg RECORD;
BEGIN
    SELECT id, project_id, created_by, status, current_revision
    INTO v_reg
    FROM public.site_registers
    WHERE id = p_register_id;

    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;

    IF NOT public.ctecs_is_approved_member(v_reg.project_id, p_uid) THEN
        RETURN FALSE;
    END IF;

    IF v_reg.status IN ('DRAFT', 'REJECTED') THEN
        RETURN (
            v_reg.created_by = p_uid
            OR public.ctecs_has_project_role(
                v_reg.project_id, p_uid,
                ARRAY['SITE_ENGINEER', 'SITE_SUPERVISOR', 'CONTRACTOR_SITE_ENGINEER']
            )
        );
    END IF;

    IF v_reg.status IN ('EDIT_PERMISSION_GRANTED', 'REOPENED') THEN
        RETURN (
            v_reg.created_by = p_uid
            AND EXISTS (
                SELECT 1
                FROM public.site_edit_permissions ep
                WHERE ep.register_id = v_reg.id
                  AND ep.target_revision_number = v_reg.current_revision
                  AND ep.authorized_user_id = p_uid
                  AND ep.granted_by_pmc_id <> p_uid
                  AND ep.is_active = TRUE
                  AND ep.revoked_at IS NULL
                  AND ep.consumed_at IS NULL
                  AND ep.expires_at > NOW()
            )
        );
    END IF;

    -- SUBMITTED, PENDING_PMC_APPROVAL, APPROVED_LOCKED, EDIT_PERMISSION_REQUESTED,
    -- EDIT_PERMISSION_EXPIRED, RESUBMITTED -> strictly immutable to direct edits!
    RETURN FALSE;
END;
$$;

-- Enable RLS on ALL 28 tables
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.project_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.contractors ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.project_invitations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.registration_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.highway_activities ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.activity_measurement_templates ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.site_registers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.site_register_revisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.site_register_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.site_measurements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.site_segments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.glass_grid_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tackcoat_tests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tackcoat_test_measurements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.quality_test_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.site_observations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.site_rectifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.site_attachments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.site_signatures ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.site_sharing_codes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.site_edit_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.site_edit_permissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.site_approvals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.site_audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.daily_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_notifications ENABLE ROW LEVEL SECURITY;

-- 1. profiles policies
CREATE POLICY profiles_select ON public.profiles FOR SELECT
    USING (
        id = auth.uid()
        OR public.ctecs_is_system_admin(auth.uid())
        OR EXISTS (
            SELECT 1 FROM public.project_members pm1
            JOIN public.project_members pm2 ON pm1.project_id = pm2.project_id
            WHERE pm1.user_id = auth.uid() AND pm1.approval_status = 'APPROVED'
              AND pm2.user_id = profiles.id
        )
    );
CREATE POLICY profiles_insert ON public.profiles FOR INSERT
    WITH CHECK (id = auth.uid() AND is_system_admin = FALSE);
CREATE POLICY profiles_update ON public.profiles FOR UPDATE
    USING (id = auth.uid() OR public.ctecs_is_system_admin(auth.uid()))
    WITH CHECK (
        (id = auth.uid() AND is_system_admin = (SELECT p.is_system_admin FROM public.profiles p WHERE p.id = auth.uid()))
        OR public.ctecs_is_system_admin(auth.uid())
    );
CREATE POLICY profiles_delete ON public.profiles FOR DELETE
    USING (public.ctecs_is_system_admin(auth.uid()));

-- 2. projects policies (confidential details hidden until code validated via RPC or approved membership)
CREATE POLICY projects_select ON public.projects FOR SELECT
    USING (public.ctecs_is_approved_member(id, auth.uid()));
CREATE POLICY projects_insert ON public.projects FOR INSERT
    WITH CHECK (
        public.ctecs_is_system_admin(auth.uid())
        OR EXISTS (
            SELECT 1 FROM public.project_members
            WHERE user_id = auth.uid() AND approval_status = 'APPROVED'
              AND assigned_role IN ('PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR')
        )
    );
CREATE POLICY projects_update ON public.projects FOR UPDATE
    USING (public.ctecs_has_project_role(id, auth.uid(), ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR']))
    WITH CHECK (public.ctecs_has_project_role(id, auth.uid(), ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR']));
CREATE POLICY projects_delete ON public.projects FOR DELETE
    USING (public.ctecs_is_system_admin(auth.uid()));

-- 3. project_members policies (users can view own memberships; only PMC/Admin can manage via RPC)
CREATE POLICY project_members_select ON public.project_members FOR SELECT
    USING (
        user_id = auth.uid()
        OR public.ctecs_is_approved_member(project_id, auth.uid())
    );
CREATE POLICY project_members_insert ON public.project_members FOR INSERT
    WITH CHECK (
        public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR'])
    );
CREATE POLICY project_members_update ON public.project_members FOR UPDATE
    USING (
        public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR'])
        AND user_id <> auth.uid() -- Prevent self-approval / self-elevation
    )
    WITH CHECK (
        public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR'])
        AND user_id <> auth.uid()
    );
CREATE POLICY project_members_delete ON public.project_members FOR DELETE
    USING (public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR']));

-- 4. contractors policies
CREATE POLICY contractors_select ON public.contractors FOR SELECT
    USING (public.ctecs_is_approved_member(project_id, auth.uid()));
CREATE POLICY contractors_insert ON public.contractors FOR INSERT
    WITH CHECK (public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR']));
CREATE POLICY contractors_update ON public.contractors FOR UPDATE
    USING (public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR']));
CREATE POLICY contractors_delete ON public.contractors FOR DELETE
    USING (public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR']));

-- 5. project_invitations policies (only PMC/Admin view & manage; registrants validate through SECURITY DEFINER RPC)
CREATE POLICY invitations_select ON public.project_invitations FOR SELECT
    USING (public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR']));
CREATE POLICY invitations_insert ON public.project_invitations FOR INSERT
    WITH CHECK (public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR']));
CREATE POLICY invitations_update ON public.project_invitations FOR UPDATE
    USING (public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR']));
CREATE POLICY invitations_delete ON public.project_invitations FOR DELETE
    USING (public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR']));

-- 6. registration_requests policies
CREATE POLICY reg_requests_select ON public.registration_requests FOR SELECT
    USING (
        user_id = auth.uid()
        OR public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR'])
    );
CREATE POLICY reg_requests_insert ON public.registration_requests FOR INSERT
    WITH CHECK (user_id = auth.uid() AND status = 'PENDING' AND assigned_role IS NULL);
CREATE POLICY reg_requests_update ON public.registration_requests FOR UPDATE
    USING (
        public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR'])
        AND user_id <> auth.uid()
    );
CREATE POLICY reg_requests_delete ON public.registration_requests FOR DELETE
    USING (public.ctecs_is_system_admin(auth.uid()));

-- 7 & 8. highway_activities & activity_measurement_templates
CREATE POLICY activities_select ON public.highway_activities FOR SELECT
    USING (project_id IS NULL OR public.ctecs_is_approved_member(project_id, auth.uid()));
CREATE POLICY activities_insert ON public.highway_activities FOR INSERT
    WITH CHECK (project_id IS NOT NULL AND public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR']));
CREATE POLICY activities_update ON public.highway_activities FOR UPDATE
    USING (project_id IS NOT NULL AND public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR']));
CREATE POLICY activities_delete ON public.highway_activities FOR DELETE
    USING (project_id IS NOT NULL AND public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR']));

CREATE POLICY templates_select ON public.activity_measurement_templates FOR SELECT
    USING (project_id IS NULL OR public.ctecs_is_approved_member(project_id, auth.uid()));
CREATE POLICY templates_insert ON public.activity_measurement_templates FOR INSERT
    WITH CHECK (project_id IS NOT NULL AND public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR']));
CREATE POLICY templates_update ON public.activity_measurement_templates FOR UPDATE
    USING (project_id IS NOT NULL AND public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR']));
CREATE POLICY templates_delete ON public.activity_measurement_templates FOR DELETE
    USING (project_id IS NOT NULL AND public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR']));

-- 9. site_registers policies (enforces immutable lock at DB level)
CREATE POLICY registers_select ON public.site_registers FOR SELECT
    USING (public.ctecs_is_approved_member(project_id, auth.uid()));
CREATE POLICY registers_insert ON public.site_registers FOR INSERT
    WITH CHECK (
        created_by = auth.uid()
        AND status = 'DRAFT'
        AND public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['SITE_ENGINEER', 'SITE_SUPERVISOR', 'CONTRACTOR_SITE_ENGINEER', 'PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR'])
    );
CREATE POLICY registers_update ON public.site_registers FOR UPDATE
    USING (public.ctecs_can_edit_register(id, auth.uid()))
    WITH CHECK (
        public.ctecs_can_edit_register(id, auth.uid())
        AND status NOT IN ('APPROVED_LOCKED') -- Only PMC approval RPC may set APPROVED_LOCKED
    );
CREATE POLICY registers_delete ON public.site_registers FOR DELETE
    USING (
        status = 'DRAFT'
        AND created_by = auth.uid()
        AND public.ctecs_is_approved_member(project_id, auth.uid())
    );

-- 10. site_register_revisions policies (strictly immutable once locked)
CREATE POLICY revisions_select ON public.site_register_revisions FOR SELECT
    USING (public.ctecs_is_approved_member(project_id, auth.uid()));
CREATE POLICY revisions_insert ON public.site_register_revisions FOR INSERT
    WITH CHECK (public.ctecs_can_edit_register(register_id, auth.uid()));
CREATE POLICY revisions_update ON public.site_register_revisions FOR UPDATE
    USING (is_locked = FALSE AND public.ctecs_can_edit_register(register_id, auth.uid()));
CREATE POLICY revisions_delete ON public.site_register_revisions FOR DELETE
    USING (FALSE); -- Revisions are permanent history

-- 11-14. Register child work tables (entries, measurements, segments, glass_grid)
CREATE POLICY entries_select ON public.site_register_entries FOR SELECT
    USING (public.ctecs_is_approved_member(project_id, auth.uid()));
CREATE POLICY entries_insert ON public.site_register_entries FOR INSERT
    WITH CHECK (created_by = auth.uid() AND public.ctecs_can_edit_register(register_id, auth.uid()));
CREATE POLICY entries_update ON public.site_register_entries FOR UPDATE
    USING (public.ctecs_can_edit_register(register_id, auth.uid()));
CREATE POLICY entries_delete ON public.site_register_entries FOR DELETE
    USING (public.ctecs_can_edit_register(register_id, auth.uid()));

CREATE POLICY measurements_select ON public.site_measurements FOR SELECT
    USING (public.ctecs_is_approved_member(project_id, auth.uid()));
CREATE POLICY measurements_insert ON public.site_measurements FOR INSERT
    WITH CHECK (created_by = auth.uid() AND public.ctecs_can_edit_register(register_id, auth.uid()));
CREATE POLICY measurements_update ON public.site_measurements FOR UPDATE
    USING (public.ctecs_can_edit_register(register_id, auth.uid()));
CREATE POLICY measurements_delete ON public.site_measurements FOR DELETE
    USING (public.ctecs_can_edit_register(register_id, auth.uid()));

CREATE POLICY segments_select ON public.site_segments FOR SELECT
    USING (public.ctecs_is_approved_member(project_id, auth.uid()));
CREATE POLICY segments_insert ON public.site_segments FOR INSERT
    WITH CHECK (created_by = auth.uid() AND public.ctecs_can_edit_register(register_id, auth.uid()));
CREATE POLICY segments_update ON public.site_segments FOR UPDATE
    USING (public.ctecs_can_edit_register(register_id, auth.uid()));
CREATE POLICY segments_delete ON public.site_segments FOR DELETE
    USING (public.ctecs_can_edit_register(register_id, auth.uid()));

CREATE POLICY glass_grid_select ON public.glass_grid_entries FOR SELECT
    USING (public.ctecs_is_approved_member(project_id, auth.uid()));
CREATE POLICY glass_grid_insert ON public.glass_grid_entries FOR INSERT
    WITH CHECK (created_by = auth.uid() AND public.ctecs_can_edit_register(register_id, auth.uid()));
CREATE POLICY glass_grid_update ON public.glass_grid_entries FOR UPDATE
    USING (public.ctecs_can_edit_register(register_id, auth.uid()));
CREATE POLICY glass_grid_delete ON public.glass_grid_entries FOR DELETE
    USING (public.ctecs_can_edit_register(register_id, auth.uid()));

-- 15-17. Tack Coat Tests & Quality Test Reports
CREATE POLICY tackcoat_tests_select ON public.tackcoat_tests FOR SELECT
    USING (public.ctecs_is_approved_member(project_id, auth.uid()));
CREATE POLICY tackcoat_tests_insert ON public.tackcoat_tests FOR INSERT
    WITH CHECK (
        created_by = auth.uid()
        AND public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['SITE_ENGINEER', 'SITE_SUPERVISOR', 'CONTRACTOR_SITE_ENGINEER', 'IE_REPRESENTATIVE', 'PMC_REPRESENTATIVE'])
        AND (register_id IS NULL OR public.ctecs_can_edit_register(register_id, auth.uid()))
    );
CREATE POLICY tackcoat_tests_update ON public.tackcoat_tests FOR UPDATE
    USING (
        (register_id IS NOT NULL AND public.ctecs_can_edit_register(register_id, auth.uid()))
        OR (register_id IS NULL AND created_by = auth.uid() AND public.ctecs_is_approved_member(project_id, auth.uid()))
    );
CREATE POLICY tackcoat_tests_delete ON public.tackcoat_tests FOR DELETE
    USING (register_id IS NULL AND created_by = auth.uid());

CREATE POLICY tackcoat_rows_select ON public.tackcoat_test_measurements FOR SELECT
    USING (public.ctecs_is_approved_member(project_id, auth.uid()));
CREATE POLICY tackcoat_rows_insert ON public.tackcoat_test_measurements FOR INSERT
    WITH CHECK (public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['SITE_ENGINEER', 'SITE_SUPERVISOR', 'CONTRACTOR_SITE_ENGINEER', 'IE_REPRESENTATIVE', 'PMC_REPRESENTATIVE']));
CREATE POLICY tackcoat_rows_update ON public.tackcoat_test_measurements FOR UPDATE
    USING (public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['SITE_ENGINEER', 'SITE_SUPERVISOR', 'CONTRACTOR_SITE_ENGINEER', 'PMC_REPRESENTATIVE']));
CREATE POLICY tackcoat_rows_delete ON public.tackcoat_test_measurements FOR DELETE
    USING (public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['SITE_ENGINEER', 'PMC_REPRESENTATIVE']));

CREATE POLICY quality_tests_select ON public.quality_test_reports FOR SELECT
    USING (public.ctecs_is_approved_member(project_id, auth.uid()));
CREATE POLICY quality_tests_insert ON public.quality_test_reports FOR INSERT
    WITH CHECK (
        created_by = auth.uid()
        AND public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['SITE_ENGINEER', 'SITE_SUPERVISOR', 'CONTRACTOR_SITE_ENGINEER', 'IE_REPRESENTATIVE', 'PMC_REPRESENTATIVE'])
        AND (register_id IS NULL OR public.ctecs_can_edit_register(register_id, auth.uid()))
    );
CREATE POLICY quality_tests_update ON public.quality_test_reports FOR UPDATE
    USING (
        (register_id IS NOT NULL AND public.ctecs_can_edit_register(register_id, auth.uid()))
        OR (register_id IS NULL AND created_by = auth.uid())
    );
CREATE POLICY quality_tests_delete ON public.quality_test_reports FOR DELETE
    USING (register_id IS NULL AND created_by = auth.uid());

-- 18 & 19. Observations & Rectifications
CREATE POLICY observations_select ON public.site_observations FOR SELECT
    USING (public.ctecs_is_approved_member(project_id, auth.uid()));
CREATE POLICY observations_insert ON public.site_observations FOR INSERT
    WITH CHECK (
        created_by = auth.uid()
        AND public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['SITE_ENGINEER', 'SITE_SUPERVISOR', 'CONTRACTOR_SITE_ENGINEER', 'CONTRACTOR_REPRESENTATIVE', 'IE_REPRESENTATIVE', 'PMC_REPRESENTATIVE', 'PROJECT_MANAGER'])
    );
CREATE POLICY observations_update ON public.site_observations FOR UPDATE
    USING (
        public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['SITE_ENGINEER', 'CONTRACTOR_REPRESENTATIVE', 'IE_REPRESENTATIVE', 'PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR'])
    );
CREATE POLICY observations_delete ON public.site_observations FOR DELETE
    USING (public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR']));

CREATE POLICY rectifications_select ON public.site_rectifications FOR SELECT
    USING (public.ctecs_is_approved_member(project_id, auth.uid()));
CREATE POLICY rectifications_insert ON public.site_rectifications FOR INSERT
    WITH CHECK (
        submitted_by = auth.uid()
        AND public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['SITE_ENGINEER', 'SITE_SUPERVISOR', 'CONTRACTOR_SITE_ENGINEER', 'CONTRACTOR_REPRESENTATIVE', 'IE_REPRESENTATIVE', 'PMC_REPRESENTATIVE'])
    );
CREATE POLICY rectifications_update ON public.site_rectifications FOR UPDATE
    USING (public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['IE_REPRESENTATIVE', 'PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR']));
CREATE POLICY rectifications_delete ON public.site_rectifications FOR DELETE
    USING (FALSE);

-- 20. site_attachments
CREATE POLICY attachments_select ON public.site_attachments FOR SELECT
    USING (public.ctecs_is_approved_member(project_id, auth.uid()));
CREATE POLICY attachments_insert ON public.site_attachments FOR INSERT
    WITH CHECK (
        uploaded_by = auth.uid()
        AND public.ctecs_is_approved_member(project_id, auth.uid())
        AND (register_id IS NULL OR public.ctecs_can_edit_register(register_id, auth.uid()))
    );
CREATE POLICY attachments_update ON public.site_attachments FOR UPDATE
    USING (FALSE); -- Original evidence metadata is immutable
CREATE POLICY attachments_delete ON public.site_attachments FOR DELETE
    USING (register_id IS NOT NULL AND uploaded_by = auth.uid() AND public.ctecs_can_edit_register(register_id, auth.uid()));

-- 21. site_signatures
CREATE POLICY signatures_select ON public.site_signatures FOR SELECT
    USING (public.ctecs_is_approved_member(project_id, auth.uid()));
CREATE POLICY signatures_insert ON public.site_signatures FOR INSERT
    WITH CHECK (
        signer_user_id = auth.uid()
        AND public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['SITE_ENGINEER', 'CONTRACTOR_SITE_ENGINEER', 'CONTRACTOR_REPRESENTATIVE', 'IE_REPRESENTATIVE', 'PMC_REPRESENTATIVE', 'PROJECT_MANAGER'])
    );
CREATE POLICY signatures_update ON public.site_signatures FOR UPDATE
    USING (FALSE); -- Signatures are immutable records
CREATE POLICY signatures_delete ON public.site_signatures FOR DELETE
    USING (FALSE);

-- 22. site_sharing_codes
CREATE POLICY sharing_codes_select ON public.site_sharing_codes FOR SELECT
    USING (public.ctecs_is_approved_member(project_id, auth.uid()));
CREATE POLICY sharing_codes_insert ON public.site_sharing_codes FOR INSERT
    WITH CHECK (
        created_by = auth.uid()
        AND public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['SITE_ENGINEER', 'CONTRACTOR_SITE_ENGINEER', 'PMC_REPRESENTATIVE', 'PROJECT_MANAGER', 'PROJECT_ADMINISTRATOR'])
    );
CREATE POLICY sharing_codes_update ON public.site_sharing_codes FOR UPDATE
    USING (
        created_by = auth.uid()
        OR public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR'])
    );
CREATE POLICY sharing_codes_delete ON public.site_sharing_codes FOR DELETE
    USING (public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR']));

-- 23 & 24. site_edit_requests & site_edit_permissions
CREATE POLICY edit_requests_select ON public.site_edit_requests FOR SELECT
    USING (public.ctecs_is_approved_member(project_id, auth.uid()));
CREATE POLICY edit_requests_insert ON public.site_edit_requests FOR INSERT
    WITH CHECK (
        requested_by = auth.uid()
        AND status = 'PENDING'
        AND EXISTS (SELECT 1 FROM public.site_registers r WHERE r.id = register_id AND r.created_by = auth.uid() AND r.status = 'APPROVED_LOCKED')
    );
CREATE POLICY edit_requests_update ON public.site_edit_requests FOR UPDATE
    USING (
        public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR'])
        AND requested_by <> auth.uid()
    );
CREATE POLICY edit_requests_delete ON public.site_edit_requests FOR DELETE
    USING (FALSE);

CREATE POLICY edit_permissions_select ON public.site_edit_permissions FOR SELECT
    USING (public.ctecs_is_approved_member(project_id, auth.uid()));
CREATE POLICY edit_permissions_insert ON public.site_edit_permissions FOR INSERT
    WITH CHECK (
        granted_by_pmc_id = auth.uid()
        AND authorized_user_id <> auth.uid()
        AND public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR'])
    );
CREATE POLICY edit_permissions_update ON public.site_edit_permissions FOR UPDATE
    USING (public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR']));
CREATE POLICY edit_permissions_delete ON public.site_edit_permissions FOR DELETE
    USING (FALSE);

-- 25. site_approvals (strictly PMC/Admin append-only)
CREATE POLICY approvals_select ON public.site_approvals FOR SELECT
    USING (public.ctecs_is_approved_member(project_id, auth.uid()));
CREATE POLICY approvals_insert ON public.site_approvals FOR INSERT
    WITH CHECK (
        pmc_user_id = auth.uid()
        AND public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR'])
    );
CREATE POLICY approvals_update ON public.site_approvals FOR UPDATE USING (FALSE);
CREATE POLICY approvals_delete ON public.site_approvals FOR DELETE USING (FALSE);

-- 26. site_audit_logs (permanent append-only history; ordinary users can NEVER alter or delete)
CREATE POLICY audit_logs_select ON public.site_audit_logs FOR SELECT
    USING (
        project_id IS NOT NULL
        AND public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PMC_REPRESENTATIVE', 'IE_REPRESENTATIVE', 'PROJECT_MANAGER', 'PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR'])
    );
CREATE POLICY audit_logs_insert ON public.site_audit_logs FOR INSERT
    WITH CHECK (user_id = auth.uid());
CREATE POLICY audit_logs_update ON public.site_audit_logs FOR UPDATE USING (FALSE);
CREATE POLICY audit_logs_delete ON public.site_audit_logs FOR DELETE USING (FALSE);

-- 27. daily_reports
CREATE POLICY daily_reports_select ON public.daily_reports FOR SELECT
    USING (public.ctecs_is_approved_member(project_id, auth.uid()));
CREATE POLICY daily_reports_insert ON public.daily_reports FOR INSERT
    WITH CHECK (generated_by = auth.uid() AND public.ctecs_is_approved_member(project_id, auth.uid()));
CREATE POLICY daily_reports_update ON public.daily_reports FOR UPDATE USING (FALSE);
CREATE POLICY daily_reports_delete ON public.daily_reports FOR DELETE
    USING (public.ctecs_has_project_role(project_id, auth.uid(), ARRAY['PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR']));

-- 28. user_notifications
CREATE POLICY notifications_select ON public.user_notifications FOR SELECT
    USING (user_id = auth.uid());
CREATE POLICY notifications_insert ON public.user_notifications FOR INSERT
    WITH CHECK (project_id IS NULL OR public.ctecs_is_approved_member(project_id, auth.uid()));
CREATE POLICY notifications_update ON public.user_notifications FOR UPDATE
    USING (user_id = auth.uid());
CREATE POLICY notifications_delete ON public.user_notifications FOR DELETE
    USING (user_id = auth.uid());
