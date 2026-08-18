-- DUV-FCST-001 — a metade "capacidade" da previsão.
--
-- O TEMPO DE CICLO É DECLARADO, E NÃO INFERIDO. Quantos dias uma cerveja ocupa o fermentador depende do
-- estilo, da temperatura e do que a casa aceita; inferir isso de lotes passados daria um número que parece
-- cálculo e é média de coisas diferentes. A cervejaria declara os dias por tanque, e o sistema multiplica.
--
-- NENHUMA LINHA É INSERIDA AQUI. Sem tanque declarado, a previsão responde "não sei" sobre capacidade — e
-- não zero. Zero diria que a cervejaria não consegue produzir nada, e alguém planejaria em cima disso.
CREATE TABLE forecast_tank_cycle (
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    -- Sem chave estrangeira para equipment: ela mora em outro módulo. A integridade vem do caso de uso,
    -- que só grava depois de o equipamento responder por si.
    equipment_id UUID NOT NULL,
    cycle_days INTEGER NOT NULL,
    note VARCHAR(300),
    updated_by UUID NOT NULL REFERENCES security_user (id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (brewery_id, equipment_id),
    -- Ciclo zero seria produção infinita, e o erro só apareceria como uma capacidade absurda que ninguém
    -- questiona porque veio do sistema.
    CONSTRAINT ck_tank_cycle_days CHECK (cycle_days >= 1)
);

INSERT INTO security_permission (id, domain_id, code, name, critical)
SELECT '22222222-0000-0000-0000-000000000177', d.id,
       'forecast.capacity.manage', 'Declarar o ciclo de ocupação dos fermentadores', false
FROM permission_domain d WHERE d.code = 'sales'
ON CONFLICT (id) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'forecast.capacity.manage'
ON CONFLICT (group_id, permission_id) DO NOTHING;
