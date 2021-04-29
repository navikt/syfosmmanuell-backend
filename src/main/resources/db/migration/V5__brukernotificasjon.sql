CREATE TABLE brukernotifikasjon (
    sykmelding_id VARCHAR PRIMARY KEY,
    event_id VARCHAR NOT NULL,
    pasient_fnr CHAR(11) NOT NULL,
    timestamp TIMESTAMP with time zone
);
