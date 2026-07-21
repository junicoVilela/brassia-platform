# Status — Sprint 00

Estado: EM ANDAMENTO (preparação da fundação)

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| FND-000 | Parcial | Claude/junico | git local `main`, commit bootstrap | Remoto adiado por decisão do usuário (só local por enquanto) |
| FND-001 | Em andamento | Claude/junico | backend compila + 3 testes verdes | Frontend Angular oficial ainda não gerado |
| FND-002 | Concluída | Claude/junico | migrations aplicadas em PG18 real; app sobe; health UP | Perfis local/test/prod criados; ver evidências |
| FND-003 | A fazer | — | — | — |
| FND-004 | Concluída | Claude/junico | 401/403/erros em problem+json validados via curl | Problem Details RFC 9457 + traceId |
| FND-005 | A fazer | — | — | Auditoria/observabilidade |
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
