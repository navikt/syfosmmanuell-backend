ALTER TABLE manuelloppgave ALTER COLUMN id TYPE varchar USING trim(id);
ALTER TABLE manuelloppgave ALTER COLUMN pasientfnr TYPE varchar USING trim(pasientfnr);