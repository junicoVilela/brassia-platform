-- QLT-001-A — a frequência declarada passa a ser fiscalizada.
--
-- O DÉBITO ESTAVA REGISTRADO COM O BLOQUEIO ERRADO. Ele dizia "isso pede varredura agendada, o mesmo
-- débito aberto desde FER-004" e o critério de remoção era "existir agendador na plataforma e ligá-lo à
-- cadência do ponto". O agendador existe desde a Sprint 13 (`@Scheduled` em webhooks e relatórios): o
-- débito ficou aberto por leitura desatualizada, não por falta de ferramenta.
--
-- ALERTA, NÃO BLOQUEIO — decisão do mantenedor. Bloquear a produção por um controle atrasado pararia a
-- fábrica por causa de uma medição, e quem opera passaria a burlar a regra em vez de cumpri-la. O alerta
-- entra na central do lote, como o desvio grave da QLT-001 e a etapa atrasada da FER-004 já fazem.
--
-- POR QUE UMA TABELA, E NÃO SÓ O ALERTA. Sem memória do que já foi avisado, a varredura de hora em hora
-- avisaria de novo a cada hora sobre o mesmo atraso — e uma central que repete o mesmo aviso 24 vezes por
-- dia é uma central que ninguém lê. A chave única é (ponto, lote, janela perdida): reavisar só acontece
-- quando uma janela NOVA é perdida, que é informação nova.
CREATE TABLE quality_frequency_alert (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    point_id UUID NOT NULL REFERENCES quality_control_point (id) ON DELETE CASCADE,
    batch_id UUID NOT NULL REFERENCES production_batch (id),
    -- O instante em que a medição deveria ter acontecido. Derivado: última medição do ponto no lote mais
    -- o intervalo, ou o início do lote mais o intervalo quando ainda não houve medição nenhuma.
    missed_window_at TIMESTAMPTZ NOT NULL,
    alerted_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_quality_frequency_alert UNIQUE (point_id, batch_id, missed_window_at)
);

CREATE INDEX ix_quality_frequency_alert_batch
    ON quality_frequency_alert (brewery_id, batch_id, alerted_at DESC);
