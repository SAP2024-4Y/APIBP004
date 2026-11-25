-- Compatibility entry point for psql: execute the SAME authoritative migration.
-- Usage from any directory: psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f /path/to/database/jobportal.sql
-- Use only with an EMPTY database. No CREATE DATABASE, DROP, or automatic reset occurs.
-- Recommended: let the application run Flyway instead (see docs/database-and-demo-data.md).
-- If using this manual bootstrap, explicitly baseline Flyway at version 1 on its FIRST startup.
\set ON_ERROR_STOP on
BEGIN;
\ir ../backend/src/main/resources/db/migration/V1__job_portal_schema.sql
COMMIT;
