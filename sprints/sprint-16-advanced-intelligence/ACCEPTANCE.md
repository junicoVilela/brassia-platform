# Aceite — Sprint 16

Os critérios desta sprint são de **processo**, e não de comportamento do produto: eles perguntam se a
sprint foi feita do jeito que a casa combinou. Cada um aponta o artefato que o sustenta.

- [x] **Todas as histórias selecionadas atendem critérios específicos.**
      6/6 entregues e mescladas, cada uma com evidência nominal na tabela do `STATUS.md`: DTW-001 (PR e
      `V103`), SPC-001, EXP-001 (#180, `V104`), BLD-001 (#181, `V110`), FLD-001 (#182, `V106`), OPT-001
      (#183, `V107`) — módulo, migration e tela citados por história.

- [x] **Nenhuma história posterior foi implementada parcialmente.**
      Uma história por vez, cada uma com seu PR e sua migration numerada em sequência. As decisões que
      apareceram no meio viraram registro (`DEC-DTW-001/002/003`, `DEC-SPC-001/002`, `DEC-EXP-001/002`,
      `DEC-BLD-001/002/003`, `DEC-FLD-001/002`, `DEC-OPT-001/002/003`), e não código adiantado.

- [x] **Testes de domínio, integração, autorização e tenant estão verdes.**
      `mvnw clean verify`: 1525 testes, incluindo `TenantIsolationTest`, `ModularityTest` e as migrations
      em banco limpo.

- [x] **OpenAPI, migrations, eventos e documentação estão consistentes.**
      O CI tem um check próprio para isso ("Contratos (schemas e OpenAPI)"), verde em todo PR desta
      sprint. As migrations `V103`–`V110` estão no histórico e são exercitadas em banco limpo a cada
      execução.
      *Nota de honestidade:* esse check garante que o documento **parseia** e bate com os schemas, e não
      que ele descreve o comportamento certo. Em 22/08 apareceu no `openapi.yaml` um bloco inteiro
      aninhado no endpoint errado (o corpo da recuperação de vasilhame dentro do `PUT` de políticas), e o
      check passava — YAML com chave duplicada é válido, e a última vence. Não é desta sprint, mas o
      limite do check vale para ela também.

- [x] **Frontend trata loading, vazio, erro, conflito e acesso negado.**
      As cinco features têm E2E contra a stack real: `digital-twin.spec.ts`, `experiments.spec.ts`,
      `blends.spec.ts`, `field-feedback.spec.ts`, `optimization.spec.ts`. **Esta sprint é a que fez
      certo** — as 18, 19 e 20 declararam entrega sem a jornada que o plano pedia, e a 18 ainda não tem
      (`DEB-COM-001`).
      Correção aplicada depois, em 22/08: `profile.store.ts` (digital twin) lia `e.error?.code` onde o
      interceptor entrega o campo no primeiro nível — o E2E existia, mas não cobria essa costura.

- [x] **Observabilidade permite localizar a operação por traceId.**
      `RequestTraceIdFilter` propaga o `X-Trace-Id` que veio, ou gera um, e o põe no MDC; de lá ele sai
      nos logs, nos eventos de auditoria e nas respostas de erro (`ProblemDetails`,
      `WebObservabilityConfiguration` cobre até o 401/403, que é escrito antes do controller). Quem
      recebe uma resposta de erro tem o número para procurar no log.

- [x] **`.ai/DEFINITION_OF_DONE.md` foi executado.**
      O arquivo existe e é a base do `AI_EXECUTION.md` da sprint. Desde 22/08 ele tem um portão a mais,
      escrito em `docs/17_SPRINT_WORKFLOW.md`: jornada E2E e revisão de código **antes** de declarar
      história entregue.

- [x] **Débitos e decisões restantes foram registrados, não escondidos em TODO.**
      As três pendências herdadas da Sprint 15 estão nomeadas no `STATUS.md` e **as três fecharam**:
      `DEB-INT-003` (MQTT contra broker HiveMQ real), `DEB-SEC-001` (OIDC e SAML contra Keycloak real) e
      `SEN-002`. E a afirmação é conferível: **zero `TODO` e zero `FIXME`** em `backend/src/main/java` e
      em `frontend/src`. Os registros vivem no `STATUS.md`, com efeito e critério de remoção.
