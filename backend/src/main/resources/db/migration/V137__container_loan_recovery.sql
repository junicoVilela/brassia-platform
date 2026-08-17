-- DUV-CON-002 — o vasilhame dado como perdido que reaparece.
--
-- A PERDA NÃO É APAGADA. Ela aconteceu, a caução foi retida por causa dela, e reescrever o registro faria
-- sumir o motivo pelo qual o cliente foi cobrado. O reaparecimento é fato NOVO, com data e motivo
-- próprios — e o empréstimo passa a contar a história inteira: sumiu, cobrou-se, voltou.
ALTER TABLE container_loan ADD COLUMN recovered_at TIMESTAMPTZ;
ALTER TABLE container_loan ADD COLUMN recovery_reason VARCHAR(500);

-- Só se recupera o que se deu por perdido, e a recuperação não antecede a perda. O motivo é obrigatório:
-- "apareceu" não basta — quem lê seis meses depois precisa saber se o bar reabriu, se estava no depósito
-- do cliente, ou se voltou por engano de outra rota.
ALTER TABLE container_loan ADD CONSTRAINT ck_loan_recovery CHECK (
    (recovered_at IS NULL AND recovery_reason IS NULL)
    OR (recovered_at IS NOT NULL AND lost_at IS NOT NULL AND recovered_at >= lost_at
        AND length(btrim(coalesce(recovery_reason, ''))) > 0));

-- O índice do empréstimo aberto ignora o recuperado: um vasilhame que voltou pode ser emprestado de novo.
-- Sem isto, a recuperação devolveria o keg ao inventário e o deixaria impossível de emprestar.
DROP INDEX IF EXISTS ux_loan_open;
CREATE UNIQUE INDEX ux_loan_open ON container_loan (container_id)
    WHERE returned_at IS NULL AND lost_at IS NULL;

-- A fila de perdidos que ainda podem voltar — e a consulta que a recuperação usa.
CREATE INDEX ix_loan_lost_open ON container_loan (brewery_id, lost_at DESC)
    WHERE lost_at IS NOT NULL AND recovered_at IS NULL;
