-- CST-002: planejado versus real.
--
-- NÃO HÁ TABELA, pela mesma razão da UTL-001. A variação é uma explicação sobre fatos que já estão
-- guardados: o plano sai da receita que a ordem congelou, a base de preço sai dos movimentos de
-- reserva (que sobrevivem no ledger append-only mesmo depois de liberados), o real sai do consumo
-- confirmado, e os volumes saem da transferência e do envase. Congelar a explicação criaria uma
-- segunda verdade ao lado do custo fechado — e ela envelheceria a cada correção de estoque.
--
-- O que se congela é o CUSTO (CST-001). A explicação dele se refaz.
--
-- Alçada separada da leitura do custo: variação expõe preço de compra por ingrediente, que é
-- informação comercial. Quem pode ver o total do lote não necessariamente pode ver por quanto a
-- casa comprou o malte.
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000105', '11111111-0000-0000-0000-000000000022',
     'costing.variance.read', 'Consultar planejado versus real do lote', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'costing.variance.read'
ON CONFLICT (group_id, permission_id) DO NOTHING;
