# Backlog — Sprint 12


## CST-001 — Custo realizado

**Objetivo:** Somar insumo, embalagem, utilidade, perda e mão de obra.

**Critérios específicos:**

- Origem de cada parcela é rastreável; snapshot fecha com lote.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## CST-002 — Planejado versus real

**Objetivo:** Explicar variação de preço, consumo, rendimento e perda.

**Critérios específicos:**

- Totais conciliam com ledger e snapshot.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## RPT-001 — Relatório do lote

**Objetivo:** Consolidar plano, execução, qualidade, custo e genealogia.

**Critérios específicos:**

- Filtro respeita permissão; exportação é auditada.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## UTL-001 — Água/energia/CO₂ por litro

**Objetivo:** Medir consumo por processo e produto envasado.

**Critérios específicos:**

- Indicador diferencia medido e estimado; período é reproduzível.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## RPT-002 — Dashboard operacional

**Objetivo:** Mostrar produção, estoque, desvios, fermentação e custo.

**Critérios específicos:**

- Indicadores têm definição, período e drill-down.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.
