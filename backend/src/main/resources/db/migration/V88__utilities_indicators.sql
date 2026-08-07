-- UTL-001: água, energia e CO₂ por litro envasado.
--
-- NÃO HÁ TABELA, e é a decisão da história. O indicador é derivado das medições que já existem —
-- o consumo do ciclo de limpeza (CLN-005) e o consumo de gás lançado na conexão (GAS-001) —
-- dividido pelo que foi envasado no período. Guardá-lo criaria uma terceira verdade que
-- envelheceria a cada ciclo registrado com atraso, e o critério pede o contrário: "período é
-- reproduzível", isto é, o mesmo período responde o mesmo enquanto os fatos não mudam.
--
-- O que se guarda é a MEDIÇÃO, e ela já está guardada nos módulos que medem. O indicador é
-- aritmética sobre elas.
INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000023', NULL, 'utilities', 'Utilidades e sustentabilidade', 28)
ON CONFLICT (id) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000104', '11111111-0000-0000-0000-000000000023',
     'utilities.indicator.read', 'Consultar consumo de água, energia e CO₂ por litro', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'utilities.indicator.read'
ON CONFLICT (group_id, permission_id) DO NOTHING;
