# Backlog — Sprint 07


## PRD-001 — Iniciar lote

**Objetivo:** Criar Batch e roteiro a partir da OP liberada.

**Critérios específicos:**

- Transição única; snapshot preservado; reservas confirmadas.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## PRD-002 — Modo passo a passo

**Objetivo:** Exibir etapa, meta, tolerância, instrução e cronômetro.

**Critérios específicos:**

- Retomada mantém estado; avanço inválido é bloqueado.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## PRD-003 — Registrar medição

**Objetivo:** Guardar valor, unidade, temperatura, método, origem e operador.

**Critérios específicos:**

- Medição é imutável; unidade incompatível é rejeitada.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## PRD-004 — Correções determinísticas

**Objetivo:** Calcular diluição, concentração, temperatura, volume e sais.

**Critérios específicos:**

- Impactos são mostrados; nenhuma correção é aplicada sem confirmação.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## PRD-005 — Transferência ao fermentador

**Objetivo:** Registrar volume, OG, perdas e destino.

**Critérios específicos:**

- Capacidade do destino e balanço de massa são verificados.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.
## PRD-006 — Central de alertas, cronômetros e ações

**Objetivo:** Reunir adições, etapas, medições atrasadas e decisões pendentes em uma linha do tempo operacional.

**Critérios específicos:**

- Alertas sobrevivem a recarga e reconexão.
- Atraso mostra horário planejado, realizado e impacto, sem avançar etapa sozinho.
- Confirmação é idempotente e auditada.
- Notificação não revela dados de outra cervejaria em dispositivo compartilhado.

## CAL-002 — Calculadoras de correção durante a execução

**Objetivo:** Aplicar diluição, concentração, água de reposição, tempo de fervura e ajustes de densidade de forma segura.

**Critérios específicos:**

- Reutiliza o motor e os métodos versionados da Sprint 04.
- Mostra medição de origem, hipótese, quantidade, limites e efeito estimado.
- Nenhuma ação física é executada automaticamente.
- Aplicação gera evento e preserva planejado versus realizado.
