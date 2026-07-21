# Backlog — Sprint 14


## INT-001 — Ingestão de sensor

**Objetivo:** Receber densidade, temperatura, pressão e vazão.

**Critérios específicos:**

- Mensagem duplicada é idempotente; qualidade e atraso são sinalizados.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## INT-002 — Webhooks

**Objetivo:** Publicar eventos assinados com retry controlado.

**Critérios específicos:**

- Falha não bloqueia domínio; destino e tentativas são auditados.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## PWA-001 — Roteiro offline

**Objetivo:** Disponibilizar OP, checklist e etapas essenciais.

**Critérios específicos:**

- Sem rede, leitura funciona; dados sensíveis seguem protegidos.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## PWA-002 — Fila offline

**Objetivo:** Registrar apontamentos e sincronizar com idempotência.

**Critérios específicos:**

- Conflito não sobrescreve silenciosamente; usuário resolve quando preciso.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## INT-003 — QR code

**Objetivo:** Abrir equipamento, lote, OP e embalagem.

**Critérios específicos:**

- Código não concede acesso; autorização é verificada após leitura.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.
