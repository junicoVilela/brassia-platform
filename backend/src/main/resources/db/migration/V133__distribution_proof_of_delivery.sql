-- LOG-002 — prova de entrega e coleta.
--
-- ISTO NÃO SE EDITA. É o critério transversal da sprint: todo movimento é append-only e corrige por
-- evento compensatório. Uma prova de entrega reescrita é a pior espécie de registro — ela PARECE original
-- e diz outra coisa, e ninguém consegue mais saber o que o entregador anotou às dez da manhã.
CREATE TABLE distribution_proof (
    id UUID PRIMARY KEY,
    stop_id UUID NOT NULL REFERENCES distribution_load_stop (id) ON DELETE CASCADE,
    outcome VARCHAR(12) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_by UUID NOT NULL REFERENCES security_user (id),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    note VARCHAR(1000),
    -- A janela era COMPROMISSO; perdê-la é fato a explicar depois, e não motivo para recusar a entrega
    -- que o cliente aceitou.
    outside_window BOOLEAN NOT NULL DEFAULT FALSE,

    -- ASSINATURA/FOTO SÓ COM CONSENTIMENTO. Guarda a chave do arquivo, e não o arquivo. O CHECK abaixo
    -- torna impossível uma mídia sem quem consentiu, quando e PARA QUÊ — sem finalidade escrita, o
    -- consentimento vira cheque em branco.
    media_kind VARCHAR(10),
    media_key VARCHAR(500),
    media_consented_by VARCHAR(160),
    media_consented_at TIMESTAMPTZ,
    media_purpose VARCHAR(200),

    -- GEOLOCALIZAÇÃO MINIMIZADA: três casas decimais, ~100 m. O bastante para confirmar o endereço e
    -- insuficiente para dizer em que ponto da calçada alguém parou. A coordenada cheia é arredondada na
    -- fronteira e não é guardada em lugar nenhum — dado que não existe não vaza. A precisão está no tipo,
    -- e não numa convenção: NUMERIC(6,3) não consegue guardar mais casas nem que alguém tente.
    latitude NUMERIC(6, 3),
    longitude NUMERIC(6, 3),

    -- A correção aponta para a original. As duas ficam, e o caminho até a última palavra continua
    -- legível — que é o que separa uma correção de um encobrimento.
    corrects_proof_id UUID REFERENCES distribution_proof (id),

    CONSTRAINT ck_proof_outcome CHECK (outcome IN ('DELIVERED', 'PARTIAL', 'REFUSED', 'ABSENT',
                                                   'RESCHEDULED')),
    CONSTRAINT ck_proof_media_kind CHECK (media_kind IS NULL OR media_kind IN ('SIGNATURE', 'PHOTO')),
    -- Ou não há mídia, ou há mídia COM consentimento completo. Não existe meio-termo: uma assinatura
    -- guardada sem quem consentiu é dado pessoal sem base para estar ali.
    CONSTRAINT ck_proof_media_consent CHECK (
        (media_kind IS NULL AND media_key IS NULL AND media_consented_by IS NULL
         AND media_consented_at IS NULL AND media_purpose IS NULL)
        OR (media_kind IS NOT NULL AND media_key IS NOT NULL AND media_consented_by IS NOT NULL
            AND media_consented_at IS NOT NULL AND media_purpose IS NOT NULL)),
    -- Coordenada é par: metade dela não localiza nada e ainda assim é dado de posição.
    CONSTRAINT ck_proof_location CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude IS NOT NULL AND longitude IS NOT NULL)),
    -- Uma NÃO ENTREGA precisa do motivo: o que fazer amanhã depende dele, e "recusado" sozinho não diz
    -- se foi preço, avaria ou pedido errado.
    CONSTRAINT ck_proof_reason CHECK (
        outcome IN ('DELIVERED', 'PARTIAL') OR length(btrim(coalesce(note, ''))) > 0)
);

CREATE INDEX ix_proof_stop ON distribution_proof (stop_id, occurred_at DESC);

-- UMA PROVA ORIGINAL POR PARADA. A segunda tentativa de registrar a mesma parada é o duplo clique do
-- celular no meio da rua — e ela viraria duas entregas para o mesmo cliente. Corrigir tem caminho
-- próprio, e ele é o `corrects_proof_id`.
CREATE UNIQUE INDEX ux_proof_original ON distribution_proof (stop_id)
    WHERE corrects_proof_id IS NULL;

-- E UMA CORREÇÃO POR PROVA: corrigir a correção encadearia versões e tornaria "a última palavra" uma
-- pergunta.
CREATE UNIQUE INDEX ux_proof_correction ON distribution_proof (corrects_proof_id)
    WHERE corrects_proof_id IS NOT NULL;

-- O que desceu e o que subiu de volta. Duas listas, porque ENTREGAR E COLETAR SÃO FATOS SEPARADOS: o
-- motorista recolhe vazios num bar onde não deixou nada, e às vezes deixa sem recolher.
CREATE TABLE distribution_proof_item (
    id UUID PRIMARY KEY,
    proof_id UUID NOT NULL REFERENCES distribution_proof (id) ON DELETE CASCADE,
    container_id UUID NOT NULL,
    direction VARCHAR(10) NOT NULL,
    CONSTRAINT ck_proof_item_direction CHECK (direction IN ('DELIVERED', 'COLLECTED'))
);

-- O mesmo vasilhame não é entregue E recolhido na mesma prova.
CREATE UNIQUE INDEX ux_proof_item ON distribution_proof_item (proof_id, container_id);

CREATE INDEX ix_proof_item_container ON distribution_proof_item (container_id);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000171', '11111111-0000-0000-0000-000000000038',
     'distribution.delivery.record', 'Registrar entrega e coleta', false),
    -- Crítica: corrigir uma prova de entrega mexe no que já foi dado como fato, e é o tipo de ato que
    -- precisa de nome e trilha.
    ('22222222-0000-0000-0000-000000000172', '11111111-0000-0000-0000-000000000038',
     'distribution.delivery.correct', 'Corrigir prova de entrega', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('distribution.delivery.record', 'distribution.delivery.correct')
ON CONFLICT (group_id, permission_id) DO NOTHING;
