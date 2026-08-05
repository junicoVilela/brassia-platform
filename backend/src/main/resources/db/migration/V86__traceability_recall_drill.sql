-- FDS-004: simulado de recall — medir tempo, cobertura e lacunas sem afetar estoque real.
--
-- "Sem afetar estoque real" é a restrição que define a tabela: o simulado NÃO cria expedição, não
-- move saldo, não abre quarentena e não gera pendência de comunicação. Ele lê o mesmo grafo que o
-- recall de verdade leria e cronometra a casa respondendo "onde está".
--
-- O QUE É MEDIDO FICA CONGELADO, e aqui a cópia é a coisa certa — pelo motivo oposto ao do escopo
-- do recall. O escopo é sobre o presente e por isso é derivado; o resultado do simulado é uma
-- MEDIÇÃO: quantas unidades a equipe localizou, em quanto tempo, com quantas lacunas naquele dia.
-- Recalcular isso depois responderia sobre outro dia e apagaria o exercício — o mesmo princípio de
-- uma leitura de instrumento (MTR-002), que se corrige com registro, não se recalcula.
--
-- O tempo medido é o da CERVEJARIA, não o do sistema. Derivar o escopo leva milissegundos e não
-- diz nada; o que a norma cobra, e o que o gerente precisa saber, é quantas horas a casa levou
-- para localizar o produto.
CREATE TABLE traceability_recall_drill (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    code VARCHAR(30) NOT NULL,
    node_type VARCHAR(30) NOT NULL,
    node_id UUID NOT NULL,
    origin_label VARCHAR(200),
    note VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    started_by UUID NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_by UUID,
    finished_at TIMESTAMPTZ,
    -- Medição congelada no encerramento. Nula enquanto o simulado corre: o relatório não existe
    -- antes de a equipe dizer o que encontrou.
    units_in_scope INTEGER,
    units_located INTEGER,
    destinations_reached INTEGER,
    gaps_found INTEGER,
    summary VARCHAR(1000),
    -- Texto livre: transformar ação corretiva em item com dono e prazo é trabalho do CAPA (QLT-002),
    -- e duplicá-lo aqui criaria um segundo lugar para acompanhar a mesma coisa.
    corrective_actions VARCHAR(2000),
    CONSTRAINT uq_drill_code UNIQUE (brewery_id, code),
    CONSTRAINT ck_drill_status CHECK (status IN ('RUNNING', 'FINISHED')),
    CONSTRAINT ck_drill_finish CHECK (
        (status = 'RUNNING' AND finished_at IS NULL AND units_located IS NULL AND summary IS NULL)
        OR (status = 'FINISHED' AND finished_by IS NOT NULL AND finished_at IS NOT NULL
            AND units_in_scope IS NOT NULL AND units_located IS NOT NULL AND summary IS NOT NULL)),
    CONSTRAINT ck_drill_located CHECK (units_located IS NULL OR units_located >= 0)
);

CREATE INDEX ix_drill_started ON traceability_recall_drill (brewery_id, started_at DESC);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000100', '11111111-0000-0000-0000-000000000020',
     'traceability.drill.read', 'Consultar simulados de recall e seus relatórios', false),
    ('22222222-0000-0000-0000-000000000101', '11111111-0000-0000-0000-000000000020',
     'traceability.drill.manage', 'Iniciar e encerrar simulado de recall', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('traceability.drill.read', 'traceability.drill.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
