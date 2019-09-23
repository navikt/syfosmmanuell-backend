CREATE TABLE manuelloppgave (
  id CHAR(64) PRIMARY KEY,
  receivedsykmelding jsonb NOT NULL,
  validationresult jsonb NOT NULL,
  apprec jsonb NOT NULL,
  pasientfnr CHAR(11) NOT NULL,
  tildeltenhetsnr CHAR(11) NOT NULL,
  ferdigstilt boolean NOT NULL
);