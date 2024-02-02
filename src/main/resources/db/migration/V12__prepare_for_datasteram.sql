alter user "syfosmmanuell-backend-instance" with replication;
alter user "datastream_syfosmmanuell-user" with replication;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO "datastream_syfosmmanuell-user";
GRANT USAGE ON SCHEMA public TO "datastream_syfosmmanuell-user";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO "datastream_syfosmmanuell-user";

