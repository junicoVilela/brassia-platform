-- CST-002-A — a perda esperada ganha lugar, e a perda real passa a ter contra o que ser comparada.
--
-- O QUE HAVIA. A variação (CST-002) mostrava a perda como **fato, sem desvio**, e declarava a lacuna:
-- "chamá-la de desfavorável seria acusar a fábrica com um critério que ela nunca definiu, e assumir
-- esperado zero faria toda perda parecer desvio". O critério estava certo; faltava alguém definir o
-- esperado.
--
-- ONDE FICA, E POR QUE NÃO NO EQUIPAMENTO. O critério de remoção sugeria "no equipamento ou na política
-- de envase", e a decisão do mantenedor foi outra: **na receita**. A razão é que a perda característica é
-- da cerveja tanto quanto do tanque — uma IPA muito lupulada deixa mais líquido preso no trub que uma
-- lager no mesmo fermentador, e o dead space do equipamento já é conhecido em outro lugar. A receita já
-- carrega eficiência de mostura e volumes; perda esperada é o mesmo tipo de dado.
--
-- VERSIONA DE GRAÇA. Cada versão de receita é uma linha (V28), então a perda esperada acompanha a versão
-- publicada — e um lote é comparado contra o número que valia quando ele foi feito, não contra o que
-- alguém ajustou depois.
--
-- PERCENTUAL, E NÃO LITROS. Perda de trub e de absorção de lúpulo escala com o tamanho da brassa; um
-- valor absoluto ficaria errado no dia em que a cervejaria dobrar o lote, e ficaria errado em silêncio.
ALTER TABLE recipe
    ADD COLUMN transfer_loss_percent NUMERIC(5, 2),
    ADD COLUMN packaging_loss_percent NUMERIC(5, 2);

-- Nulo é o estado de quem ainda não mediu a própria perda, e continua sendo legítimo: a variação volta a
-- mostrar a perda como fato e a declarar a lacuna. O que o CHECK impede é o número impossível — perda
-- negativa seria cerveja aparecendo, e 100% seria um lote que não chega ao fermentador.
ALTER TABLE recipe
    ADD CONSTRAINT ck_recipe_transfer_loss CHECK (
        transfer_loss_percent IS NULL
        OR (transfer_loss_percent >= 0 AND transfer_loss_percent < 100));

ALTER TABLE recipe
    ADD CONSTRAINT ck_recipe_packaging_loss CHECK (
        packaging_loss_percent IS NULL
        OR (packaging_loss_percent >= 0 AND packaging_loss_percent < 100));
