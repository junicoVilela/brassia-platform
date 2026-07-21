# Backlog — Sprint 04


## PLN-001 — Agenda de produção

**Objetivo:** Planejar receita, volume, equipamento, equipe e data.

**Critérios específicos:**

- Conflito de equipamento é sinalizado; simulação não altera estado.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## PLN-002 — Necessidade de materiais

**Objetivo:** Explodir receita e perdas em necessidade por item.

**Critérios específicos:**

- Unidades são convertidas; faltas aparecem sem reservar.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## BOP-001 — Criar ordem

**Objetivo:** Gerar OP com snapshot e código único.

**Critérios específicos:**

- Somente receita publicada; snapshot contém cálculo/equipamento.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## BOP-002 — Liberar ordem

**Objetivo:** Validar equipamento, estoque, sanitização e responsável.

**Critérios específicos:**

- Falha lista bloqueios; sucesso emite evento e auditoria.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## BOP-003 — Cancelar ordem

**Objetivo:** Cancelar com motivo e liberar reservas.

**Critérios específicos:**

- Ordem iniciada segue regra específica; operação é idempotente.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.
