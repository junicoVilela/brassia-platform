-- CLN-001: POP de limpeza/sanitização versionado. Rascunho editável → publicado
-- (imutável; o ciclo referencia a versão). Etapas com campos tipados da ficha:
-- método, produto, faixa autorizada (concentração/temperatura), tempo, vazão/ação
-- mecânica, EPIs, alternativa, proibição e evidência exigida. Multi-tenant.

CREATE TABLE sanitation_procedure (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    version INTEGER NOT NULL,
    status VARCHAR(12) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_sanitation_procedure_version UNIQUE (brewery_id, code, version),
    CONSTRAINT ck_sanitation_procedure_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

CREATE INDEX ix_sanitation_procedure_brewery ON sanitation_procedure (brewery_id, code, version);

CREATE TABLE sanitation_procedure_step (
    id UUID PRIMARY KEY,
    procedure_id UUID NOT NULL REFERENCES sanitation_procedure (id) ON DELETE CASCADE,
    brewery_id UUID NOT NULL,
    step_order INTEGER NOT NULL,
    method VARCHAR(120) NOT NULL,
    product VARCHAR(120),
    concentration_min_pct NUMERIC(6, 3),
    concentration_max_pct NUMERIC(6, 3),
    temp_min_c NUMERIC(5, 2),
    temp_max_c NUMERIC(5, 2),
    time_minutes INTEGER,
    flow VARCHAR(120),
    ppe VARCHAR(200),
    alternative VARCHAR(200),
    prohibition VARCHAR(200),
    evidence_required BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT uq_sanitation_step_order UNIQUE (procedure_id, step_order),
    CONSTRAINT ck_sanitation_step_conc
        CHECK (concentration_min_pct IS NULL OR concentration_max_pct IS NULL
               OR concentration_min_pct <= concentration_max_pct),
    CONSTRAINT ck_sanitation_step_temp
        CHECK (temp_min_c IS NULL OR temp_max_c IS NULL OR temp_min_c <= temp_max_c)
);

CREATE INDEX ix_sanitation_procedure_step ON sanitation_procedure_step (procedure_id, step_order);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000013', NULL, 'sanitation', 'Limpeza e sanitização', 15)
ON CONFLICT (id) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000050', '11111111-0000-0000-0000-000000000013',
     'sanitation.procedure.read', 'Consultar POPs de limpeza', false),
    ('22222222-0000-0000-0000-000000000051', '11111111-0000-0000-0000-000000000013',
     'sanitation.procedure.manage', 'Cadastrar e publicar POPs de limpeza', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('sanitation.procedure.read', 'sanitation.procedure.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
