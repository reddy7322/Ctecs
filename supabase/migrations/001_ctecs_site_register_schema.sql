-- ============================================================================
-- CTECS Digital Highway Site Register & Construction Quality Management System
-- Migration 001: Complete Relational Schema (All 28 Required Tables + Indexes)
-- Target Supabase Project: Ctecs (https://ytrolykqyjqwjwtifxmi.supabase.co)
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Shared updated_at trigger helper
CREATE OR REPLACE FUNCTION public.ctecs_set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

-- 1. profiles (linked 1:1 with auth.users; never stores privileged project roles directly)
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name TEXT NOT NULL,
    username TEXT NOT NULL UNIQUE,
    email TEXT NOT NULL UNIQUE,
    mobile_number TEXT NOT NULL DEFAULT '',
    default_designation TEXT NOT NULL DEFAULT '',
    default_organization_type TEXT NOT NULL DEFAULT 'CONTRACTOR'
        CHECK (default_organization_type IN ('CONTRACTOR', 'PMC', 'IE', 'CONCESSIONAIRE', 'CLIENT')),
    default_organization_name TEXT NOT NULL DEFAULT '',
    is_system_admin BOOLEAN NOT NULL DEFAULT FALSE,
    account_status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (account_status IN ('ACTIVE', 'INCOMPLETE', 'SUSPENDED', 'DEACTIVATED')),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_profiles_username_lower ON public.profiles (LOWER(username));
CREATE INDEX IF NOT EXISTS idx_profiles_email_lower ON public.profiles (LOWER(email));

-- 2. projects
CREATE TABLE IF NOT EXISTS public.projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_code_prefix TEXT NOT NULL DEFAULT 'CTECS',
    project_name TEXT NOT NULL,
    highway_name TEXT NOT NULL DEFAULT '',
    nh_sh_number TEXT NOT NULL DEFAULT '',
    package_number TEXT NOT NULL DEFAULT '',
    project_location TEXT NOT NULL DEFAULT '',
    state_name TEXT NOT NULL DEFAULT '',
    contractor_name TEXT NOT NULL DEFAULT '',
    concessionaire_name TEXT NOT NULL DEFAULT '',
    client_name TEXT NOT NULL DEFAULT '',
    pmc_organization TEXT NOT NULL DEFAULT '',
    ie_organization TEXT NOT NULL DEFAULT '',
    assigned_pmc_name TEXT NOT NULL DEFAULT '',
    assigned_pmc_contact TEXT NOT NULL DEFAULT '',
    project_identification TEXT NOT NULL DEFAULT '',
    project_status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (project_status IN ('ACTIVE', 'ON_HOLD', 'COMPLETED', 'ARCHIVED')),
    registration_status TEXT NOT NULL DEFAULT 'OPEN'
        CHECK (registration_status IN ('OPEN', 'CLOSED', 'INVITE_ONLY')),
    permitted_organization_types TEXT[] NOT NULL DEFAULT ARRAY['CONTRACTOR', 'PMC', 'IE', 'CONCESSIONAIRE', 'CLIENT'],
    allowed_designations JSONB NOT NULL DEFAULT '{}'::JSONB,
    organization_directory JSONB NOT NULL DEFAULT '{}'::JSONB,
    project_settings JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_projects_status ON public.projects (project_status);

-- 3. project_members (independent role, org_type, designation, status per project)
CREATE TABLE IF NOT EXISTS public.project_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    organization_type TEXT NOT NULL
        CHECK (organization_type IN ('CONTRACTOR', 'PMC', 'IE', 'CONCESSIONAIRE', 'CLIENT')),
    organization_name TEXT NOT NULL DEFAULT '',
    organization_verified BOOLEAN NOT NULL DEFAULT FALSE,
    designation TEXT NOT NULL DEFAULT '',
    assigned_role TEXT NOT NULL DEFAULT 'CLIENT_VIEWER'
        CHECK (assigned_role IN (
            'SITE_ENGINEER', 'SITE_SUPERVISOR', 'CONTRACTOR_SITE_ENGINEER',
            'CONTRACTOR_REPRESENTATIVE', 'IE_REPRESENTATIVE', 'PMC_REPRESENTATIVE',
            'PROJECT_MANAGER', 'CLIENT_VIEWER', 'PROJECT_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR'
        )),
    approval_status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED', 'REVOKED', 'INFO_REQUESTED')),
    project_code_used TEXT NOT NULL DEFAULT '',
    approved_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    approved_at TIMESTAMPTZ,
    remarks TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (project_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_project_members_user ON public.project_members (user_id, approval_status);
CREATE INDEX IF NOT EXISTS idx_project_members_project ON public.project_members (project_id, approval_status, assigned_role);

-- 4. contractors
CREATE TABLE IF NOT EXISTS public.contractors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    contractor_name TEXT NOT NULL,
    organization_type TEXT NOT NULL DEFAULT 'CONTRACTOR'
        CHECK (organization_type IN ('CONTRACTOR', 'PMC', 'IE', 'CONCESSIONAIRE', 'CLIENT')),
    contact_person TEXT NOT NULL DEFAULT '',
    contact_phone TEXT NOT NULL DEFAULT '',
    contact_email TEXT NOT NULL DEFAULT '',
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (project_id, contractor_name)
);

-- 5. project_invitations (PMC-assigned Dynamic Project Codes)
CREATE TABLE IF NOT EXISTS public.project_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    project_code TEXT NOT NULL UNIQUE,
    permitted_organization_types TEXT[] NOT NULL DEFAULT ARRAY['CONTRACTOR', 'PMC', 'IE', 'CONCESSIONAIRE', 'CLIENT'],
    registration_restrictions JSONB NOT NULL DEFAULT '{}'::JSONB,
    max_registrations INTEGER,
    current_registrations INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMPTZ,
    revoked_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    created_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_project_invitations_code ON public.project_invitations (UPPER(project_code));
CREATE INDEX IF NOT EXISTS idx_project_invitations_project ON public.project_invitations (project_id, is_enabled, is_revoked);

-- 6. registration_requests
CREATE TABLE IF NOT EXISTS public.registration_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    invitation_id UUID REFERENCES public.project_invitations(id) ON DELETE SET NULL,
    project_code_used TEXT NOT NULL,
    full_name TEXT NOT NULL,
    username TEXT NOT NULL,
    email TEXT NOT NULL,
    mobile_number TEXT NOT NULL DEFAULT '',
    requested_organization_type TEXT NOT NULL
        CHECK (requested_organization_type IN ('CONTRACTOR', 'PMC', 'IE', 'CONCESSIONAIRE', 'CLIENT')),
    requested_organization_name TEXT NOT NULL DEFAULT '',
    is_unlisted_organization BOOLEAN NOT NULL DEFAULT FALSE,
    organization_verified BOOLEAN NOT NULL DEFAULT FALSE,
    requested_designation TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'INFO_REQUESTED', 'SUSPENDED', 'REVOKED')),
    assigned_role TEXT,
    pmc_remarks TEXT NOT NULL DEFAULT '',
    reviewed_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_registration_requests_project_status ON public.registration_requests (project_id, status);
CREATE INDEX IF NOT EXISTS idx_registration_requests_user ON public.registration_requests (user_id);

-- 7. highway_activities (41 standard activities + project custom templates)
CREATE TABLE IF NOT EXISTS public.highway_activities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES public.projects(id) ON DELETE CASCADE,
    activity_code TEXT NOT NULL,
    activity_name TEXT NOT NULL,
    category TEXT NOT NULL
        CHECK (category IN (
            'BITUMINOUS_WORKS', 'PAVEMENT_PREPARATION', 'EARTHWORK_GRANULAR',
            'CONCRETE_STRUCTURES', 'ROAD_SAFETY_FINISHING', 'CUSTOM'
        )),
    default_unit TEXT NOT NULL DEFAULT 'm3',
    supports_vehicle_laying BOOLEAN NOT NULL DEFAULT FALSE,
    supports_cross_section BOOLEAN NOT NULL DEFAULT FALSE,
    supports_glass_grid BOOLEAN NOT NULL DEFAULT FALSE,
    supports_tack_coat_test BOOLEAN NOT NULL DEFAULT FALSE,
    is_custom BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (project_id, activity_code)
);

-- 8. activity_measurement_templates (versioned so template edits never alter historical records)
CREATE TABLE IF NOT EXISTS public.activity_measurement_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES public.projects(id) ON DELETE CASCADE,
    activity_id UUID REFERENCES public.highway_activities(id) ON DELETE CASCADE,
    activity_code TEXT NOT NULL,
    template_version INTEGER NOT NULL DEFAULT 1,
    field_schema JSONB NOT NULL DEFAULT '[]'::JSONB,
    calculation_method TEXT NOT NULL DEFAULT 'UNIFORM_SECTION',
    verified_density NUMERIC(10, 4),
    density_unit TEXT,
    spec_reference TEXT NOT NULL DEFAULT '',
    tolerance_config JSONB NOT NULL DEFAULT '{}'::JSONB,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 9. site_registers
CREATE TABLE IF NOT EXISTS public.site_registers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    contractor_id UUID REFERENCES public.contractors(id) ON DELETE SET NULL,
    contractor_name TEXT NOT NULL DEFAULT '',
    activity_code TEXT NOT NULL,
    activity_name TEXT NOT NULL,
    activity_category TEXT NOT NULL,
    template_version INTEGER NOT NULL DEFAULT 1,
    register_number TEXT NOT NULL,
    work_date DATE NOT NULL,
    shift TEXT NOT NULL DEFAULT 'DAY' CHECK (shift IN ('DAY', 'NIGHT')),
    highway_name TEXT NOT NULL DEFAULT '',
    package_location TEXT NOT NULL DEFAULT '',
    side TEXT NOT NULL DEFAULT 'RHS',
    lane TEXT NOT NULL DEFAULT 'MCW',
    chainage_from TEXT NOT NULL DEFAULT '',
    chainage_to TEXT NOT NULL DEFAULT '',
    current_revision INTEGER NOT NULL DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN (
            'DRAFT', 'SUBMITTED', 'PENDING_PMC_APPROVAL', 'APPROVED_LOCKED',
            'EDIT_PERMISSION_REQUESTED', 'EDIT_PERMISSION_GRANTED', 'EDIT_PERMISSION_EXPIRED',
            'REJECTED', 'REOPENED', 'RESUBMITTED'
        )),
    total_length_m NUMERIC(14, 4) NOT NULL DEFAULT 0,
    total_area_sqm NUMERIC(14, 4) NOT NULL DEFAULT 0,
    total_volume_cum NUMERIC(14, 4) NOT NULL DEFAULT 0,
    total_material_qty NUMERIC(14, 4),
    total_material_unit TEXT NOT NULL DEFAULT 'MT',
    quantity_classification TEXT NOT NULL DEFAULT 'MEASURED'
        CHECK (quantity_classification IN ('ESTIMATED', 'MEASURED', 'VERIFIED')),
    remarks TEXT NOT NULL DEFAULT '',
    created_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    submitted_at TIMESTAMPTZ,
    approved_at TIMESTAMPTZ,
    approved_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_site_registers_project_date ON public.site_registers (project_id, work_date DESC);
CREATE INDEX IF NOT EXISTS idx_site_registers_status ON public.site_registers (project_id, status);
CREATE INDEX IF NOT EXISTS idx_site_registers_creator ON public.site_registers (created_by);

-- 10. site_register_revisions (immutable snapshot per revision)
CREATE TABLE IF NOT EXISTS public.site_register_revisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    register_id UUID NOT NULL REFERENCES public.site_registers(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    is_locked BOOLEAN NOT NULL DEFAULT FALSE,
    snapshot_payload JSONB NOT NULL DEFAULT '{}'::JSONB,
    change_summary TEXT NOT NULL DEFAULT '',
    created_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    approved_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (register_id, revision_number)
);
CREATE INDEX IF NOT EXISTS idx_revisions_register ON public.site_register_revisions (register_id, revision_number DESC);

-- 11. site_register_entries (vehicle-wise laying & activity work rows)
CREATE TABLE IF NOT EXISTS public.site_register_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    register_id UUID NOT NULL REFERENCES public.site_registers(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL DEFAULT 1,
    serial_number INTEGER NOT NULL DEFAULT 1,
    entry_type TEXT NOT NULL DEFAULT 'WORK_ROW',
    vehicle_number TEXT NOT NULL DEFAULT '',
    material_type TEXT NOT NULL DEFAULT '',
    layer_name TEXT NOT NULL DEFAULT '',
    chainage_from TEXT NOT NULL DEFAULT '',
    chainage_to TEXT NOT NULL DEFAULT '',
    side TEXT NOT NULL DEFAULT 'RHS',
    lane TEXT NOT NULL DEFAULT 'MCW',
    start_time TEXT NOT NULL DEFAULT '',
    end_time TEXT NOT NULL DEFAULT '',
    laying_temp_c NUMERIC(6, 2),
    rolling_temp_c NUMERIC(6, 2),
    application_rate NUMERIC(10, 4),
    application_rate_unit TEXT,
    estimated_material_qty NUMERIC(14, 4),
    actual_material_qty NUMERIC(14, 4),
    verified_material_qty NUMERIC(14, 4),
    material_unit TEXT NOT NULL DEFAULT 'MT',
    extra_fields JSONB NOT NULL DEFAULT '{}'::JSONB,
    remarks TEXT NOT NULL DEFAULT '',
    created_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_entries_register_rev ON public.site_register_entries (register_id, revision_number, serial_number);

-- 12. site_measurements (centralized Design vs Actual vs Verified dimensions & explicit units)
CREATE TABLE IF NOT EXISTS public.site_measurements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    register_id UUID NOT NULL REFERENCES public.site_registers(id) ON DELETE CASCADE,
    entry_id UUID REFERENCES public.site_register_entries(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL DEFAULT 1,
    measurement_method TEXT NOT NULL DEFAULT 'UNIFORM_SECTION'
        CHECK (measurement_method IN ('UNIFORM_SECTION', 'AVERAGE_END_AREA', 'IRREGULAR_SURVEYED', 'STRUCTURAL_UNITS')),
    chainage_from TEXT NOT NULL DEFAULT '',
    chainage_to TEXT NOT NULL DEFAULT '',
    chainage_diff_m NUMERIC(14, 4),
    actual_length_m NUMERIC(14, 4),
    additional_length_m NUMERIC(14, 4) DEFAULT 0,
    total_length_m NUMERIC(14, 4),
    length_unit TEXT NOT NULL DEFAULT 'm',
    start_width NUMERIC(12, 4),
    middle_width NUMERIC(12, 4),
    end_width NUMERIC(12, 4),
    average_width NUMERIC(12, 4),
    actual_width NUMERIC(12, 4),
    left_offset NUMERIC(12, 4),
    right_offset NUMERIC(12, 4),
    formation_width NUMERIC(12, 4),
    top_width NUMERIC(12, 4),
    bottom_width NUMERIC(12, 4),
    width_unit TEXT NOT NULL DEFAULT 'm',
    design_depth NUMERIC(12, 4),
    actual_depth NUMERIC(12, 4),
    start_depth NUMERIC(12, 4),
    middle_depth NUMERIC(12, 4),
    end_depth NUMERIC(12, 4),
    average_depth NUMERIC(12, 4),
    loose_thickness NUMERIC(12, 4),
    compacted_thickness NUMERIC(12, 4),
    verified_depth NUMERIC(12, 4),
    depth_unit TEXT NOT NULL DEFAULT 'mm',
    existing_level NUMERIC(12, 4),
    proposed_level NUMERIC(12, 4),
    formation_level NUMERIC(12, 4),
    cross_sectional_area_1 NUMERIC(14, 4),
    cross_sectional_area_2 NUMERIC(14, 4),
    design_area NUMERIC(14, 4),
    actual_area NUMERIC(14, 4),
    verified_area NUMERIC(14, 4),
    area_unit TEXT NOT NULL DEFAULT 'm2',
    design_volume NUMERIC(14, 4),
    actual_volume NUMERIC(14, 4),
    verified_volume NUMERIC(14, 4),
    volume_unit TEXT NOT NULL DEFAULT 'm3',
    verified_density NUMERIC(10, 4),
    density_unit TEXT,
    calculated_quantity NUMERIC(14, 4),
    quantity_unit TEXT,
    quantity_classification TEXT NOT NULL DEFAULT 'MEASURED'
        CHECK (quantity_classification IN ('ESTIMATED', 'MEASURED', 'VERIFIED')),
    variance_value NUMERIC(14, 4),
    variance_percent NUMERIC(10, 4),
    formula_breakdown TEXT NOT NULL DEFAULT '',
    created_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_measurements_register_rev ON public.site_measurements (register_id, revision_number);

-- 13. site_segments (multi-segment chainage breakdown with non-overlap support)
CREATE TABLE IF NOT EXISTS public.site_segments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    register_id UUID NOT NULL REFERENCES public.site_registers(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL DEFAULT 1,
    segment_index INTEGER NOT NULL DEFAULT 1,
    from_chainage TEXT NOT NULL,
    to_chainage TEXT NOT NULL,
    from_chainage_m NUMERIC(14, 4) NOT NULL,
    to_chainage_m NUMERIC(14, 4) NOT NULL,
    side TEXT NOT NULL DEFAULT 'RHS',
    lane TEXT NOT NULL DEFAULT 'MCW',
    length_m NUMERIC(14, 4) NOT NULL,
    width_m NUMERIC(12, 4) NOT NULL,
    design_thickness NUMERIC(12, 4),
    actual_thickness NUMERIC(12, 4),
    verified_thickness NUMERIC(12, 4),
    thickness_unit TEXT NOT NULL DEFAULT 'mm',
    area_sqm NUMERIC(14, 4) NOT NULL DEFAULT 0,
    volume_cum NUMERIC(14, 4),
    material_qty NUMERIC(14, 4),
    material_unit TEXT,
    has_overlap_warning BOOLEAN NOT NULL DEFAULT FALSE,
    remarks TEXT NOT NULL DEFAULT '',
    created_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_segments_register_rev ON public.site_segments (register_id, revision_number, segment_index);

-- 14. glass_grid_entries
CREATE TABLE IF NOT EXISTS public.glass_grid_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    register_id UUID NOT NULL REFERENCES public.site_registers(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL DEFAULT 1,
    from_chainage TEXT NOT NULL,
    to_chainage TEXT NOT NULL,
    application_length_m NUMERIC(14, 4) NOT NULL,
    application_width_m NUMERIC(12, 4) NOT NULL,
    roll_width_m NUMERIC(10, 4) NOT NULL,
    number_of_rolls INTEGER NOT NULL DEFAULT 1,
    overlap_width_m NUMERIC(10, 4) NOT NULL DEFAULT 0,
    effective_covered_width_m NUMERIC(12, 4) NOT NULL,
    total_application_area_sqm NUMERIC(14, 4) NOT NULL,
    application_side TEXT NOT NULL DEFAULT 'RHS',
    start_time TEXT NOT NULL DEFAULT '',
    end_time TEXT NOT NULL DEFAULT '',
    surface_preparation TEXT NOT NULL DEFAULT '',
    tack_coat_status TEXT NOT NULL DEFAULT '',
    ptr_passes INTEGER,
    wrinkles_alignment TEXT NOT NULL DEFAULT '',
    remarks TEXT NOT NULL DEFAULT '',
    created_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 15. tackcoat_tests
CREATE TABLE IF NOT EXISTS public.tackcoat_tests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    register_id UUID REFERENCES public.site_registers(id) ON DELETE SET NULL,
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL DEFAULT 1,
    contractor_name TEXT NOT NULL DEFAULT '',
    independent_engineer TEXT NOT NULL DEFAULT '',
    pmc_consultant TEXT NOT NULL DEFAULT '',
    project_manager TEXT NOT NULL DEFAULT '',
    location TEXT NOT NULL DEFAULT '',
    chainage TEXT NOT NULL DEFAULT '',
    side TEXT NOT NULL DEFAULT 'RHS',
    material_type TEXT NOT NULL DEFAULT '',
    purpose TEXT NOT NULL DEFAULT '',
    test_date DATE NOT NULL,
    weather TEXT NOT NULL DEFAULT '',
    tested_by TEXT NOT NULL DEFAULT '',
    temperature_c NUMERIC(6, 2),
    spec_min_rate_kg_sqm NUMERIC(10, 4),
    spec_max_rate_kg_sqm NUMERIC(10, 4),
    spec_reference TEXT NOT NULL DEFAULT '',
    average_spray_rate_kg_sqm NUMERIC(10, 4),
    overall_result TEXT NOT NULL DEFAULT 'NEEDS_REVIEW'
        CHECK (overall_result IN ('PASS', 'FAIL', 'NEEDS_REVIEW')),
    remarks TEXT NOT NULL DEFAULT '',
    created_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 16. tackcoat_test_measurements
CREATE TABLE IF NOT EXISTS public.tackcoat_test_measurements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_id UUID NOT NULL REFERENCES public.tackcoat_tests(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    tray_number INTEGER NOT NULL,
    tray_area_sqm NUMERIC(10, 5) NOT NULL CHECK (tray_area_sqm > 0),
    weight_before_kg NUMERIC(10, 5) NOT NULL CHECK (weight_before_kg >= 0),
    weight_after_kg NUMERIC(10, 5) NOT NULL CHECK (weight_after_kg >= 0),
    bitumen_weight_kg NUMERIC(10, 5) NOT NULL,
    spray_rate_kg_sqm NUMERIC(10, 5) NOT NULL,
    spec_limit_text TEXT NOT NULL DEFAULT '',
    tray_result TEXT NOT NULL DEFAULT 'NEEDS_REVIEW'
        CHECK (tray_result IN ('PASS', 'FAIL', 'NEEDS_REVIEW')),
    remarks TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (test_id, tray_number)
);

-- 17. quality_test_reports
CREATE TABLE IF NOT EXISTS public.quality_test_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    register_id UUID REFERENCES public.site_registers(id) ON DELETE SET NULL,
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL DEFAULT 1,
    test_category TEXT NOT NULL,
    test_title TEXT NOT NULL,
    test_date DATE NOT NULL,
    chainage TEXT NOT NULL DEFAULT '',
    side TEXT NOT NULL DEFAULT 'RHS',
    sample_location TEXT NOT NULL DEFAULT '',
    tested_by TEXT NOT NULL DEFAULT '',
    measured_values JSONB NOT NULL DEFAULT '{}'::JSONB,
    spec_criteria TEXT NOT NULL DEFAULT '',
    result_status TEXT NOT NULL DEFAULT 'NEEDS_REVIEW'
        CHECK (result_status IN ('PASS', 'FAIL', 'NEEDS_REVIEW')),
    ocr_extracted_text TEXT NOT NULL DEFAULT '',
    ocr_confidence NUMERIC(5, 4),
    ocr_verified_manually BOOLEAN NOT NULL DEFAULT FALSE,
    remarks TEXT NOT NULL DEFAULT '',
    created_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 18. site_observations
CREATE TABLE IF NOT EXISTS public.site_observations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    register_id UUID REFERENCES public.site_registers(id) ON DELETE SET NULL,
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    serial_number INTEGER NOT NULL DEFAULT 1,
    observed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    activity_name TEXT NOT NULL DEFAULT '',
    chainage_from TEXT NOT NULL DEFAULT '',
    chainage_to TEXT NOT NULL DEFAULT '',
    side TEXT NOT NULL DEFAULT 'RHS',
    lane TEXT NOT NULL DEFAULT 'MCW',
    affected_length_m NUMERIC(12, 4),
    affected_width_m NUMERIC(12, 4),
    affected_depth_m NUMERIC(12, 4),
    affected_area_sqm NUMERIC(14, 4),
    affected_volume_cum NUMERIC(14, 4),
    category TEXT NOT NULL,
    description TEXT NOT NULL,
    severity TEXT NOT NULL DEFAULT 'MEDIUM' CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    corrective_action TEXT NOT NULL DEFAULT '',
    responsible_contractor TEXT NOT NULL DEFAULT '',
    target_date DATE,
    rectification_status TEXT NOT NULL DEFAULT 'OPEN'
        CHECK (rectification_status IN ('OPEN', 'UNDER_RECTIFICATION', 'RECTIFIED', 'VERIFIED', 'CLOSED')),
    verified_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    verification_date TIMESTAMPTZ,
    final_remarks TEXT NOT NULL DEFAULT '',
    created_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_observations_project_status ON public.site_observations (project_id, rectification_status);

-- 19. site_rectifications
CREATE TABLE IF NOT EXISTS public.site_rectifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    observation_id UUID NOT NULL REFERENCES public.site_observations(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    action_taken TEXT NOT NULL,
    rectified_length_m NUMERIC(12, 4),
    rectified_width_m NUMERIC(12, 4),
    rectified_depth_m NUMERIC(12, 4),
    rectified_area_sqm NUMERIC(14, 4),
    rectified_volume_cum NUMERIC(14, 4),
    status TEXT NOT NULL DEFAULT 'UNDER_RECTIFICATION'
        CHECK (status IN ('OPEN', 'UNDER_RECTIFICATION', 'RECTIFIED', 'VERIFIED', 'CLOSED')),
    submitted_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    verified_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    verified_at TIMESTAMPTZ,
    remarks TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 20. site_attachments (preserves original CTECS photos, EXIF, GPS, chainage, weather metadata)
CREATE TABLE IF NOT EXISTS public.site_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    register_id UUID REFERENCES public.site_registers(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL DEFAULT 1,
    test_report_id UUID REFERENCES public.quality_test_reports(id) ON DELETE CASCADE,
    tackcoat_test_id UUID REFERENCES public.tackcoat_tests(id) ON DELETE CASCADE,
    observation_id UUID REFERENCES public.site_observations(id) ON DELETE CASCADE,
    rectification_id UUID REFERENCES public.site_rectifications(id) ON DELETE CASCADE,
    attachment_kind TEXT NOT NULL DEFAULT 'EVIDENCE_PHOTO'
        CHECK (attachment_kind IN ('EVIDENCE_PHOTO', 'TEST_REPORT_PAGE', 'BEFORE_PHOTO', 'AFTER_PHOTO', 'MEASUREMENT_PHOTO', 'SIGNATURE_IMAGE')),
    storage_path TEXT NOT NULL,
    local_uri TEXT NOT NULL DEFAULT '',
    file_name TEXT NOT NULL DEFAULT '',
    mime_type TEXT NOT NULL DEFAULT 'image/jpeg',
    caption TEXT NOT NULL DEFAULT '',
    captured_chainage TEXT NOT NULL DEFAULT '',
    captured_side TEXT NOT NULL DEFAULT '',
    captured_lat DOUBLE PRECISION,
    captured_lng DOUBLE PRECISION,
    captured_weather TEXT NOT NULL DEFAULT '',
    exif_metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    uploaded_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_attachments_register ON public.site_attachments (register_id, revision_number);

-- 21. site_signatures (separate from PMC approval status)
CREATE TABLE IF NOT EXISTS public.site_signatures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    register_id UUID NOT NULL REFERENCES public.site_registers(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL,
    signer_user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    signer_name TEXT NOT NULL,
    signer_designation TEXT NOT NULL DEFAULT '',
    signer_organization TEXT NOT NULL DEFAULT '',
    signer_role TEXT NOT NULL,
    signature_svg_or_path TEXT NOT NULL,
    signature_status TEXT NOT NULL DEFAULT 'SIGNED'
        CHECK (signature_status IN ('PENDING', 'SIGNED', 'REJECTED', 'VERIFICATION_REQUIRED')),
    signed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    remarks TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (register_id, revision_number, signer_user_id, signer_role)
);

-- 22. site_sharing_codes (unique per register; NEVER grants edit/approval/admin authority)
CREATE TABLE IF NOT EXISTS public.site_sharing_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    register_id UUID NOT NULL REFERENCES public.site_registers(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    sharing_code TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMPTZ,
    authorized_user_ids UUID[] NOT NULL DEFAULT ARRAY[]::UUID[],
    created_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_sharing_codes_code ON public.site_sharing_codes (UPPER(sharing_code));

-- 23. site_edit_requests
CREATE TABLE IF NOT EXISTS public.site_edit_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    register_id UUID NOT NULL REFERENCES public.site_registers(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL,
    requested_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    reason TEXT NOT NULL,
    proposed_correction TEXT NOT NULL,
    supporting_evidence TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'GRANTED', 'REJECTED', 'EXPIRED', 'REVOKED', 'CONSUMED')),
    reviewed_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    pmc_remarks TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_edit_requests_register ON public.site_edit_requests (register_id, status);

-- 24. site_edit_permissions (strict time-bounded, creator-only, revision-exact temporary edit grant)
CREATE TABLE IF NOT EXISTS public.site_edit_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    edit_request_id UUID NOT NULL REFERENCES public.site_edit_requests(id) ON DELETE CASCADE,
    register_id UUID NOT NULL REFERENCES public.site_registers(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    target_revision_number INTEGER NOT NULL,
    authorized_user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    granted_by_pmc_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    revoked_at TIMESTAMPTZ,
    revoked_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    consumed_at TIMESTAMPTZ,
    CHECK (authorized_user_id <> granted_by_pmc_id),
    CHECK (expires_at > granted_at)
);
CREATE INDEX IF NOT EXISTS idx_edit_permissions_active
    ON public.site_edit_permissions (register_id, authorized_user_id, is_active, expires_at);

-- 25. site_approvals
CREATE TABLE IF NOT EXISTS public.site_approvals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    register_id UUID NOT NULL REFERENCES public.site_registers(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL,
    action TEXT NOT NULL CHECK (action IN ('APPROVED', 'REJECTED', 'CORRECTION_REQUESTED')),
    pmc_user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    pmc_name TEXT NOT NULL DEFAULT '',
    pmc_designation TEXT NOT NULL DEFAULT '',
    remarks TEXT NOT NULL DEFAULT '',
    decided_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_approvals_register_rev ON public.site_approvals (register_id, revision_number);

-- 26. site_audit_logs (append-only permanent audit history)
CREATE TABLE IF NOT EXISTS public.site_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES public.projects(id) ON DELETE SET NULL,
    register_id UUID REFERENCES public.site_registers(id) ON DELETE SET NULL,
    revision_number INTEGER,
    user_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    organization_type TEXT NOT NULL DEFAULT '',
    assigned_role TEXT NOT NULL DEFAULT '',
    action TEXT NOT NULL,
    before_values JSONB,
    after_values JSONB,
    metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_audit_logs_project_time ON public.site_audit_logs (project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_register_time ON public.site_audit_logs (register_id, created_at DESC);

-- 27. daily_reports
CREATE TABLE IF NOT EXISTS public.daily_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    report_date DATE NOT NULL,
    report_scope TEXT NOT NULL DEFAULT 'COMBINED_DAILY',
    report_format TEXT NOT NULL CHECK (report_format IN ('XLSX', 'PDF')),
    activity_filter TEXT NOT NULL DEFAULT 'ALL',
    file_name TEXT NOT NULL,
    storage_path TEXT NOT NULL DEFAULT '',
    included_pending_offline BOOLEAN NOT NULL DEFAULT FALSE,
    summary_metrics JSONB NOT NULL DEFAULT '{}'::JSONB,
    generated_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_daily_reports_project_date ON public.daily_reports (project_id, report_date DESC);

-- 28. user_notifications
CREATE TABLE IF NOT EXISTS public.user_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    project_id UUID REFERENCES public.projects(id) ON DELETE CASCADE,
    register_id UUID REFERENCES public.site_registers(id) ON DELETE CASCADE,
    notification_type TEXT NOT NULL,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::JSONB,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON public.user_notifications (user_id, is_read, created_at DESC);

-- Seed all 41 standard global highway activity templates (project_id IS NULL = global catalog)
INSERT INTO public.highway_activities (project_id, activity_code, activity_name, category, default_unit, supports_vehicle_laying, supports_cross_section, supports_glass_grid, supports_tack_coat_test)
VALUES
    -- A. Bituminous Works
    (NULL, 'BC_LAYING', 'BC Laying – BM / BC(WC)', 'BITUMINOUS_WORKS', 'MT', TRUE, FALSE, FALSE, FALSE),
    (NULL, 'DBM_LAYING', 'DBM Laying', 'BITUMINOUS_WORKS', 'MT', TRUE, FALSE, FALSE, FALSE),
    (NULL, 'BM_LAYING', 'BM Laying', 'BITUMINOUS_WORKS', 'MT', TRUE, FALSE, FALSE, FALSE),
    (NULL, 'SDBC_LAYING', 'SDBC Laying', 'BITUMINOUS_WORKS', 'm2', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'TACK_COAT', 'Tack Coat Application', 'BITUMINOUS_WORKS', 'm2', FALSE, FALSE, FALSE, TRUE),
    (NULL, 'PRIME_COAT', 'Prime Coat Application', 'BITUMINOUS_WORKS', 'm2', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'SURFACE_DRESSING', 'Surface Dressing', 'BITUMINOUS_WORKS', 'm2', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'BITUMINOUS_SURFACE_TREATMENT', 'Bituminous Surface Treatment', 'BITUMINOUS_WORKS', 'm2', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'MICROSURFACING', 'Microsurfacing', 'BITUMINOUS_WORKS', 'm2', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'DENSE_GRADED_BITUMINOUS_MACADAM', 'Dense Graded Bituminous Macadam', 'BITUMINOUS_WORKS', 'MT', TRUE, FALSE, FALSE, FALSE),
    -- B. Pavement Preparation and Strengthening
    (NULL, 'MILLING', 'Milling / Pavement Milling', 'PAVEMENT_PREPARATION', 'm3', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'SURFACE_PREPARATION', 'Surface Preparation', 'PAVEMENT_PREPARATION', 'm2', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'CRACK_SEALING', 'Crack Sealing', 'PAVEMENT_PREPARATION', 'm', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'POTHOLE_REPAIR', 'Pothole Repair', 'PAVEMENT_PREPARATION', 'm3', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'GLASS_GRID', 'Glass Grid Application', 'PAVEMENT_PREPARATION', 'm2', FALSE, FALSE, TRUE, FALSE),
    (NULL, 'GEOGRID_GEOSYNTHETIC', 'Geogrid / Geosynthetic Application', 'PAVEMENT_PREPARATION', 'm2', FALSE, FALSE, TRUE, FALSE),
    (NULL, 'EXISTING_PAVEMENT_RECTIFICATION', 'Existing Pavement Rectification', 'PAVEMENT_PREPARATION', 'm2', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'ROAD_SHOULDER_WORK', 'Road Shoulder Work', 'PAVEMENT_PREPARATION', 'm3', FALSE, FALSE, FALSE, FALSE),
    -- C. Earthwork and Granular Works
    (NULL, 'GSB', 'GSB', 'EARTHWORK_GRANULAR', 'm3', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'WMM', 'WMM', 'EARTHWORK_GRANULAR', 'm3', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'EARTHWORK_EMBANKMENT', 'Earthwork / Embankment', 'EARTHWORK_GRANULAR', 'm3', FALSE, TRUE, FALSE, FALSE),
    (NULL, 'SUBGRADE_PREPARATION', 'Subgrade Preparation', 'EARTHWORK_GRANULAR', 'm3', FALSE, TRUE, FALSE, FALSE),
    (NULL, 'SOIL_STABILIZATION', 'Soil Stabilization', 'EARTHWORK_GRANULAR', 'm3', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'GRANULAR_SHOULDER', 'Granular Shoulder Construction', 'EARTHWORK_GRANULAR', 'm3', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'DRAINAGE_WORK', 'Drainage Work', 'EARTHWORK_GRANULAR', 'm3', FALSE, TRUE, FALSE, FALSE),
    -- D. Concrete and Structures
    (NULL, 'PQC', 'PQC', 'CONCRETE_STRUCTURES', 'm3', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'DLC', 'DLC', 'CONCRETE_STRUCTURES', 'm3', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'CONCRETE_PAVEMENT', 'Concrete Pavement', 'CONCRETE_STRUCTURES', 'm3', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'KERB_LAYING', 'Kerb Laying', 'CONCRETE_STRUCTURES', 'm', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'RCC_WORK', 'RCC Work', 'CONCRETE_STRUCTURES', 'm3', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'CULVERT_CONSTRUCTION', 'Culvert Construction', 'CONCRETE_STRUCTURES', 'm3', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'BRIDGE_WORK', 'Minor Bridge / Major Bridge', 'CONCRETE_STRUCTURES', 'm3', FALSE, TRUE, FALSE, FALSE),
    (NULL, 'RETAINING_WALL', 'Retaining Wall', 'CONCRETE_STRUCTURES', 'm3', FALSE, TRUE, FALSE, FALSE),
    (NULL, 'PROTECTION_WORK', 'Protection Work', 'CONCRETE_STRUCTURES', 'm3', FALSE, FALSE, FALSE, FALSE),
    -- E. Road Safety and Finishing
    (NULL, 'ROAD_MARKING', 'Road Marking', 'ROAD_SAFETY_FINISHING', 'm2', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'THERMOPLASTIC_MARKING', 'Thermoplastic Marking', 'ROAD_SAFETY_FINISHING', 'm2', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'ROAD_STUD_INSTALLATION', 'Road Stud Installation', 'ROAD_SAFETY_FINISHING', 'm', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'CRASH_BARRIER', 'Crash Barrier Installation', 'ROAD_SAFETY_FINISHING', 'm', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'SIGNBOARD_INSTALLATION', 'Signboard Installation', 'ROAD_SAFETY_FINISHING', 'm2', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'ROAD_FURNITURE', 'Road Furniture', 'ROAD_SAFETY_FINISHING', 'm', FALSE, FALSE, FALSE, FALSE),
    (NULL, 'FINAL_FINISHING', 'Final Finishing and Maintenance', 'ROAD_SAFETY_FINISHING', 'm2', FALSE, FALSE, FALSE, FALSE)
ON CONFLICT DO NOTHING;
