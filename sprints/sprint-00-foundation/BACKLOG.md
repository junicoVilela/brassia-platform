# Backlog — Sprint 00


## FND-000 — Repositório Git remoto e governança

**Objetivo:** Coletar os dados mínimos, criar ou vincular com segurança o repositório remoto, inicializar Git e publicar o commit de bootstrap.

**Critérios específicos:**

- Remote `origin`, branch `main`, visibilidade, licença e proprietário correspondem à decisão do usuário; nenhum segredo é versionado; não há force-push nem sobrescrita de histórico.

- Execução preserva trabalho existente, usa versões fixas e não expõe credenciais.
- Procedimento funciona pelos wrappers/lockfiles e produz evidência verificável.
- Falha ou permissão externa ausente é registrada como bloqueio real, nunca como conclusão presumida.
- Nenhuma funcionalidade da Sprint 01 é implementada antecipadamente.



## FND-001 — Projetos iniciais e estrutura modular

**Objetivo:** Gerar o backend Spring Modulith e o frontend Angular feature-first reais, com fronteiras verificáveis e scripts de execução.

**Critérios específicos:**

- Backend e frontend são projetos executáveis, não apenas exemplos; Spring Modulith/ESLint reprovam dependência proibida; build completo fica verde.

- Execução preserva trabalho existente, usa versões fixas e não expõe credenciais.
- Procedimento funciona pelos wrappers/lockfiles e produz evidência verificável.
- Falha ou permissão externa ausente é registrada como bloqueio real, nunca como conclusão presumida.
- Nenhuma funcionalidade da Sprint 01 é implementada antecipadamente.



## FND-002 — Ambiente local

**Objetivo:** Disponibilizar PostgreSQL e adapter local de arquivos.

**Critérios específicos:**

- Subida documentada; health checks aprovados; filesystem é restrito a dev; nenhum segredo versionado.

- Execução preserva trabalho existente, usa versões fixas e não expõe credenciais.
- Procedimento funciona pelos wrappers/lockfiles e produz evidência verificável.
- Falha ou permissão externa ausente é registrada como bloqueio real, nunca como conclusão presumida.
- Nenhuma funcionalidade da Sprint 01 é implementada antecipadamente.



## FND-003 — Pipeline CI

**Objetivo:** Executar build, lint, unitários, integração, migrations e contrato.

**Critérios específicos:**

- Falha em qualquer gate bloqueia merge; cache não mascara teste.

- Execução preserva trabalho existente, usa versões fixas e não expõe credenciais.
- Procedimento funciona pelos wrappers/lockfiles e produz evidência verificável.
- Falha ou permissão externa ausente é registrada como bloqueio real, nunca como conclusão presumida.
- Nenhuma funcionalidade da Sprint 01 é implementada antecipadamente.



## FND-004 — Contrato de erros

**Objetivo:** Implementar Problem Details RFC 9457 com código e traceId.

**Critérios específicos:**

- Validação, conflito, não autorizado e erro interno possuem exemplos seguros.

- Execução preserva trabalho existente, usa versões fixas e não expõe credenciais.
- Procedimento funciona pelos wrappers/lockfiles e produz evidência verificável.
- Falha ou permissão externa ausente é registrada como bloqueio real, nunca como conclusão presumida.
- Nenhuma funcionalidade da Sprint 01 é implementada antecipadamente.



## FND-005 — Auditoria e observabilidade base

**Objetivo:** Criar trace, log estruturado, audit port e métricas.

**Critérios específicos:**

- Comando crítico gera auditoria; log não contém token ou payload sensível.

- Execução preserva trabalho existente, usa versões fixas e não expõe credenciais.
- Procedimento funciona pelos wrappers/lockfiles e produz evidência verificável.
- Falha ou permissão externa ausente é registrada como bloqueio real, nunca como conclusão presumida.
- Nenhuma funcionalidade da Sprint 01 é implementada antecipadamente.



## FND-006 — Baseline de versões

**Objetivo:** Fixar Java 25 LTS, Boot 4.1, Modulith 2.1, Maven 3.9.16, PostgreSQL 18, Angular 22 e Node 24 LTS.

**Critérios específicos:**

- Wrappers/lockfile reproduzem a CI; nenhuma versão preview ou intervalo inseguro é usado.

- Execução preserva trabalho existente, usa versões fixas e não expõe credenciais.
- Procedimento funciona pelos wrappers/lockfiles e produz evidência verificável.
- Falha ou permissão externa ausente é registrada como bloqueio real, nunca como conclusão presumida.
- Nenhuma funcionalidade da Sprint 01 é implementada antecipadamente.



## FND-007 — Validação reproduzível e encerramento

**Objetivo:** Validar a entrega a partir de checkout limpo, atualizar evidências, criar o commit final e publicar a Sprint 00.

**Critérios específicos:**

- Clone/checkout limpo reproduz build, testes, migrations e subida local; CI remota fica verde; commits são enviados sem reescrever histórico; STATUS e aceite registram evidências reais.

- Execução preserva trabalho existente, usa versões fixas e não expõe credenciais.
- Procedimento funciona pelos wrappers/lockfiles e produz evidência verificável.
- Falha ou permissão externa ausente é registrada como bloqueio real, nunca como conclusão presumida.
- Nenhuma funcionalidade da Sprint 01 é implementada antecipadamente.
