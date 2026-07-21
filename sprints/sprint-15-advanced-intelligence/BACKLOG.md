# Backlog — Sprint 15


## DTW-001 — Perfil aprendido

**Objetivo:** Estimar eficiência, perdas e tempos com faixa de confiança.

**Critérios específicos:**

- Modelo é versionado; poucos dados geram baixa confiança explícita.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## SPC-001 — Controle estatístico

**Objetivo:** Detectar tendência e variação fora do padrão.

**Critérios específicos:**

- Limite de controle não é confundido com especificação.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## EXP-001 — Lote dividido

**Objetivo:** Planejar hipótese, controle, variante, medições e sensorial.

**Critérios específicos:**

- Uma variável pode ser isolada; conclusão registra limitações.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## BLD-001 — Blend/reprocesso

**Objetivo:** Simular, aprovar e executar união/divisão mantendo genealogia.

**Critérios específicos:**

- Balanço fecha; rótulo, alergênico e recall são recalculados.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## FLD-001 — Feedback de campo

**Objetivo:** Relacionar reclamação, armazenamento e amostra ao lote.

**Critérios específicos:**

- Severidade pode abrir CAPA/quarentena; dados pessoais são protegidos.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.
