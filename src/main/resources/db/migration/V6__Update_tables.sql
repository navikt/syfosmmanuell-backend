ALTER TABLE manuelloppgave ADD COLUMN sendt_apprec boolean;
UPDATE manuelloppgave SET sendt_apprec = true WHERE ferdigstilt = true