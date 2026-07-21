# Definition of Done — Sprint 00 (percorrido)

Checklist de `.ai/DEFINITION_OF_DONE.md` avaliado item a item contra as entregas
da Sprint 00 (foundation). Resolve **DEBT-DOC-001**. Marcações: `[x]` atendido,
`[~]` parcial, `[N/A]` não se aplica à foundation (chega em histórias de produto
a partir da Sprint 01). Evidências detalhadas em `STATUS.md` e `ACCEPTANCE.md`.

- [x] **História e regra de negócio atendidas sem escopo lateral.** FND-000..007 concluídas (ver tabela do `STATUS.md`); nenhuma ampliação de escopo além do baseline de fundação.
- [x] **Caso de uso e domínio não dependem de framework.** Domínio `recipe` puro; fronteiras validadas por `ModularityTest` (Spring Modulith) e `RecipeTest`.
- [x] **Testes unitários cobrem invariantes, limites e transições inválidas.** 6 testes backend (domínio, mascaramento, auditoria) + 3 frontend (store, contrato da API, app) verdes.
- [x] **Teste de integração usa PostgreSQL/Testcontainers.** `ApplicationContextIT` sobe PostgreSQL 18 via Testcontainers, aplica as migrations V1/V2 e valida `ddl validate` + wiring.
- [N/A] **Autorização e isolamento multi-tenant possuem testes negativos.** Multi-tenant (`brewery_id`) é escopo da Sprint 01. Base de segurança já coberta: 401/403 em `application/problem+json` validados (FND-004).
- [x] **Migration, índices e constraints revisados.** V1 (recipe) e V2 (spring session) revisadas e aplicadas em PG18 real (`success=t`); tabelas conferidas.
- [x] **OpenAPI, exemplos e Problem Details RFC 9457 atualizados.** `contracts/*.json` e OpenAPI validados no job `contracts` da CI; Problem Details RFC 9457 com `code`/`traceId`, sem stack/SQL (FND-004).
- [x] **Auditoria, logs e métricas não expõem dados sensíveis.** `SensitiveDataMasker` + `LoggingAuditTrailTest` (não vaza segredo, mascara `token=***`); métricas Prometheus com exposição mínima (FND-005).
- [~] **UI possui estados de carregamento, vazio, erro, sucesso e acessibilidade.** `RecipesStore` expõe `loading`/`empty`/`error`; `recipe-list-page` renderiza os estados. Auditoria formal de acessibilidade (a11y) fica para as telas de produto da Sprint 01.
- [~] **Fluxo principal E2E aprovado.** Jornada frontend↔API coberta por proxy de dev (`proxy.conf.json`) + teste de contrato do endpoint `/api/v1/recipes` (DEBT-FE-002). E2E de navegador (Playwright) será adotado junto ao primeiro fluxo de produto.
- [x] **Sem segredo, TODO sem issue, código morto ou supressão injustificada.** `gitleaks` verde no histórico (job `secrets`); nenhum `.env`/segredo versionado; sem supressões de lint (fronteira de camadas ativa — DEBT-FE-001).
- [x] **Documentação e ADR atualizados quando houve decisão.** ADRs, `STATUS.md`, `ACCEPTANCE.md` e este DoD atualizados; decisões temporárias/débitos com identificador e critério de remoção.

## Resumo

Itens aplicáveis à foundation: **atendidos**. Dois itens `[~]` (a11y formal e E2E
de navegador) e um `[N/A]` (multi-tenant) são intrinsecamente de histórias de
produto e entram na Sprint 01 — registrados aqui para rastreio, não como débito
aberto da Sprint 00.
