# Backlog — Sprint 17


## REL-001 — Teste de restauração

**Objetivo:** Restaurar banco e arquivos em ambiente isolado.

**Critérios específicos:**

- RPO/RTO medidos; procedimento reproduzível e auditado.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## REL-002 — Performance

**Objetivo:** Medir jornadas críticas e corrigir gargalos reais.

**Critérios específicos:**

- Metas NFR atendidas com dataset representativo.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## REL-003 — Segurança

**Objetivo:** Executar revisão OWASP, tenant, segredo, upload e dependências.

**Critérios específicos:**

- Achados críticos/altos resolvidos ou release bloqueado.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## REL-004 — Migração e rollback operacional

**Objetivo:** Ensaiar deploy, migration e retorno de aplicação.

**Critérios específicos:**

- Runbook testado; forward-fix de banco documentado.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## REL-005 — Aceite do usuário

**Objetivo:** Executar ciclo completo de produção em homologação.

**Critérios específicos:**

- Evidências anexadas; bloqueadores encerrados; manual mínimo entregue.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.
