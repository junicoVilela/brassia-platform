# Backlog — Sprint 08


## FER-001 — Perfil de fermentação

**Objetivo:** Configurar estágios, rampas, pressão e critérios.

**Critérios específicos:**

- Avanço usa condição e confirmação; histórico não é reescrito.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## FER-002 — Leituras e curvas

**Objetivo:** Registrar densidade, temperatura, pressão e pH.

**Critérios específicos:**

- Gráfico diferencia manual/sensor; leituras inválidas são sinalizadas.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## FER-003 — Estabilidade de FG

**Objetivo:** Avaliar janela e tolerância configuráveis.

**Critérios específicos:**

- Resultado explica leituras usadas; não encerra automaticamente.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## YST-001 — Coletar levedura

**Objetivo:** Registrar origem, geração, condição, viabilidade e armazenamento.

**Critérios específicos:**

- Genealogia é completa; coleta reprovada não fica disponível.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## YST-002 — Recomendar reutilização

**Objetivo:** Combinar idade, geração, testes e desempenho.

**Critérios específicos:**

- Recomendação é explicável; uso exige confirmação e lote vinculado.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.
