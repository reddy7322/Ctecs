-- ============================================================================
-- CTECS Digital Highway Site Register & Construction Quality Management System
-- Migration 004: Supabase Storage Buckets, Storage RLS & Realtime Publication
-- ============================================================================

INSERT INTO storage.buckets (id, name, public)
VALUES
    ('site-attachments', 'site-attachments', FALSE),
    ('site-signatures', 'site-signatures', FALSE),
    ('site-reports', 'site-reports', FALSE)
ON CONFLICT (id) DO NOTHING;

-- Project-scoped storage policies: path convention = <project_id>/<register_or_entity_id>/<filename>
CREATE POLICY storage_site_objects_select ON storage.objects FOR SELECT
    USING (
        bucket_id IN ('site-attachments', 'site-signatures', 'site-reports')
        AND public.ctecs_is_approved_member((storage.foldername(name))[1]::UUID, auth.uid())
    );

CREATE POLICY storage_site_objects_insert ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id IN ('site-attachments', 'site-signatures', 'site-reports')
        AND public.ctecs_is_approved_member((storage.foldername(name))[1]::UUID, auth.uid())
    );

-- Enable Supabase Realtime replication for live multi-user monitoring tables
DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'site_registers',
        'site_register_revisions',
        'site_register_entries',
        'site_measurements',
        'site_segments',
        'glass_grid_entries',
        'tackcoat_tests',
        'tackcoat_test_measurements',
        'quality_test_reports',
        'site_observations',
        'site_rectifications',
        'site_attachments',
        'site_signatures',
        'site_edit_requests',
        'site_edit_permissions',
        'site_approvals',
        'registration_requests',
        'user_notifications'
    ]
    LOOP
        BEGIN
            EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', tbl);
        EXCEPTION WHEN duplicate_object THEN
            NULL;
        END;
    END LOOP;
END $$;
