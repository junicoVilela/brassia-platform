# Backlog — Sprint 07


## CLN-001 — POP versionado

**Objetivo:** Cadastrar sequência, produto, limites, EPIs, alternativa e proibição.

**Critérios específicos:**

- Ciclo referencia versão; parâmetro fora da ficha é bloqueado.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## CLN-002 — Matriz por equipamento

**Objetivo:** Recomendar método por material, risco, sujidade e produto anterior.

**Critérios específicos:**

- Alternativa explica restrições; madeira/plástico não herdam inox.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## CLN-003 — Executar ciclo

**Objetivo:** Registrar tempo, temperatura, vazão, química e evidências.

**Critérios específicos:**

- Etapas fora de ordem exigem motivo; interrupção é preservada.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## CLN-004 — Verificar e liberar

**Objetivo:** Validar enxágue, visual, ATP/micro e aprovação.

**Critérios específicos:**

- Sanitização não passa com limpeza reprovada; liberação é auditada.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## CLN-005 — Consumo e otimização

**Objetivo:** Medir água, energia e produto por ciclo.

**Critérios específicos:**

- Comparação não reduz parâmetro sem nova versão aprovada.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.
