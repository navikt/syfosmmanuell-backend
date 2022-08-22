DO
$$
BEGIN
        IF EXISTS
            (SELECT 1 FROM pg_user where usename = 'macgyver')
        THEN
            GRANT SELECT ON ALL TABLES IN SCHEMA PUBLIC TO "macgyver";
END IF;
END
$$;