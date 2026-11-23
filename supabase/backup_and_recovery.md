# CTECS Supabase Deployment, Backup & Recovery Procedures

## Target Supabase Project
- **Project Name**: `Ctecs`
- **Project URL**: `https://ytrolykqyjqwjwtifxmi.supabase.co`
- **Android Key Config**: Supply the Supabase Publishable (anon) Key via the **Secrets panel in AI Studio** (`SUPABASE_PUBLISHABLE_KEY`). Never bundle `service_role` keys in the APK.

## Applying Migrations
```bash
supabase link --project-ref ytrolykqyjqwjwtifxmi
supabase db push
supabase functions deploy ctecs-sharing-code
```

## Automated Backup & Recovery
1. **Daily Logical Backups (pg_dump)**:
   - Schedule nightly `pg_dump --format=custom --no-owner` of the `public` schema to encrypted object storage with 30-day retention.
2. **Point-in-Time Recovery (PITR)**:
   - Enable WAL archiving / PITR in Supabase Project Settings -> Database -> Backups for RPO < 5 minutes.
3. **Storage & Attachment Integrity**:
   - All original CTECS camera photographs remain archived locally on device + uploaded to private `site-attachments` bucket with immutable EXIF/GPS/chainage metadata.
4. **Recovery Drill**:
   - Restore latest custom dump into a staging branch (`pg_restore --clean --if-exists`), run RLS verification queries ensuring `APPROVED_LOCKED` revisions reject direct `UPDATE`/`DELETE`, and verify row counts on `site_register_revisions` and `site_audit_logs`.
