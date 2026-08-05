-- CST-001: custo realizado do lote.
--
-- O custo é DERIVADO enquanto o lote está aberto e CONGELADO quando alguém o fecha. É a mesma
-- distinção que a sprint 12 firmou: o que é sobre o presente se deriva, o que é sobre o passado se
-- guarda. Um custo aberto tem de acompanhar o que ainda acontece — um envase a mais muda o custo
-- por litro —, e um custo fechado é a resposta daquele dia, que não pode mudar sozinha depois.
--
-- Por isso a tabela nasce vazia: enquanto ninguém fechar, não há linha nenhuma aqui, e a consulta
-- responde do ledger. Fechar é um ato com autor, data e motivo.
CREATE TABLE costing_batch_cost (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    batch_id UUID NOT NULL,
    batch_code VARCHAR(40) NOT NULL,
    -- Volume que serve de divisor do custo por litro. Congelado junto: recalcular o divisor depois
    -- mudaria o indicador de um custo que já foi fechado.
    volume_liters NUMERIC(12, 3) NOT NULL,
    total_cost NUMERIC(14, 4) NOT NULL,
    note VARCHAR(500),
    closed_by UUID NOT NULL,
    closed_at TIMESTAMPTZ NOT NULL,
    -- Um custo fechado por lote. Refazer o cálculo é reabrir, e reabrir não existe: um custo
    -- fechado é evidência, e evidência que se sobrescreve não é evidência.
    CONSTRAINT uq_batch_cost UNIQUE (brewery_id, batch_id),
    CONSTRAINT ck_batch_cost_volume CHECK (volume_liters > 0),
    CONSTRAINT ck_batch_cost_total CHECK (total_cost >= 0)
);

-- Cada parcela com a origem por extenso: o critério da história é "origem de cada parcela é
-- rastreável". Guardar só o total daria um número que ninguém consegue explicar seis meses depois.
CREATE TABLE costing_batch_cost_line (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    cost_id UUID NOT NULL REFERENCES costing_batch_cost (id) ON DELETE CASCADE,
    category VARCHAR(20) NOT NULL,
    description VARCHAR(200) NOT NULL,
    -- De onde o número veio, em texto legível: "lote F-1234 do fornecedor", "consumo do envase".
    source VARCHAR(300) NOT NULL,
    quantity NUMERIC(14, 4) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    unit_cost NUMERIC(14, 4) NOT NULL,
    total NUMERIC(14, 4) NOT NULL,
    CONSTRAINT ck_cost_line_category CHECK (category IN ('INGREDIENT', 'PACKAGING', 'UTILITY', 'LABOR'))
);

CREATE INDEX ix_cost_line_cost ON costing_batch_cost_line (brewery_id, cost_id);

-- As lacunas do custo fechado, congeladas junto. Um custo que não diz o que ficou de fora parece
-- completo e não é: sem mão de obra e sem utilidade, o total é menor do que a verdade, e quem lê
-- precisa saber disso ao ler, não depois.
CREATE TABLE costing_batch_cost_gap (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    cost_id UUID NOT NULL REFERENCES costing_batch_cost (id) ON DELETE CASCADE,
    category VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL
);

CREATE INDEX ix_cost_gap_cost ON costing_batch_cost_gap (brewery_id, cost_id);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000022', NULL, 'costing', 'Custos', 27)
ON CONFLICT (id) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000102', '11111111-0000-0000-0000-000000000022',
     'costing.cost.read', 'Consultar o custo realizado do lote', false),
    ('22222222-0000-0000-0000-000000000103', '11111111-0000-0000-0000-000000000022',
     'costing.cost.close', 'Fechar o custo do lote — o número deixa de mudar', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('costing.cost.read', 'costing.cost.close')
ON CONFLICT (group_id, permission_id) DO NOTHING;
