# Backlog — Sprint 09


## PKG-001 — Planejar envase

**Objetivo:** Definir embalagem, quantidade, linha e checklist.

**Critérios específicos:**

- Disponibilidade e limpeza são verificadas; lote de embalagem reservado.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## GAS-001 — Rede e cilindros

**Objetivo:** Rastrear cilindro, regulador, manifold, pressão e consumo.

**Critérios específicos:**

- Cilindro vencido/bloqueado não é alocado; teste de vazamento registrado.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## PKG-002 — Carbonatação

**Objetivo:** Calcular priming ou pressão forçada com método versionado.

**Critérios específicos:**

- Temperatura e CO₂ residual entram no cálculo; confirmação obrigatória.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## PKG-003 — Executar envase

**Objetivo:** Registrar unidades, volume, perdas, rejeitos e embalagem.

**Critérios específicos:**

- Balanço de volume fecha; consumo de embalagem vira movimento.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## FSL-001 — Oxigênio e validade

**Objetivo:** Registrar DO/TPO, purga, vedação e plano de vida útil.

**Critérios específicos:**

- Validade é recomendada com evidência; override é auditado.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.
