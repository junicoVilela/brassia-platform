-- PKG-004-B — o ABV medido passa a existir, e a etiqueta prefere o que foi medido.
--
-- O QUE HAVIA. O ABV do rótulo vinha das métricas da receita publicada — fonte rastreável, com versão, e
-- honesta ao dizer "calculado, não medido". O que faltava era o outro lado: uma cervejaria que mede ABV em
-- laboratório não tinha onde guardar o número, e o rótulo continuava imprimindo a conta da receita mesmo
-- quando havia medição melhor.
--
-- POR QUE MEDIÇÃO DE LOTE, E NÃO CAMPO NOVO EM ALGUM LUGAR. ABV é uma grandeza medida do lote, igual a cor
-- e amargor — que já são MeasurementKind desde a V52. Um campo próprio criaria um segundo caminho para
-- registrar medição, com outra permissão, outra auditoria e outra tela, para a mesma coisa. Entrando aqui,
-- ele herda tudo: quem pode registrar, o rastro de quem registrou, a carta de controle e a série histórica.
--
-- A UNIDADE É `%ABV`, e não `%`: porcentagem de quê é a pergunta que separa álcool por volume de álcool
-- por massa, e as duas circulam em rótulo pelo mundo. É também a notação impressa no rótulo, então quem
-- lê a série reconhece. A plataforma não converte — a conversão pertence a quem mediu.
--
-- (A coluna `unit` é VARCHAR(8), o que descartou `ABV_PERCENT` — e a notação curta acabou sendo a melhor
-- das duas de qualquer forma.)
ALTER TABLE production_measurement DROP CONSTRAINT ck_production_measurement_kind;

ALTER TABLE production_measurement
    ADD CONSTRAINT ck_production_measurement_kind
        CHECK (kind IN ('DENSITY', 'TEMPERATURE', 'VOLUME', 'PH', 'COLOR', 'IBU', 'ABV'));
