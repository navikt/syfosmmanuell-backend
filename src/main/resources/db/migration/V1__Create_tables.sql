CREATE TABLE manuelloppgave (
  id CHAR(64) PRIMARY KEY,
  receivedsykmelding jsonb NOT NULL,
  validationresult jsonb NOT NULL,
  apprec jsonb NOT NULL
);