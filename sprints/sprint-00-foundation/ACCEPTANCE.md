# Aceite — Sprint 00

Estado: **CONCLUÍDA com ressalvas registradas** (ver Débitos). Validado em checkout
limpo (`git clone` do remoto) em 2026-07-21.

## Repositório e governança

- [x] Provedor, proprietário, nome, visibilidade e branch principal foram confirmados. (GitHub · junicoVilela · brassia-platform · privado · main)
- [x] Repositório remoto correto existe; `origin` não contém credencial; `main` rastreia o remoto.
- [x] Histórico existente foi preservado; nenhum force-push, exclusão ou sobrescrita foi usado.
- [x] `.gitignore`, `.gitattributes`, `.editorconfig`, `.env.example` e README estão versionados; varredura não encontrou segredo.

## Projetos e arquitetura

- [x] Backend Spring Boot/Modulith e frontend Angular são projetos reais, iniciam e compilam pelos wrappers/lockfile.
- [x] Estrutura modular e feature-first corresponde aos documentos; testes reprovam dependências proibidas (Spring Modulith `verify`).
- [x] Versões efetivas correspondem ao baseline estável da sprint e estão reproduzíveis na CI (JDK 25, Boot 4.1, Modulith 2.1, Maven 3.9.16, PG 18, Angular 22, Node 24, Vitest).
- [x] Nenhuma funcionalidade da Sprint 01 ou infraestrutura sem necessidade foi antecipada.

## Ambiente e fundação transversal

- [x] PostgreSQL sobe com health check; migrations funcionam desde banco vazio (validado local e via Testcontainers na CI).
- [x] Perfis local/test/prod e adapter local de arquivos não expõem segredo nem comportamento de desenvolvimento em produção.
- [x] Health/readiness funcionam. Integração ponta-a-ponta frontend↔API (proxy) pendente — ver DEBT-FE-002.
- [x] Problem Details RFC 9457 possui código e `traceId`, sem stack trace, SQL ou dado sensível.
- [x] Logs estruturados (ECS), métricas (Prometheus) e porta de auditoria têm testes e política de mascaramento.

## Qualidade, CI e encerramento

- [x] Build, unitários, integração/Testcontainers, arquitetura, migration e contrato estão verdes. Lint de fronteira do frontend pendente — ver DEBT-FE-001.
- [x] Pipeline remoto executa os mesmos gates e a última execução está verde (run 29799869326).
- [x] Proteção de `main`: bloqueio externo real (plano) registrado com instrução manual — ver DEBT-CI-001.
- [x] Checkout/clone limpo reproduziu configuração, build e testes usando apenas conteúdo versionado (backend `mvnw verify` e frontend `npm ci`/`build`/`test` verdes em `/tmp/brassia-clean`).
- [x] OpenAPI, ADRs e documentação estão consistentes; `.ai/DEFINITION_OF_DONE.md` revisado (checklist formal completo pendente — ver DEBT-DOC-001).
- [x] `STATUS.md` contém commits, comandos, resultados e links/identificadores de evidência reais.
- [x] Commit final publicado sem reescrever histórico; débitos têm identificador e critério de remoção (abaixo).

## Débitos técnicos e ressalvas

| ID | Débito | Critério de remoção |
|---|---|---|
| ~~DEBT-CI-001~~ (RESOLVIDO 2026-07-21) | Proteção da `main` — repo tornado público (licença proprietária) e ruleset aplicado: PR + 4 checks + bloqueio de force-push/deleção. | — |
| DEBT-FE-001 | Frontend sem ESLint com regra de fronteira de import (só Prettier presente). | Adicionar ESLint + regra de camadas (core/features) reprovando import proibido; incluir no job de CI do frontend. |
| DEBT-FE-002 | Sem `proxy.conf.json` e sem verificação ponta-a-ponta frontend↔API. | Configurar proxy do dev server para a API (8081) e um teste/checagem da jornada de receitas. |
| DEBT-DOC-001 | `.ai/DEFINITION_OF_DONE.md` não percorrido formalmente item a item. | Executar o checklist completo e anexar evidência ao encerramento. |
