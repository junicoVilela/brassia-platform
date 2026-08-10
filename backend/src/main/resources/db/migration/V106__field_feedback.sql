-- FLD-001 — Feedback de campo.
--
-- DUAS TABELAS, E A SEPARAÇÃO É O CONTROLE.
--
-- A reclamação é registro de qualidade e precisa sobreviver anos. O dado pessoal de quem reclamou não —
-- e mantê-lo na mesma linha tornaria impossível apagar um sem apagar o outro. Separados, o apagamento a
-- pedido esvazia o contato e a investigação permanece íntegra.
CREATE TABLE field_complaint (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    batch_id UUID NOT NULL,
    -- Referência externa livre: número de atendimento, protocolo do SAC, id do formulário.
    reference VARCHAR(80),
    category VARCHAR(20) NOT NULL,
    severity VARCHAR(12) NOT NULL,
    description TEXT NOT NULL,
    -- Condições de armazenagem: existem para separar defeito de maltrato. Cerveja a 35 °C por duas
    -- semanas desenvolve off-flavor sem que nada tenha saído errado na fábrica. Todas anuláveis, porque
    -- quase nunca se sabe tudo — e nulo aqui é "ninguém perguntou", não "estava tudo bem".
    storage_temperature_celsius NUMERIC(5, 1),
    storage_days_since_purchase INTEGER,
    storage_exposed_to_light BOOLEAN,
    storage_notes TEXT,
    sample_status VARCHAR(16) NOT NULL,
    sample_location VARCHAR(200),
    status VARCHAR(16) NOT NULL,
    closing_note TEXT,
    closed_by UUID,
    closed_at TIMESTAMPTZ,
    registered_by UUID NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_field_complaint_status CHECK (status IN ('OPEN', 'UNDER_ANALYSIS', 'CLOSED')),
    CONSTRAINT ck_field_complaint_sample_status
        CHECK (sample_status IN ('RETAINED', 'WITH_CONSUMER', 'UNAVAILABLE', 'UNKNOWN')),
    -- Amostra retida sem lugar declarado é amostra que ninguém acha quando precisa.
    CONSTRAINT ck_field_complaint_sample_located
        CHECK (sample_status <> 'RETAINED' OR sample_location IS NOT NULL),
    CONSTRAINT ck_field_complaint_closed_complete CHECK (
        (status <> 'CLOSED' AND closed_by IS NULL AND closed_at IS NULL)
        OR (status = 'CLOSED' AND closed_by IS NOT NULL AND closed_at IS NOT NULL)
    )
);

CREATE INDEX ix_field_complaint_batch ON field_complaint (brewery_id, batch_id);
CREATE INDEX ix_field_complaint_status ON field_complaint (brewery_id, status);

-- As ações exigidas NÃO são gravadas: derivam de severidade + categoria na leitura.
--
-- Gravá-las abriria a possibilidade de existir uma reclamação de corpo estranho cuja lista de exigências
-- foi editada para vazia — que é exatamente o que esta história impede. O que se grava é o DESTINO de
-- cada exigência: atendida (com referência) ou dispensada (com justificativa assinada).
CREATE TABLE field_complaint_action (
    complaint_id UUID NOT NULL REFERENCES field_complaint (id) ON DELETE CASCADE,
    action VARCHAR(24) NOT NULL,
    fulfilled BOOLEAN NOT NULL,
    reference_id UUID,
    justification TEXT,
    decided_by UUID NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (complaint_id, action),
    CONSTRAINT ck_field_action_kind CHECK (action IN ('QUARANTINE', 'ROOT_CAUSE_ANALYSIS')),
    -- Atendida aponta para o que foi criado; dispensada explica por quê. Nunca as duas, nunca nenhuma:
    -- sem isto, "atendida" sem referência seria uma afirmação sem contra o que conferir, e uma dispensa
    -- sem texto seria indistinguível de esquecimento.
    CONSTRAINT ck_field_action_outcome CHECK (
        (fulfilled AND reference_id IS NOT NULL AND justification IS NULL)
        OR (NOT fulfilled AND reference_id IS NULL AND justification IS NOT NULL)
    )
);

-- DADO PESSOAL. Tabela própria, permissão própria, leitura auditada.
--
-- `erased` esvazia o conteúdo e preserva o fato: sem ele, "reclamação anônima desde o início" e "dados
-- apagados a pedido" ficariam indistinguíveis — e a segunda precisa ser demonstrável, inclusive para
-- quem pediu o apagamento.
CREATE TABLE field_complaint_contact (
    complaint_id UUID PRIMARY KEY REFERENCES field_complaint (id) ON DELETE CASCADE,
    name VARCHAR(200),
    email VARCHAR(200),
    phone VARCHAR(40),
    address VARCHAR(400),
    erased BOOLEAN NOT NULL DEFAULT FALSE,
    erased_at TIMESTAMPTZ,
    recorded_by UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    -- Apagado é apagado: o banco não guarda resto. Uma coluna que sobrevivesse ao `erased` seria o
    -- vazamento que a separação inteira existe para evitar.
    CONSTRAINT ck_field_contact_erased_is_empty CHECK (
        NOT erased OR (name IS NULL AND email IS NULL AND phone IS NULL AND address IS NULL)
    ),
    CONSTRAINT ck_field_contact_erased_dated CHECK (erased = (erased_at IS NOT NULL))
);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000032', NULL, 'feedback', 'Feedback de campo', 37)
ON CONFLICT (id) DO NOTHING;

-- Ler a reclamação e ler o dado pessoal são permissões DIFERENTES, e é o ponto.
--
-- Quem analisa off-flavor precisa do lote, da temperatura de armazenagem e da amostra — não do endereço
-- do consumidor. Uma permissão só faria todo analista ler dado pessoal de graça, todo dia, sem precisar.
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000135', '11111111-0000-0000-0000-000000000032',
     'feedback.complaint.read', 'Consultar reclamações de campo', false),
    ('22222222-0000-0000-0000-000000000136', '11111111-0000-0000-0000-000000000032',
     'feedback.complaint.write', 'Registrar e tratar reclamações', false),
    ('22222222-0000-0000-0000-000000000137', '11111111-0000-0000-0000-000000000032',
     'feedback.contact.read', 'Ver dados pessoais de quem reclamou — cada leitura é auditada', true),
    ('22222222-0000-0000-0000-000000000138', '11111111-0000-0000-0000-000000000032',
     'feedback.contact.erase', 'Apagar dados pessoais a pedido', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('feedback.complaint.read', 'feedback.complaint.write',
                 'feedback.contact.read', 'feedback.contact.erase')
ON CONFLICT (group_id, permission_id) DO NOTHING;
