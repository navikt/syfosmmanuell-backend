CREATE INDEX concurrently if not exists manuelloppgave_sykmelding_id_index ON MANUELLOPPGAVE ((receivedsykmelding->'sykmelding'->>'id'));

