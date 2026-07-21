# Status — Sprint 00

Estado: EM ANDAMENTO (preparação da fundação)

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| FND-000 | Concluída | Claude/junico | `github.com/junicoVilela/brassia-platform` (privado), `main` publicada | Proteção de branch fica para depois da CI (FND-003) |
| FND-001 | Concluída | Claude/junico | backend 3 testes verdes; frontend build+2 testes verdes | Angular 22 real gerado e integrado |
| FND-002 | Concluída | Claude/junico | migrations aplicadas em PG18 real; app sobe; health UP | Perfis local/test/prod criados; ver evidências |
| FND-003 | Parcial | Claude/junico | CI verde (4 jobs); proteção da main BLOQUEADA (plano) | Ver bloqueio abaixo |
| FND-004 | Concluída | Claude/junico | 401/403/erros em problem+json validados via curl | Problem Details RFC 9457 + traceId |
| FND-005 | Concluída | Claude/junico | prometheus 200; ECS JSON; 6 testes verdes | Auditoria + observabilidade + mascaramento |
| FND-006 | Concluída | Claude/junico | ver evidências abaixo | Baseline fixado e validado |
| FND-007 | A fazer | — | — | — |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

### FND-006 — Baseline de versões (validado em 2026-07-20)

- Toolchain confirmado: JDK **Temurin 25.0.3 LTS** (via SDKMAN), Maven Wrapper **3.9.16**, Node **24.15.0**, Docker 29, PostgreSQL 18 (imagem do compose).
- `backend/mvnw` + `.mvn/wrapper/maven-wrapper.properties` gerados (Maven 3.9.16).
- `frontend/.nvmrc` fixado em `24.15.0`.
- Correções aplicadas ao `pom.xml` do scaffold (necessárias para o baseline resolver/compilar):
  1. Testcontainers 2.0.x renomeou o módulo: `org.testcontainers:postgresql` → `org.testcontainers:testcontainers-postgresql`.
  2. Adicionado repositório **Shibboleth** (`build.shibboleth.net/maven/releases`), pois os artefatos OpenSAML 5.2.2 (transitivos de `spring-security-saml2-service-provider`) não estão no Maven Central.
- Correção de fronteira Modulith: criado `shared/package-info.java` declarando `shared` como módulo `OPEN` (capacidades técnicas compartilhadas), resolvendo a violação `recipe → shared.security.SecurityPrincipal`.

### Evidência de execução (backend)

- `./mvnw -B clean compile` → BUILD SUCCESS.
- `./mvnw -B test` → `Tests run: 3, Failures: 0, Errors: 0` (ModularityTest, RecipeTest x2). BUILD SUCCESS.
- JDK usado: `openjdk 25.0.3 Temurin-25.0.3+9-LTS`.

### FND-001 — Frontend Angular 22 (validado em 2026-07-21)

- Projeto real gerado com Angular CLI **22.0.7** (`@angular/build`), **zoneless** (sem zone.js), TypeScript 6.0, **Vitest** como runner padrão (`@angular/build:unit-test` + jsdom).
- Estrutura feature-first preservada do scaffold: `core/http` (ProblemDetails RFC 9457 + interceptor), `features/recipes` (domain, data-access com Signals, page). Shell `App` standalone com `<router-outlet />`; rota `recipes` com lazy-load.
- `app.config.ts` compõe `provideZonelessChangeDetection`, `provideRouter`, `provideHttpClient(withInterceptors([...]))` e `provideBrowserGlobalErrorListeners`.
- `package-lock.json` versionado; `styles.scss` importa os tokens.
- Evidência: `npm install` (459 pacotes), `npm run build` → bundle OK com lazy chunks `recipe-list-page` e `recipes-routes`; `npm test` → **2 arquivos, 2 testes verdes** (Vitest).
- Pendente (refinamento): ESLint com regra de fronteira de import e `proxy.conf.json` para a API.

### FND-003 — Pipeline CI (verde em 2026-07-21)

- Workflow `.github/workflows/ci.yml` com 4 jobs, permissão mínima (`contents: read`), `concurrency` e caches por lockfile:
  - **backend**: `actions/setup-java@v4` Temurin **25** + `./mvnw -B verify` — unitários (surefire), verificação Spring Modulith e `ApplicationContextIT` (Testcontainers **PostgreSQL 18**: migrations aplicam em banco limpo + `ddl validate` + wiring completo).
  - **frontend**: `actions/setup-node@v5` (via `.nvmrc`) + `npm ci` + `npm run build` + `npm test` (Vitest).
  - **contracts**: valida `contracts/*.json` (json.tool) e `*.yaml` (OpenAPI).
  - **secrets**: `gitleaks` (imagem pinada `v8.21.2`) no histórico.
- Evidência: run **success** — https://github.com/junicoVilela/brassia-platform/actions/runs/29799869326 (backend 55s, frontend 38s).
- `.github/dependabot.yml`: atualizações semanais de maven, npm e github-actions.

**BLOQUEADO — Proteção da branch `main`.** A API de rulesets/branch protection retorna **HTTP 403**: "Upgrade to GitHub Pro or make this repository public to enable this feature." O plano Free não permite proteção em repositório **privado**. Desbloqueio (decisão do usuário), qualquer um destes:
1. Assinar **GitHub Pro** e então rodar o ruleset (PR + 4 checks + bloqueio de force-push/deleção). O JSON pronto está em `/tmp/ruleset.json` / posso recriá-lo.
2. Tornar o repositório **público** (proteção fica gratuita) — exige decidir licença antes.
3. Manter sem proteção por ora (disciplina de PR manual).

### FND-000 — Repositório remoto (criado em 2026-07-21)

- Provedor GitHub, conta **junicoVilela**, repositório **`brassia-platform`**, visibilidade **privada**, descrição "BrassIA — Plataforma inteligente de gestão cervejeira".
- `origin` via HTTPS sem credencial embutida; `main` publicada e rastreando `origin/main`; local e remoto no mesmo commit.
- Nenhum segredo versionado (varredura de `.env`, chaves e tokens antes do push). Sem force-push nem reescrita de histórico.
- Pendente: proteção de `main` (PR + CI verde + bloqueio de force-push), a configurar quando os checks existirem em FND-003.

### FND-005 — Auditoria e observabilidade (validado em 2026-07-21)

- **traceId** centralizado em `shared/observability/Trace` (MDC); `RequestTraceIdFilter` e `ProblemDetails` passam a usá-lo. Presente no header `X-Trace-Id`, nas respostas de erro e no padrão de log (`%5p [traceId=%X{traceId:-}]`).
- **Logs estruturados**: perfil `prod` emite ECS JSON (`logging.structured.format.console: ecs`); verificado com override no boot local (linhas JSON com `@timestamp`, `log.level`, `service.name`, `ecs.version`).
- **Métricas**: dependência `micrometer-registry-prometheus`; `GET /actuator/prometheus` → **200**, formato Prometheus, 131 métricas (inclui `jvm_info` runtime Temurin 25). Exposição mínima: só `health`, `info` e `prometheus` são públicos; demais endpoints exigem autenticação.
- **Auditoria**: módulo `audit` com porta `AuditTrail` + valor `AuditEvent` (exposed) e adapter `LoggingAuditTrail` (logger dedicado `AUDIT`, append-only). Fiado no comando crítico `recipe.create` (`CreateRecipeHandler`). Módulo Modulith verde com a dependência `recipe → audit`.
- **Mascaramento**: `SensitiveDataMasker` (senha, token, authorization, cookie, secret, pepper, apikey…); aplicado nos metadados de auditoria.
- Testes (6 verdes): `SensitiveDataMaskerTest` (leak), `LoggingAuditTrailTest` (não vaza segredo, mascara `token=***`), `CreateRecipeHandlerTest` (comando gera auditoria SUCCESS), além de Modulith e domínio.

### FND-002 — Ambiente local (validado em 2026-07-20)

- `compose.yaml` corrigido para o PostgreSQL 18: volume em `/var/lib/postgresql` (novo layout do PG18) e porta host **5433** (a 5432 já é usada por outro projeto local — `softon-postgres`, preservado).
- Perfis criados: `application-local.yml` (PG 5433, porta 8081, filesystem), `application-test.yml` (Testcontainers), `application-prod.yml` (dirigido por env, cookie `__Host-` seguro, storage S3, sem defaults inseguros).
- Migrations Flyway aplicadas em banco **PostgreSQL 18.4 real** desde vazio: V1 (recipe) e V2 (spring session), ambas `success=t`; tabelas `recipe`, `spring_session`, `spring_session_attributes`, `flyway_schema_history`. Hibernate `ddl-auto: validate` passou.
- App sobe com perfil `local`: `Started BrassiaApplication` em ~4s; `GET /actuator/health` → 200 `{"status":"UP"}`.
- Correções de código necessárias no scaffold:
  - `pom.xml`: adicionado `org.flywaydb:flyway-database-postgresql` (Flyway 10+ modulariza suporte por banco; sem ele o PG18 falha com "Unsupported Database").
  - `JpaRecipeRepositoryAdapter`: removido `final` (bean `@Repository` é proxiado por CGLIB).

### FND-004 — Contrato de erros / Problem Details (validado em 2026-07-20)

- `shared/web`: `ProblemDetails` (fábrica RFC 9457), `RequestTraceIdFilter` (gera/propaga `X-Trace-Id`, MDC, ordem -200 antes do Security), `ApiExceptionHandler` (@RestControllerAdvice: validação→400 com `errors[]`, conflito→409, `IllegalArgumentException`→400, genérico→500 sem vazar detalhe).
- `security/adapter/inbound/web`: `ProblemDetailAuthenticationEntryPoint` (401) e `ProblemDetailAccessDeniedHandler` (403), fiados na `SecurityConfiguration`.
- `spring.mvc.problemdetails.enabled=true`.
- Evidência (curl): `GET /api/v1/recipes` sem sessão → **HTTP 401**, `Content-Type: application/problem+json`, header `X-Trace-Id`, corpo com `type,title,status,detail,code,traceId`.
- Nota: sem bean `ObjectMapper` auto-configurado no Boot 4.1; `ProblemDetails` usa um `ObjectMapper` estático próprio.

### Bloqueios / pendências

- `java` do PATH global é 17; o build depende do JDK 25 do SDKMAN. Recomenda-se documentar/exportar `JAVA_HOME` (SDKMAN) ou instalar JDK 25 como default. **Ainda não configurado como padrão do shell.**
- Repositório remoto não criado (decisão do usuário: só local por enquanto) — FND-000 permanece parcial.
- Frontend: projeto Angular 22 oficial (package.json, angular.json, lockfile) ainda não gerado; hoje há apenas os trechos de referência do scaffold.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
