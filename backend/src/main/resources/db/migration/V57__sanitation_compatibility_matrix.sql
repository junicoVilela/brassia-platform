-- CLN-002: matriz de compatibilidade. Recomenda método/POP por material, sujidade,
-- risco e produto anterior. Sem herança entre materiais (madeira/plástico ≠ inox).
-- A regra pode referenciar um POP publicado (código) e traz alternativa e restrição.

CREATE TABLE sanitation_compatibility_rule (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    material VARCHAR(16) NOT NULL,
    soiling VARCHAR(12) NOT NULL,
    risk VARCHAR(8) NOT NULL,
    previous_product VARCHAR(120),
    procedure_code VARCHAR(40),
    method VARCHAR(160) NOT NULL,
    alternative VARCHAR(300),
    restriction VARCHAR(300),
    CONSTRAINT ck_sanitation_rule_material
        CHECK (material IN ('INOX', 'ALUMINIO', 'PLASTICO', 'MADEIRA', 'VIDRO', 'BORRACHA')),
    CONSTRAINT ck_sanitation_rule_soiling CHECK (soiling IN ('LEVE', 'MODERADA', 'PESADA')),
    CONSTRAINT ck_sanitation_rule_risk CHECK (risk IN ('BAIXO', 'MEDIO', 'ALTO')),
    CONSTRAINT uq_sanitation_rule_key UNIQUE (brewery_id, material, soiling, risk, previous_product)
);

CREATE INDEX ix_sanitation_rule_lookup
    ON sanitation_compatibility_rule (brewery_id, material, soiling, risk);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000052', '11111111-0000-0000-0000-000000000013',
     'sanitation.matrix.read', 'Consultar matriz de compatibilidade', false),
    ('22222222-0000-0000-0000-000000000053', '11111111-0000-0000-0000-000000000013',
     'sanitation.matrix.manage', 'Cadastrar regras da matriz', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('sanitation.matrix.read', 'sanitation.matrix.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
