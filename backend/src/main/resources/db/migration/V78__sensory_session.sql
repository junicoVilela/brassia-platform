-- SEN-001: sessão sensorial — amostras cegas, ficha, resultado e comparação.
-- A cegueira é sustentada pelo ESTADO da sessão: enquanto OPEN, nenhuma nota e nenhum lote saem
-- da API. O vínculo ao lote nunca é apagado — ele apenas não é revelado antes do fechamento.

CREATE TABLE sensory_session (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    purpose VARCHAR(200) NOT NULL,
    scheduled_for DATE NOT NULL,
    status VARCHAR(10) NOT NULL,
    opened_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_sensory_session_code UNIQUE (brewery_id, code),
    CONSTRAINT ck_sensory_session_status CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED')),
    CONSTRAINT ck_sensory_session_opened CHECK (status = 'DRAFT' OR opened_at IS NOT NULL),
    CONSTRAINT ck_sensory_session_closed CHECK (
        (status <> 'CLOSED' AND closed_at IS NULL) OR (status = 'CLOSED' AND closed_at IS NOT NULL))
);

CREATE INDEX ix_sensory_session_brewery ON sensory_session (brewery_id, status, scheduled_for DESC);

CREATE TABLE sensory_sample (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    session_id UUID NOT NULL REFERENCES sensory_session (id) ON DELETE CASCADE,
    -- Três dígitos ALEATÓRIOS: código sequencial vazaria a ordem de preparo, e ordem é informação.
    blind_code CHAR(3) NOT NULL,
    -- O mesmo lote pode aparecer duas vezes sob códigos diferentes: duplicata cega é técnica para
    -- medir a consistência do painel, então NÃO há unicidade por lote.
    batch_id UUID NOT NULL,
    note VARCHAR(500),
    CONSTRAINT uq_sensory_sample_blind_code UNIQUE (session_id, blind_code),
    CONSTRAINT ck_sensory_sample_blind_code CHECK (blind_code ~ '^[0-9]{3}$' AND blind_code <> '000')
);

CREATE INDEX ix_sensory_sample_session ON sensory_sample (session_id);

-- A ficha SÓ ENTRA: não há update. Sem isso bastaria esperar o fechamento, ver o resultado e
-- reescrever a própria avaliação.
CREATE TABLE sensory_evaluation (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    session_id UUID NOT NULL REFERENCES sensory_session (id) ON DELETE CASCADE,
    sample_id UUID NOT NULL REFERENCES sensory_sample (id) ON DELETE CASCADE,
    taster_id UUID NOT NULL,
    appearance SMALLINT NOT NULL,
    aroma SMALLINT NOT NULL,
    flavor SMALLINT NOT NULL,
    body SMALLINT NOT NULL,
    overall SMALLINT NOT NULL,
    descriptors JSONB NOT NULL,
    note VARCHAR(1000),
    submitted_at TIMESTAMPTZ NOT NULL,
    -- Um provador, uma ficha por amostra: reenviar seria a porta dos fundos da imutabilidade.
    CONSTRAINT uq_sensory_evaluation_taster UNIQUE (sample_id, taster_id),
    CONSTRAINT ck_sensory_evaluation_scores CHECK (
        appearance BETWEEN 0 AND 10 AND aroma BETWEEN 0 AND 10 AND flavor BETWEEN 0 AND 10
        AND body BETWEEN 0 AND 10 AND overall BETWEEN 0 AND 10)
);

CREATE INDEX ix_sensory_evaluation_session ON sensory_evaluation (brewery_id, session_id);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000019', NULL, 'sensory', 'Análise sensorial', 24)
ON CONFLICT (id) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000083', '11111111-0000-0000-0000-000000000019',
     'sensory.session.read', 'Consultar sessões sensoriais e resultados', false),
    ('22222222-0000-0000-0000-000000000084', '11111111-0000-0000-0000-000000000019',
     'sensory.session.manage', 'Montar, abrir e encerrar sessões sensoriais', false),
    ('22222222-0000-0000-0000-000000000085', '11111111-0000-0000-0000-000000000019',
     'sensory.evaluation.submit', 'Enviar ficha de avaliação sensorial', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('sensory.session.read', 'sensory.session.manage', 'sensory.evaluation.submit')
ON CONFLICT (group_id, permission_id) DO NOTHING;
