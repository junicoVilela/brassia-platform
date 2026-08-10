-- Contagem EXATA por tabela, para comparar origem e restauração (REL-001).
--
-- Por que não `pg_stat_user_tables.n_live_tup`: aquilo é estimativa mantida pelo autovacuum, e num banco
-- recém-restaurado vem ZERADA até alguém rodar ANALYZE. O ensaio reportaria divergência em todas as
-- tabelas, sempre — e um alarme que sempre dispara é um alarme que se aprende a ignorar.
--
-- `query_to_xml` executa um COUNT(*) real por tabela dentro de uma única consulta, sem precisar de
-- procedimento nem de laço no shell.
SELECT c.relname || '|' ||
       (xpath('/row/cnt/text()',
              query_to_xml(format('SELECT count(*) AS cnt FROM %I.%I', n.nspname, c.relname),
                           false, true, '')))[1]::text::bigint
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE c.relkind = 'r'
  AND n.nspname = 'public'
ORDER BY c.relname;
