-- DEB-SAL-001 — o custeio passa a guardar a moeda junto do valor.
--
-- O PROBLEMA: `costing` guardava `BigDecimal` puro em custo total, custo por litro e taxa da hora.
-- Enquanto a cervejaria opera numa moeda só, nada quebra — mas a primeira exportação soma real com dólar
-- sem que nada reclame, e o erro aparece no fechamento do mês, longe da causa.
--
-- A MOEDA JÁ EXISTIA, e o que faltava era ela chegar aqui: `brewery_operational_preferences.currency_code`
-- é a fonte de verdade desde a Sprint 01. Nada é inventado neste arquivo — o backfill materializa a
-- suposição que o custeio já fazia em silêncio.

ALTER TABLE costing_batch_cost ADD COLUMN currency CHAR(3);
ALTER TABLE costing_labor_rate ADD COLUMN currency CHAR(3);

-- Backfill a partir da preferência da própria cervejaria. Um custo fechado é evidência: escrever nele a
-- moeda que a casa declarava na época é o mais próximo da verdade que existe — e é exatamente o que
-- quem leu aquele número entendeu.
UPDATE costing_batch_cost c
SET currency = p.currency_code
FROM brewery_operational_preferences p
WHERE p.brewery_id = c.brewery_id AND c.currency IS NULL;

UPDATE costing_labor_rate r
SET currency = p.currency_code
FROM brewery_operational_preferences p
WHERE p.brewery_id = r.brewery_id AND r.currency IS NULL;

-- Cervejaria sem preferência configurada não deveria ter custo fechado, mas o banco não sabe disso, e
-- uma migration que falha em produção por causa de uma linha órfã é pior que um padrão declarado.
UPDATE costing_batch_cost SET currency = 'BRL' WHERE currency IS NULL;
UPDATE costing_labor_rate SET currency = 'BRL' WHERE currency IS NULL;

ALTER TABLE costing_batch_cost ALTER COLUMN currency SET NOT NULL;
ALTER TABLE costing_labor_rate ALTER COLUMN currency SET NOT NULL;

-- ISO 4217: três letras maiúsculas. Aceitar "R$" ou "reais" faria a comparação entre moedas depender de
-- como cada um digitou, e dois custos "na mesma moeda" deixariam de ser comparáveis.
ALTER TABLE costing_batch_cost
    ADD CONSTRAINT ck_batch_cost_currency CHECK (currency ~ '^[A-Z]{3}$');
ALTER TABLE costing_labor_rate
    ADD CONSTRAINT ck_labor_rate_currency CHECK (currency ~ '^[A-Z]{3}$');

-- NÃO entra moeda na LINHA do custo, e é decisão.
--
-- Quem produz uma linha é o contribuinte — estoque, envase, utilidades, mão de obra —, e nenhum deles
-- conhece moeda: eles reportam "consumi 20 kg a 4,50". Exigir moeda na linha faria a produção precisar
-- saber de dinheiro para registrar que trabalhou, que é exatamente o que a V117 recusou quando separou
-- apontamento de hora da taxa da hora. A moeda é da CASA, e o custeio a carimba quando compõe o total.
