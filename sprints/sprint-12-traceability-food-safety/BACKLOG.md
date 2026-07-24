# Backlog — Sprint 12


## TRC-001 — Genealogia completa

**Objetivo:** Ligar insumo, OP, lote, blend e embalagem.

**Critérios específicos:**

- Consulta funciona para trás e frente; ausência de elo é evidenciada.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## FDS-001 — Matriz de alergênicos

**Objetivo:** Mapear ingrediente, equipamento compartilhado e limpeza.

**Critérios específicos:**

- Troca de produto exige procedimento compatível; rótulo é revalidado.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## FDS-002 — Quarentena

**Objetivo:** Bloquear lote e descendentes durante investigação.

**Critérios específicos:**

- Envase/expedição são impedidos; liberação exige alçada.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## FDS-003 — Abrir recall

**Objetivo:** Identificar origem, destinos, contatos e ações.

**Critérios específicos:**

- Escopo é reproduzível; toda comunicação e decisão é auditada.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## FDS-004 — Simulado de recall

**Objetivo:** Medir tempo, cobertura e lacunas sem afetar estoque real.

**Critérios específicos:**

- Relatório apresenta percentual localizado e ações corretivas.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.
