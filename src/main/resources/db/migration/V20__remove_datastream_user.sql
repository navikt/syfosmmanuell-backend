-- Run as the role that granted default privileges in V12.
-- Rollback after commit requires recreating the user and restoring its grants.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_roles
        WHERE rolname = 'datastream_syfosmmanuell-user'
    ) THEN
        ALTER DEFAULT PRIVILEGES IN SCHEMA public
            REVOKE SELECT ON TABLES
            FROM "datastream_syfosmmanuell-user";

        REVOKE SELECT ON ALL TABLES IN SCHEMA public
            FROM "datastream_syfosmmanuell-user";

        REVOKE USAGE ON SCHEMA public
            FROM "datastream_syfosmmanuell-user";

        DROP ROLE "datastream_syfosmmanuell-user";
    END IF;
END
$$;
