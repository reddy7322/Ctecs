-- ============================================================================
-- CTECS Digital Highway Site Register & Construction Quality Management System
-- Migration 003: Transactional Security-Definer RPC Functions & Lock Triggers
-- ============================================================================

-- Trigger: Hard database-level block on mutating any locked revision row
CREATE OR REPLACE FUNCTION public.ctecs_block_locked_revision_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.is_locked = TRUE THEN
        RAISE EXCEPTION 'CTECS_IMMUTABLE_REVISION: Revision % of register % is PMC-approved and locked.',
            OLD.revision_number, OLD.register_id;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_block_locked_revision_mutation ON public.site_register_revisions;
CREATE TRIGGER trg_block_locked_revision_mutation
    BEFORE UPDATE OR DELETE ON public.site_register_revisions
    FOR EACH ROW EXECUTE FUNCTION public.ctecs_block_locked_revision_mutation();

-- 1. Validate a PMC-assigned Dynamic Project Code (returns ONLY non-confidential verified metadata)
CREATE OR REPLACE FUNCTION public.validate_project_code(p_code TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_inv RECORD;
    v_proj RECORD;
BEGIN
    IF p_code IS NULL OR LENGTH(TRIM(p_code)) < 6 THEN
        RETURN jsonb_build_object('valid', FALSE, 'error', 'Invalid project code format.');
    END IF;

    SELECT * INTO v_inv
    FROM public.project_invitations
    WHERE UPPER(project_code) = UPPER(TRIM(p_code))
    LIMIT 1;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('valid', FALSE, 'error', 'Project code not found.');
    END IF;

    IF v_inv.is_revoked THEN
        RETURN jsonb_build_object('valid', FALSE, 'error', 'This project code has been revoked by PMC.');
    END IF;

    IF NOT v_inv.is_enabled THEN
        RETURN jsonb_build_object('valid', FALSE, 'error', 'Registration is currently disabled for this project code.');
    END IF;

    IF v_inv.expires_at IS NOT NULL AND v_inv.expires_at <= NOW() THEN
        RETURN jsonb_build_object('valid', FALSE, 'error', 'This project code has expired.');
    END IF;

    IF v_inv.max_registrations IS NOT NULL AND v_inv.current_registrations >= v_inv.max_registrations THEN
        RETURN jsonb_build_object('valid', FALSE, 'error', 'Maximum registrations reached for this project code.');
    END IF;

    SELECT * INTO v_proj FROM public.projects WHERE id = v_inv.project_id;
    IF NOT FOUND OR v_proj.registration_status = 'CLOSED' THEN
        RETURN jsonb_build_object('valid', FALSE, 'error', 'Project registration is closed.');
    END IF;

    INSERT INTO public.site_audit_logs (project_id, user_id, action, metadata)
    VALUES (v_proj.id, auth.uid(), 'PROJECT_CODE_VALIDATED', jsonb_build_object('code', v_inv.project_code));

    RETURN jsonb_build_object(
        'valid', TRUE,
        'invitation_id', v_inv.id,
        'project_code', v_inv.project_code,
        'project_id', v_proj.id,
        'project_name', v_proj.project_name,
        'highway_name', v_proj.highway_name,
        'nh_sh_number', v_proj.nh_sh_number,
        'package_number', v_proj.package_number,
        'project_location', v_proj.project_location,
        'state_name', v_proj.state_name,
        'contractor_name', v_proj.contractor_name,
        'concessionaire_name', v_proj.concessionaire_name,
        'client_name', v_proj.client_name,
        'pmc_organization', v_proj.pmc_organization,
        'ie_organization', v_proj.ie_organization,
        'assigned_pmc_name', v_proj.assigned_pmc_name,
        'assigned_pmc_contact', v_proj.assigned_pmc_contact,
        'project_identification', v_proj.project_identification,
        'project_status', v_proj.project_status,
        'registration_status', v_proj.registration_status,
        'permitted_organization_types', v_inv.permitted_organization_types,
        'allowed_designations', v_proj.allowed_designations,
        'organization_directory', v_proj.organization_directory
    );
END;
$$;

-- 2. Register or join a project with a validated code (always creates PENDING membership; never grants PMC role)
CREATE OR REPLACE FUNCTION public.register_project_membership(
    p_project_code TEXT,
    p_full_name TEXT,
    p_username TEXT,
    p_email TEXT,
    p_mobile TEXT,
    p_org_type TEXT,
    p_org_name TEXT,
    p_designation TEXT,
    p_is_unlisted_org BOOLEAN DEFAULT FALSE
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_inv RECORD;
    v_proj RECORD;
    v_req_id UUID;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required.';
    END IF;

    SELECT * INTO v_inv
    FROM public.project_invitations
    WHERE UPPER(project_code) = UPPER(TRIM(p_project_code))
      AND is_enabled = TRUE
      AND is_revoked = FALSE
      AND (expires_at IS NULL OR expires_at > NOW())
      AND (max_registrations IS NULL OR current_registrations < max_registrations)
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Project code is invalid, expired, revoked, or exhausted.';
    END IF;

    IF NOT (p_org_type = ANY(v_inv.permitted_organization_types)) THEN
        RAISE EXCEPTION 'Selected organization type (%) is not permitted for this project code.', p_org_type;
    END IF;

    SELECT * INTO v_proj FROM public.projects WHERE id = v_inv.project_id;

    INSERT INTO public.profiles (id, full_name, username, email, mobile_number, default_designation, default_organization_type, default_organization_name)
    VALUES (v_uid, p_full_name, p_username, p_email, p_mobile, p_designation, p_org_type, p_org_name)
    ON CONFLICT (id) DO UPDATE SET
        full_name = EXCLUDED.full_name,
        mobile_number = EXCLUDED.mobile_number,
        default_designation = EXCLUDED.default_designation,
        updated_at = NOW();

    INSERT INTO public.project_members (
        project_id, user_id, organization_type, organization_name, organization_verified,
        designation, assigned_role, approval_status, project_code_used
    ) VALUES (
        v_inv.project_id, v_uid, p_org_type, p_org_name, NOT p_is_unlisted_org,
        p_designation, 'CLIENT_VIEWER', 'PENDING', v_inv.project_code
    )
    ON CONFLICT (project_id, user_id) DO NOTHING;

    INSERT INTO public.registration_requests (
        project_id, user_id, invitation_id, project_code_used, full_name, username, email,
        mobile_number, requested_organization_type, requested_organization_name,
        is_unlisted_organization, requested_designation, status
    ) VALUES (
        v_inv.project_id, v_uid, v_inv.id, v_inv.project_code, p_full_name, p_username, p_email,
        p_mobile, p_org_type, p_org_name, p_is_unlisted_org, p_designation, 'PENDING'
    ) RETURNING id INTO v_req_id;

    UPDATE public.project_invitations
    SET current_registrations = current_registrations + 1, updated_at = NOW()
    WHERE id = v_inv.id;

    -- Notify all approved PMC / Project Admins of this project
    INSERT INTO public.user_notifications (user_id, project_id, notification_type, title, message, payload)
    SELECT pm.user_id, v_inv.project_id, 'NEW_REGISTRATION',
           'New User Registration Pending Approval',
           p_full_name || ' (' || p_org_type || ' - ' || p_designation || ') requested access via ' || v_inv.project_code,
           jsonb_build_object('request_id', v_req_id, 'applicant_id', v_uid)
    FROM public.project_members pm
    WHERE pm.project_id = v_inv.project_id
      AND pm.approval_status = 'APPROVED'
      AND pm.assigned_role IN ('PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR');

    INSERT INTO public.site_audit_logs (project_id, user_id, organization_type, assigned_role, action, after_values)
    VALUES (
        v_inv.project_id, v_uid, p_org_type, 'PENDING', 'PROJECT_MEMBERSHIP_REQUESTED',
        jsonb_build_object('request_id', v_req_id, 'code', v_inv.project_code, 'designation', p_designation)
    );

    RETURN jsonb_build_object(
        'request_id', v_req_id,
        'project_id', v_inv.project_id,
        'project_name', v_proj.project_name,
        'status', 'PENDING'
    );
END;
$$;

-- 3. PMC Review of Registration Request (blocks self-approval, circular approval, and unauthorized PMC promotion)
CREATE OR REPLACE FUNCTION public.review_registration_request(
    p_request_id UUID,
    p_decision TEXT,
    p_assigned_role TEXT DEFAULT 'SITE_ENGINEER',
    p_verify_org BOOLEAN DEFAULT TRUE,
    p_remarks TEXT DEFAULT ''
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_req RECORD;
    v_actor_role TEXT;
BEGIN
    SELECT * INTO v_req FROM public.registration_requests WHERE id = p_request_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Registration request not found.';
    END IF;

    IF v_req.user_id = v_actor THEN
        RAISE EXCEPTION 'Self-approval is strictly prohibited.';
    END IF;

    SELECT assigned_role INTO v_actor_role
    FROM public.project_members
    WHERE project_id = v_req.project_id AND user_id = v_actor AND approval_status = 'APPROVED';

    IF NOT public.ctecs_is_system_admin(v_actor)
       AND (v_actor_role IS NULL OR v_actor_role NOT IN ('PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR')) THEN
        RAISE EXCEPTION 'Only an approved PMC Representative or Project Administrator can review registrations.';
    END IF;

    -- Prevent circular approval (cannot approve someone who approved you)
    IF EXISTS (
        SELECT 1 FROM public.project_members
        WHERE project_id = v_req.project_id AND user_id = v_actor AND approved_by = v_req.user_id
    ) THEN
        RAISE EXCEPTION 'Circular approval is prohibited.';
    END IF;

    -- Assigning PMC/Admin roles requires existing PMC Admin / Project Admin / System Admin
    IF p_decision = 'APPROVED' AND p_assigned_role IN ('PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR') THEN
        IF NOT public.ctecs_is_system_admin(v_actor) AND v_actor_role NOT IN ('PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR') THEN
            RAISE EXCEPTION 'Insufficient authority to assign PMC or Administrator role.';
        END IF;
    END IF;

    UPDATE public.registration_requests
    SET status = p_decision,
        assigned_role = CASE WHEN p_decision = 'APPROVED' THEN p_assigned_role ELSE assigned_role END,
        organization_verified = p_verify_org,
        pmc_remarks = p_remarks,
        reviewed_by = v_actor,
        reviewed_at = NOW(),
        updated_at = NOW()
    WHERE id = p_request_id;

    UPDATE public.project_members
    SET approval_status = p_decision,
        assigned_role = CASE WHEN p_decision = 'APPROVED' THEN p_assigned_role ELSE assigned_role END,
        organization_verified = p_verify_org,
        approved_by = CASE WHEN p_decision = 'APPROVED' THEN v_actor ELSE approved_by END,
        approved_at = CASE WHEN p_decision = 'APPROVED' THEN NOW() ELSE approved_at END,
        remarks = p_remarks,
        updated_at = NOW()
    WHERE project_id = v_req.project_id AND user_id = v_req.user_id;

    INSERT INTO public.site_audit_logs (project_id, user_id, assigned_role, action, after_values)
    VALUES (
        v_req.project_id, v_actor, COALESCE(v_actor_role, 'SYSTEM_ADMINISTRATOR'),
        'MEMBERSHIP_' || p_decision,
        jsonb_build_object('target_user', v_req.user_id, 'assigned_role', p_assigned_role, 'remarks', p_remarks)
    );

    INSERT INTO public.user_notifications (user_id, project_id, notification_type, title, message)
    VALUES (
        v_req.user_id, v_req.project_id, 'MEMBERSHIP_' || p_decision,
        'Project Membership ' || p_decision,
        'Your membership request status is now ' || p_decision ||
        CASE WHEN p_decision = 'APPROVED' THEN ' with role ' || p_assigned_role ELSE '' END
    );

    RETURN jsonb_build_object('request_id', p_request_id, 'status', p_decision, 'assigned_role', p_assigned_role);
END;
$$;

-- 4. Submit Site Register (locks creator editing and sends to Pending PMC Approval)
CREATE OR REPLACE FUNCTION public.submit_site_register(p_register_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_reg RECORD;
BEGIN
    SELECT * INTO v_reg FROM public.site_registers WHERE id = p_register_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Register not found.'; END IF;
    IF NOT public.ctecs_can_edit_register(p_register_id, v_uid) THEN
        RAISE EXCEPTION 'You do not have permission to submit this register.';
    END IF;

    UPDATE public.site_registers
    SET status = 'PENDING_PMC_APPROVAL', submitted_at = NOW(), updated_at = NOW()
    WHERE id = p_register_id;

    INSERT INTO public.site_register_revisions (register_id, project_id, revision_number, status, is_locked, created_by)
    VALUES (v_reg.id, v_reg.project_id, v_reg.current_revision, 'PENDING_PMC_APPROVAL', FALSE, v_uid)
    ON CONFLICT (register_id, revision_number) DO UPDATE
        SET status = 'PENDING_PMC_APPROVAL', updated_at = NOW();

    INSERT INTO public.site_audit_logs (project_id, register_id, revision_number, user_id, action)
    VALUES (v_reg.project_id, v_reg.id, v_reg.current_revision, v_uid, 'REGISTER_SUBMITTED');

    RETURN jsonb_build_object('register_id', v_reg.id, 'revision', v_reg.current_revision, 'status', 'PENDING_PMC_APPROVAL');
END;
$$;

-- 5. PMC Review Register (Approve -> Immutable Lock, or Reject)
CREATE OR REPLACE FUNCTION public.pmc_review_register(
    p_register_id UUID,
    p_action TEXT,
    p_remarks TEXT DEFAULT ''
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_reg RECORD;
    v_prof RECORD;
    v_new_status TEXT;
BEGIN
    IF p_action NOT IN ('APPROVED', 'REJECTED') THEN
        RAISE EXCEPTION 'Action must be APPROVED or REJECTED.';
    END IF;

    SELECT * INTO v_reg FROM public.site_registers WHERE id = p_register_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Register not found.'; END IF;

    IF NOT public.ctecs_has_project_role(v_reg.project_id, v_uid, ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR']) THEN
        RAISE EXCEPTION 'Only an approved PMC Representative can approve or reject site registers.';
    END IF;

    IF v_reg.created_by = v_uid THEN
        RAISE EXCEPTION 'Self-approval of your own site register is prohibited.';
    END IF;

    SELECT full_name, default_designation INTO v_prof FROM public.profiles WHERE id = v_uid;
    v_new_status := CASE WHEN p_action = 'APPROVED' THEN 'APPROVED_LOCKED' ELSE 'REJECTED' END;

    UPDATE public.site_registers
    SET status = v_new_status,
        approved_at = CASE WHEN p_action = 'APPROVED' THEN NOW() ELSE approved_at END,
        approved_by = CASE WHEN p_action = 'APPROVED' THEN v_uid ELSE approved_by END,
        updated_at = NOW()
    WHERE id = p_register_id;

    INSERT INTO public.site_register_revisions (
        register_id, project_id, revision_number, status, is_locked, created_by, approved_by, approved_at
    ) VALUES (
        v_reg.id, v_reg.project_id, v_reg.current_revision, v_new_status,
        (p_action = 'APPROVED'), v_reg.created_by,
        CASE WHEN p_action = 'APPROVED' THEN v_uid ELSE NULL END,
        CASE WHEN p_action = 'APPROVED' THEN NOW() ELSE NULL END
    )
    ON CONFLICT (register_id, revision_number) DO UPDATE
        SET status = v_new_status,
            is_locked = (p_action = 'APPROVED'),
            approved_by = CASE WHEN p_action = 'APPROVED' THEN v_uid ELSE site_register_revisions.approved_by END,
            approved_at = CASE WHEN p_action = 'APPROVED' THEN NOW() ELSE site_register_revisions.approved_at END,
            updated_at = NOW();

    INSERT INTO public.site_approvals (register_id, project_id, revision_number, action, pmc_user_id, pmc_name, pmc_designation, remarks)
    VALUES (v_reg.id, v_reg.project_id, v_reg.current_revision, p_action, v_uid, COALESCE(v_prof.full_name, ''), COALESCE(v_prof.default_designation, ''), p_remarks);

    INSERT INTO public.site_audit_logs (project_id, register_id, revision_number, user_id, assigned_role, action, after_values)
    VALUES (v_reg.project_id, v_reg.id, v_reg.current_revision, v_uid, 'PMC_REPRESENTATIVE', 'PMC_' || p_action, jsonb_build_object('remarks', p_remarks));

    RETURN jsonb_build_object('register_id', v_reg.id, 'revision', v_reg.current_revision, 'status', v_new_status);
END;
$$;

-- 6. PMC Grant Temporary Edit Permission (creator-only, time-bounded, non-self-granted)
CREATE OR REPLACE FUNCTION public.grant_edit_permission(
    p_edit_request_id UUID,
    p_duration_minutes INTEGER DEFAULT 120,
    p_pmc_remarks TEXT DEFAULT ''
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_req RECORD;
    v_reg RECORD;
    v_perm_id UUID;
    v_expires TIMESTAMPTZ := NOW() + make_interval(mins => GREATEST(5, LEAST(p_duration_minutes, 1440)));
BEGIN
    SELECT * INTO v_req FROM public.site_edit_requests WHERE id = p_edit_request_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Edit request not found.'; END IF;

    SELECT * INTO v_reg FROM public.site_registers WHERE id = v_req.register_id FOR UPDATE;
    IF NOT public.ctecs_has_project_role(v_reg.project_id, v_uid, ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR']) THEN
        RAISE EXCEPTION 'Only PMC can grant temporary edit permissions.';
    END IF;

    IF v_req.requested_by = v_uid THEN
        RAISE EXCEPTION 'Creator self-authorization is strictly prohibited.';
    END IF;

    IF v_req.requested_by <> v_reg.created_by THEN
        RAISE EXCEPTION 'Temporary edit permission can only be granted to the designated register creator.';
    END IF;

    UPDATE public.site_edit_requests
    SET status = 'GRANTED', reviewed_by = v_uid, reviewed_at = NOW(), pmc_remarks = p_pmc_remarks, updated_at = NOW()
    WHERE id = p_edit_request_id;

    INSERT INTO public.site_edit_permissions (
        edit_request_id, register_id, project_id, target_revision_number,
        authorized_user_id, granted_by_pmc_id, expires_at, is_active
    ) VALUES (
        p_edit_request_id, v_reg.id, v_reg.project_id, v_reg.current_revision,
        v_reg.created_by, v_uid, v_expires, TRUE
    ) RETURNING id INTO v_perm_id;

    UPDATE public.site_registers
    SET status = 'EDIT_PERMISSION_GRANTED', updated_at = NOW()
    WHERE id = v_reg.id;

    INSERT INTO public.site_audit_logs (project_id, register_id, revision_number, user_id, assigned_role, action, after_values)
    VALUES (v_reg.project_id, v_reg.id, v_reg.current_revision, v_uid, 'PMC_REPRESENTATIVE', 'EDIT_PERMISSION_GRANTED',
            jsonb_build_object('permission_id', v_perm_id, 'authorized_user', v_reg.created_by, 'expires_at', v_expires));

    RETURN jsonb_build_object('permission_id', v_perm_id, 'expires_at', v_expires, 'status', 'EDIT_PERMISSION_GRANTED');
END;
$$;

-- 7. PMC Revoke Temporary Edit Permission (immediately terminates editing)
CREATE OR REPLACE FUNCTION public.revoke_edit_permission(p_permission_id UUID, p_reason TEXT DEFAULT '')
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_perm RECORD;
BEGIN
    SELECT * INTO v_perm FROM public.site_edit_permissions WHERE id = p_permission_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Permission not found.'; END IF;
    IF NOT public.ctecs_has_project_role(v_perm.project_id, v_uid, ARRAY['PMC_REPRESENTATIVE', 'PROJECT_ADMINISTRATOR']) THEN
        RAISE EXCEPTION 'Only PMC can revoke temporary edit permissions.';
    END IF;

    UPDATE public.site_edit_permissions
    SET is_active = FALSE, revoked_at = NOW(), revoked_by = v_uid
    WHERE id = p_permission_id;

    UPDATE public.site_edit_requests
    SET status = 'REVOKED', pmc_remarks = p_reason, updated_at = NOW()
    WHERE id = v_perm.edit_request_id;

    UPDATE public.site_registers
    SET status = 'APPROVED_LOCKED', updated_at = NOW()
    WHERE id = v_perm.register_id AND status IN ('EDIT_PERMISSION_GRANTED', 'REOPENED');

    INSERT INTO public.site_audit_logs (project_id, register_id, revision_number, user_id, assigned_role, action, after_values)
    VALUES (v_perm.project_id, v_perm.register_id, v_perm.target_revision_number, v_uid, 'PMC_REPRESENTATIVE', 'EDIT_PERMISSION_REVOKED', jsonb_build_object('reason', p_reason));

    RETURN jsonb_build_object('permission_id', p_permission_id, 'status', 'APPROVED_LOCKED');
END;
$$;

-- 8. Submit Corrected Revision (creates Revision N+1, preserves Revision N immutable, revokes temp permission immediately)
CREATE OR REPLACE FUNCTION public.submit_corrected_revision(
    p_register_id UUID,
    p_change_summary TEXT,
    p_snapshot JSONB DEFAULT '{}'::JSONB
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_reg RECORD;
    v_perm RECORD;
    v_next_rev INTEGER;
BEGIN
    SELECT * INTO v_reg FROM public.site_registers WHERE id = p_register_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Register not found.'; END IF;

    IF v_reg.created_by <> v_uid THEN
        RAISE EXCEPTION 'Only the authorized creator can submit a corrected revision.';
    END IF;

    SELECT * INTO v_perm
    FROM public.site_edit_permissions
    WHERE register_id = v_reg.id
      AND target_revision_number = v_reg.current_revision
      AND authorized_user_id = v_uid
      AND is_active = TRUE
      AND revoked_at IS NULL
      AND consumed_at IS NULL
      AND expires_at > NOW()
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'No active, non-expired PMC edit permission exists for this revision.';
    END IF;

    v_next_rev := v_reg.current_revision + 1;

    -- Immediately consume & revoke temporary permission
    UPDATE public.site_edit_permissions
    SET is_active = FALSE, consumed_at = NOW(), revoked_at = NOW()
    WHERE id = v_perm.id;

    UPDATE public.site_edit_requests
    SET status = 'CONSUMED', updated_at = NOW()
    WHERE id = v_perm.edit_request_id;

    -- Insert new revision N+1 (previous approved revision N remains untouched & locked)
    INSERT INTO public.site_register_revisions (
        register_id, project_id, revision_number, status, is_locked, snapshot_payload, change_summary, created_by
    ) VALUES (
        v_reg.id, v_reg.project_id, v_next_rev, 'PENDING_PMC_APPROVAL', FALSE, p_snapshot, p_change_summary, v_uid
    );

    UPDATE public.site_registers
    SET current_revision = v_next_rev, status = 'PENDING_PMC_APPROVAL', submitted_at = NOW(), updated_at = NOW()
    WHERE id = v_reg.id;

    INSERT INTO public.site_audit_logs (project_id, register_id, revision_number, user_id, action, after_values)
    VALUES (v_reg.project_id, v_reg.id, v_next_rev, v_uid, 'CORRECTED_REVISION_RESUBMITTED',
            jsonb_build_object('previous_revision', v_reg.current_revision, 'new_revision', v_next_rev, 'summary', p_change_summary));

    RETURN jsonb_build_object('register_id', v_reg.id, 'new_revision', v_next_rev, 'status', 'PENDING_PMC_APPROVAL');
END;
$$;
