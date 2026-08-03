-- QLT-002: não conformidade e CAPA — conter, investigar, agir e verificar eficácia.
-- As fases têm ORDEM: não se investiga o que não se conteve, não se age sem causa raiz e não se
-- verifica sem ação. Encerrar exige verificação com resultado positivo — fechar com verificação
-- negativa produziria um registro dizendo que o problema foi resolvido quando ele não foi.
-- Prazo vencido é DERIVADO da data na consulta, nunca coluna: coluna de "atrasado" envelheceria
-- sozinha e precisaria de varredura agendada, que a plataforma ainda não tem.

CREATE TABLE quality_non_conformity (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    -- Desvio é só uma das origens: NC também nasce de reclamação, auditoria e fornecedor.
    source VARCHAR(20) NOT NULL,
    deviation_id UUID REFERENCES quality_deviation (id),
    severity VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    containment_due_on DATE NOT NULL,
    investigation_due_on DATE NOT NULL,
    verification_due_on DATE NOT NULL,
    containment_description VARCHAR(1000),
    containment_at TIMESTAMPTZ,
    containment_by UUID,
    investigation_root_cause VARCHAR(1000),
    investigation_method VARCHAR(200),
    investigation_at TIMESTAMPTZ,
    investigation_by UUID,
    opened_at TIMESTAMPTZ NOT NULL,
    opened_by UUID NOT NULL,
    closed_at TIMESTAMPTZ,
    closed_by UUID,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_quality_nc_code UNIQUE (brewery_id, code),
    CONSTRAINT ck_quality_nc_source CHECK (source IN ('DEVIATION', 'COMPLAINT', 'AUDIT', 'SUPPLIER',
        'OTHER')),
    CONSTRAINT ck_quality_nc_severity CHECK (severity IN ('MINOR', 'MAJOR', 'CRITICAL')),
    CONSTRAINT ck_quality_nc_status CHECK (status IN ('OPEN', 'CONTAINED', 'INVESTIGATED',
        'ACTION_PLANNED', 'VERIFIED', 'CLOSED')),
    -- NC originada de desvio precisa apontar o desvio, senão o encerramento não tem o que fechar.
    CONSTRAINT ck_quality_nc_deviation CHECK (source <> 'DEVIATION' OR deviation_id IS NOT NULL),
    -- Conter depois de investigar não faz sentido nem no papel.
    CONSTRAINT ck_quality_nc_due_order CHECK (
        investigation_due_on >= containment_due_on AND verification_due_on >= investigation_due_on),
    -- A contenção é registro completo ou ausente; meia contenção não existe.
    CONSTRAINT ck_quality_nc_containment CHECK (
        (containment_description IS NULL AND containment_at IS NULL AND containment_by IS NULL)
        OR (containment_description IS NOT NULL AND containment_at IS NOT NULL
            AND containment_by IS NOT NULL)),
    CONSTRAINT ck_quality_nc_investigation CHECK (
        (investigation_root_cause IS NULL AND investigation_method IS NULL AND investigation_at IS NULL
         AND investigation_by IS NULL)
        OR (investigation_root_cause IS NOT NULL AND investigation_method IS NOT NULL
            AND investigation_at IS NOT NULL AND investigation_by IS NOT NULL)),
    CONSTRAINT ck_quality_nc_closed CHECK (
        (status <> 'CLOSED' AND closed_at IS NULL AND closed_by IS NULL)
        OR (status = 'CLOSED' AND closed_at IS NOT NULL AND closed_by IS NOT NULL))
);

CREATE INDEX ix_quality_nc_open ON quality_non_conformity (brewery_id, status, opened_at DESC);
CREATE INDEX ix_quality_nc_deviation ON quality_non_conformity (brewery_id, deviation_id)
    WHERE deviation_id IS NOT NULL;

-- Corretiva trata a ocorrência; preventiva trata a causa. Um CAPA só com corretiva se repete.
CREATE TABLE quality_capa_action (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    non_conformity_id UUID NOT NULL REFERENCES quality_non_conformity (id) ON DELETE CASCADE,
    kind VARCHAR(12) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    owner VARCHAR(120) NOT NULL,
    due_on DATE NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_quality_action_kind CHECK (kind IN ('CORRECTIVE', 'PREVENTIVE'))
);

CREATE INDEX ix_quality_action_nc ON quality_capa_action (non_conformity_id, due_on);

-- O histórico de verificações só cresce: a negativa fica como evidência de que a primeira
-- tentativa não resolveu.
CREATE TABLE quality_verification (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    non_conformity_id UUID NOT NULL REFERENCES quality_non_conformity (id) ON DELETE CASCADE,
    effective BOOLEAN NOT NULL,
    evidence VARCHAR(1000) NOT NULL,
    verified_at TIMESTAMPTZ NOT NULL,
    verified_by UUID NOT NULL
);

CREATE INDEX ix_quality_verification_nc ON quality_verification (non_conformity_id, verified_at);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000080', '11111111-0000-0000-0000-000000000018',
     'quality.nc.read', 'Consultar não conformidades e o tratamento', false),
    ('22222222-0000-0000-0000-000000000081', '11111111-0000-0000-0000-000000000018',
     'quality.nc.manage', 'Abrir e tratar não conformidades', false),
    -- Encerrar é alçada própria: é o ato que declara o problema resolvido.
    ('22222222-0000-0000-0000-000000000082', '11111111-0000-0000-0000-000000000018',
     'quality.nc.close', 'Encerrar não conformidade após verificação eficaz', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('quality.nc.read', 'quality.nc.manage', 'quality.nc.close')
ON CONFLICT (group_id, permission_id) DO NOTHING;
