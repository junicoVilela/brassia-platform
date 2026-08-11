-- FDS-004-A — a ação corretiva do simulado deixa de ser texto e vira item de CAPA.
--
-- O CRITÉRIO DE REMOÇÃO, LITERAL: "o CAPA publicar porta de abertura de ação, e o encerramento do simulado
-- passar a criar as ações lá em vez de escrevê-las como texto". É o que esta migration sustenta.
--
-- POR QUE O TEXTO NÃO BASTAVA. "revisar contatos dos distribuidores" escrito no relatório de um simulado
-- não tem dono, não tem prazo e não aparece em lista nenhuma. Seis meses depois, o próximo simulado
-- encontra a mesma lacuna e o relatório anterior está lá dizendo o que fazer — o que é a definição de um
-- exercício que não melhora nada. Item de CAPA tem dono, prazo e cobrança.
--
-- O QUE NÃO FOI FEITO, E POR QUÊ. O simulado NÃO abre a não conformidade sozinho. Fazer isso exigiria o
-- sistema decidir a severidade, e se 75% de cobertura é grave depende do produto e de quem audita —
-- inventar severidade é o tipo de regra de negócio que o `AGENTS.md` proíbe. Quem encerra escolhe uma NC
-- aberta ou abre uma na hora, declarando a severidade.
ALTER TABLE traceability_recall_drill
    ADD COLUMN non_conformity_id UUID REFERENCES quality_non_conformity (id);

-- O texto continua aceito e continua nulo na maioria dos casos: simulado sem lacuna não gera ação, e
-- simulados antigos têm texto que não se reescreve. O que muda é que, havendo ação a tomar, ela passa a
-- existir como item de CAPA — e o CHECK impede o estado que confundiria quem lê o relatório: ação escrita
-- como texto E vinculada a uma NC ao mesmo tempo, sem saber qual das duas é a de verdade.
ALTER TABLE traceability_recall_drill
    ADD CONSTRAINT ck_drill_corrective_actions CHECK (
        corrective_actions IS NULL OR non_conformity_id IS NULL
    );

CREATE INDEX ix_drill_non_conformity ON traceability_recall_drill (brewery_id, non_conformity_id)
    WHERE non_conformity_id IS NOT NULL;

-- A EXISTÊNCIA DO LOTE DA NC PASSA A SER GARANTIDA PELO BANCO (DEB-AIA-003 revisitado).
--
-- A V112 deixou `quality_non_conformity.batch_id` sem chave estrangeira, e a checagem vivia no handler,
-- consultando a produção. Isso deu ao módulo de qualidade uma dependência de `production` — e quando o
-- simulado passou a chamar o CAPA, fechou o ciclo `production → traceability → quality → production`,
-- que o `ModularityTest` recusa.
--
-- A troca não é só para satisfazer o teste: **checagem prévia não é garantia**. Duas requisições
-- simultâneas passariam as duas por ela, e um lote cancelado entre a checagem e o INSERT deixaria a NC
-- apontando para o nada. Quem garante é a restrição; o handler traduz o erro.
ALTER TABLE quality_non_conformity
    ADD CONSTRAINT fk_quality_nc_batch FOREIGN KEY (batch_id) REFERENCES production_batch (id);
