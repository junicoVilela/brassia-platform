-- REL-002 — Dataset representativo para medição de performance.
--
-- POR QUE ISTO EXISTE. O critério pede "metas NFR atendidas com dataset representativo", e a palavra
-- que carrega o critério é a última. Medir p95 contra banco vazio produz número bonito e inútil: todo
-- índice cabe em memória, todo plano vira sequential scan barato, e a primeira semana de produção
-- desmente a medição.
--
-- O QUE ELE NÃO É. Não é dado realista de negócio — os valores são sintéticos e não fazem sentido
-- cervejeiro. É volume e DISTRIBUIÇÃO: muitos lotes, muitas medições por lote, muitas leituras de
-- sensor, muita auditoria. São as tabelas que crescem sem teto, e são elas que decidem se a consulta
-- que hoje responde em 20 ms vai responder em 2 s no ano que vem.
--
-- COMO ESCALAR. Ajuste :lotes e :medicoes_por_lote na chamada:
--   psql ... -v lotes=500 -v medicoes_por_lote=200 -v leituras=200000 -f seed-representative-dataset.sql
--
-- IDEMPOTÊNCIA. Rodar de novo ACRESCENTA volume, não substitui. É deliberado: crescer o dataset em
-- passos e remedir é como se acha o joelho da curva, que é a informação que interessa.

\set lotes :lotes
\set medicoes_por_lote :medicoes_por_lote
\set leituras :leituras

\echo 'Semeando dataset representativo...'

-- Âncora: a cervejaria semeada pelas migrations. O dataset inteiro pendura nela, e a medição passa a
-- exercitar o filtro por brewery_id que TODA consulta do sistema tem — que é o índice que mais importa.
CREATE TEMP TABLE _anchor AS
SELECT id AS brewery_id FROM brewery ORDER BY name LIMIT 1;

-- --- Lotes ---------------------------------------------------------------------------------------
-- `started_at` espalhado por dois anos: consultas com janela temporal só mostram o custo real quando
-- há dado FORA da janela para o índice descartar.
INSERT INTO production_batch (id, brewery_id, order_id, code, recipe_id, recipe_version, recipe_name,
                              volume_liters, status, started_at, started_by)
SELECT gen_random_uuid(), a.brewery_id, gen_random_uuid(),
       'PERF-' || to_char(now(), 'YYYYMMDDHH24MISS') || '-' || g,
       gen_random_uuid(), 1, 'Receita de carga ' || (g % 40),
       400 + (g % 200), 'IN_PROGRESS',
       now() - (random() * interval '730 days'),
       gen_random_uuid()
FROM _anchor a, generate_series(1, :lotes) g;

\echo '  lotes inseridos'

-- --- Medições ------------------------------------------------------------------------------------
-- A tabela mais consultada do sistema: alimenta carta de controle (SPC-001), perfil aprendido
-- (DTW-001) e o acompanhamento do lote. É onde um índice faltando aparece primeiro.
INSERT INTO production_measurement (id, brewery_id, batch_id, kind, measured_value, unit,
                                    recorded_at, recorded_by, source)
SELECT gen_random_uuid(), b.brewery_id, b.id,
       (ARRAY['TEMPERATURE','DENSITY','PH','VOLUME'])[1 + (m % 4)],
       CASE (m % 4) WHEN 0 THEN 18 + (random() * 6)
                    WHEN 1 THEN 1.010 + (random() * 0.05)
                    WHEN 2 THEN 4.0 + (random() * 1.5)
                    ELSE 380 + (random() * 40) END,
       (ARRAY['C','SG','PH','L'])[1 + (m % 4)],
       b.started_at + (m * interval '2 hours'),
       gen_random_uuid(), 'MANUAL'
FROM production_batch b, generate_series(1, :medicoes_por_lote) m
WHERE b.code LIKE 'PERF-%';

\echo '  medições inseridas'

-- --- Auditoria -----------------------------------------------------------------------------------
-- Cresce mais rápido que tudo e nunca é apagada. Se a consulta de auditoria degrada, degrada calada:
-- ninguém abre a tela de auditoria todo dia, e o problema aparece na inspeção — que é o pior momento.
INSERT INTO audit_event (id, brewery_id, actor_id, action, target_type, target_id, outcome,
                         change_summary, occurred_at)
SELECT gen_random_uuid(), a.brewery_id, gen_random_uuid(),
       (ARRAY['production.measurement.record','recipe.publish','inventory.lot.consume',
              'security.login','blend.operation.execute'])[1 + (g % 5)],
       'perf_load', gen_random_uuid(), 'SUCCESS', '{}'::jsonb,
       now() - (random() * interval '730 days')
FROM _anchor a, generate_series(1, :leituras) g;

\echo '  auditoria inserida'

-- ANALYZE é parte do dataset, não etapa opcional: sem estatísticas atualizadas o planejador escolhe
-- planos que não escolheria em produção, e a medição mede o planejador desinformado em vez do sistema.
ANALYZE;

SELECT 'production_batch' AS tabela, count(*) FROM production_batch
UNION ALL SELECT 'production_measurement', count(*) FROM production_measurement
UNION ALL SELECT 'audit_event', count(*) FROM audit_event
ORDER BY 1;
