DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'syfosmmanuell-backend-instance')
        THEN
            ALTER USER "syfosmmanuell-backend-instance" IN DATABASE "syfosmmanuell-backend" SET pgaudit.log TO 'none';
        END IF;
    END
$$;
