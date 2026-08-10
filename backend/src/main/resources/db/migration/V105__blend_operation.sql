-- BLD-001 — União e divisão de volume entre lotes.
--
-- PREMISSA DECLARADA: origem e destino são lotes que JÁ EXISTEM.
--
-- A alternativa — criar um lote novo como resultado — esbarra em `production_batch.order_id NOT NULL`
-- com `UNIQUE (brewery_id, order_id)`: um resultado de blend não nasce de uma ordem de produção, e
-- inventar uma ordem sintética para satisfazer a coluna criaria uma ordem que ninguém programou, que
-- aparece no planejamento e que o custeio tentaria ratear. Ver DEC-BLD-003 no STATUS: a pergunta de
-- negócio está registrada, não resolvida por conta própria.
CREATE TABLE blend_operation (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    kind VARCHAR(10) NOT NULL,
    -- Perda declarada é o caminho legítimo para o balanço fechar: quem perdeu 12 L na transferência
    -- declara 12 L. O que não se aceita é a conta não fechar sem ninguém dizer por quê.
    declared_loss_liters NUMERIC(12, 3) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(12) NOT NULL,
    simulated_by UUID NOT NULL,
    simulated_at TIMESTAMPTZ NOT NULL,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    executed_by UUID,
    executed_at TIMESTAMPTZ,
    CONSTRAINT ck_blend_kind CHECK (kind IN ('MERGE', 'SPLIT')),
    CONSTRAINT ck_blend_status CHECK (status IN ('SIMULATED', 'APPROVED', 'EXECUTED', 'DISCARDED')),
    CONSTRAINT ck_blend_loss_not_negative CHECK (declared_loss_liters >= 0),
    -- Aprovar e executar registram QUEM e QUANDO, ou nenhum dos dois. Uma execução sem autor é cerveja
    -- que mudou de tanque sem ninguém por trás.
    CONSTRAINT ck_blend_approval_complete CHECK (
        (approved_by IS NULL AND approved_at IS NULL)
        OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)
    ),
    CONSTRAINT ck_blend_execution_complete CHECK (
        (executed_by IS NULL AND executed_at IS NULL)
        OR (executed_by IS NOT NULL AND executed_at IS NOT NULL)
    ),
    -- Executado exige aprovação: é a aprovação que autoriza mexer no tanque.
    CONSTRAINT ck_blend_executed_was_approved CHECK (
        status <> 'EXECUTED' OR (approved_by IS NOT NULL AND executed_by IS NOT NULL)
    )
);

CREATE INDEX ix_blend_operation_brewery_status ON blend_operation (brewery_id, status);

-- Entradas e saídas na mesma tabela, distinguidas por `side`.
--
-- Tabelas separadas duplicariam as mesmas colunas e, pior, permitiriam um lote existir dos dois lados
-- sem que nenhuma restrição percebesse — o ciclo de genealogia que o domínio recusa. Aqui a chave
-- primária composta impede o lote de repetir no mesmo lado, e o domínio cuida da sobreposição.
CREATE TABLE blend_movement (
    operation_id UUID NOT NULL REFERENCES blend_operation (id) ON DELETE CASCADE,
    side VARCHAR(6) NOT NULL,
    batch_id UUID NOT NULL,
    liters NUMERIC(12, 3) NOT NULL,
    PRIMARY KEY (operation_id, side, batch_id),
    CONSTRAINT ck_blend_movement_side CHECK (side IN ('INPUT', 'OUTPUT')),
    -- O sentido vem do `side`, nunca do sinal: guardar o sentido no sinal do número transformaria todo
    -- erro de sinal num balanço que fecha por acidente.
    CONSTRAINT ck_blend_movement_positive CHECK (liters > 0)
);

CREATE INDEX ix_blend_movement_batch ON blend_movement (batch_id);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000031', NULL, 'blend', 'Blend e reprocesso', 36)
ON CONFLICT (id) DO NOTHING;

-- Três permissões e não uma: simular é gratuito e não muda nada; aprovar autoriza misturar; executar
-- abre a válvula. Aprovar e executar são críticas porque, depois de misturadas, duas cervejas não se
-- separam — a operação é irreversível de um jeito que quase nenhuma outra na plataforma é.
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000131', '11111111-0000-0000-0000-000000000031',
     'blend.operation.read', 'Consultar operações de blend', false),
    ('22222222-0000-0000-0000-000000000132', '11111111-0000-0000-0000-000000000031',
     'blend.operation.simulate', 'Simular união ou divisão', false),
    ('22222222-0000-0000-0000-000000000133', '11111111-0000-0000-0000-000000000031',
     'blend.operation.approve', 'Aprovar operação de blend', true),
    ('22222222-0000-0000-0000-000000000134', '11111111-0000-0000-0000-000000000031',
     'blend.operation.execute', 'Executar blend — irreversível', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('blend.operation.read', 'blend.operation.simulate',
                 'blend.operation.approve', 'blend.operation.execute')
ON CONFLICT (group_id, permission_id) DO NOTHING;
