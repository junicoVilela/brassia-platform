# Status — Sprint 00

Estado: EM ANDAMENTO (preparação da fundação)

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| FND-000 | Parcial | Claude/junico | git local `main`, commit bootstrap | Remoto adiado por decisão do usuário (só local por enquanto) |
| FND-001 | Em andamento | Claude/junico | backend compila + 3 testes verdes | Frontend Angular oficial ainda não gerado |
| FND-002 | Parcial | Claude/junico | `compose.yaml` PostgreSQL 18 | Perfis local/test/prod e validação de migration em banco real pendentes |
| FND-003 | A fazer | — | — | — |
| FND-004 | A fazer | — | — | Problem Details RFC 9457 |
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
