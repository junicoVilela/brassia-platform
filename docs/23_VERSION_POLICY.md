# Versões e política de atualização

Verificado em **2026-07-16** nas documentações oficiais. “Sempre a versão mais atual” significa **última estável compatível**, não beta, RC ou atualização automática em produção.

| Componente | Baseline | Motivo |
|---|---|---|
| Java | 25 LTS | LTS atual; Java 26 é feature release curta |
| Spring Boot | 4.1.0 | linha estável atual e compatível com Java 25 |
| Spring Framework | gerenciado pelo Boot (7.0.8+) | não fixar manualmente versão gerenciada |
| Spring Modulith | 2.1.0 | verificação, testes e documentação dos módulos |
| Maven | Wrapper 3.9.16 | release estável; Maven 4 permanece preview/RC |
| PostgreSQL | 18, último minor | major estável atual; PostgreSQL 19 ainda beta |
| Spring Security | gerenciado pelo Boot | autenticação, autorização, CSRF e password encoders sem implementação caseira |
| Spring Session JDBC | gerenciado pelo Boot | sessão opaca persistida no PostgreSQL |
| Angular | 22, último patch 22.x | versão ativa atual |
| Node.js | 24 LTS, mínimo 24.15 | LTS para produção; Node 26 ainda Current |
| TypeScript | 6.0.x | faixa suportada pelo Angular 22: `>=6.0 <6.1` |
| RxJS | faixa gerenciada/compatível com Angular 22 | não antecipar major fora da matriz Angular |
| Testes frontend | Vitest + Playwright | Vitest é o padrão do Angular CLI; E2E separado |
| OpenAPI | 3.1.x | contrato HTTP; Problem Details segue RFC 9457 |

Fontes: [Java SE Support Roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html), [Spring Boot System Requirements](https://docs.spring.io/spring-boot/system-requirements.html), [Spring Security](https://docs.spring.io/spring-security/reference/), [Spring Session](https://docs.spring.io/spring-session/reference/), [Spring Modulith](https://spring.io/projects/spring-modulith), [Maven Downloads](https://maven.apache.org/download.cgi), [Angular Releases](https://angular.dev/reference/releases), [Angular Compatibility](https://angular.dev/reference/versions), [Node Releases](https://nodejs.org/en/about/previous-releases) e [PostgreSQL Versioning](https://www.postgresql.org/support/versioning/).

## Regras

- Runtime usa LTS; framework usa release estável; nunca `SNAPSHOT`, beta ou RC no baseline.
- Maven Wrapper, `.nvmrc` e lockfile tornam o build reproduzível.
- Spring Boot gerencia dependências do ecossistema; não sobrescrever versões sem necessidade comprovada.
- Dependências npm ficam fixadas no lockfile; CI usa `npm ci`.
- Patch de segurança: prioridade imediata com testes e rollback.
- Patch comum: lote semanal automatizado; minor: mensal; major: sprint própria com guia de migração.
- Renovate/Dependabot abre PR, mas nunca faz merge automático de framework, banco, autenticação ou migration.
- PostgreSQL recebe minors regularmente; major exige ensaio de `pg_upgrade`/dump-restore e restauração.
- Storage usa contrato S3; desenvolvimento pode usar filesystem. Não acoplar domínio a SDK/produto específico.
- Registrar a data da última revisão desta matriz e revisar no início de cada release.
