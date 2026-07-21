# Status — Sprint 01

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| SEC-001 | Concluída | Claude/junico | convite + aceite + administração + UI: backend (IT) e frontend (Vitest) verdes | Ciclo de conta completo (convidar/verificar/ativar/bloquear/desbloquear/desativar) + GET de listagem + tela de usuários no shell do tema Fila. |
| SEC-002 | Concluída | Claude/junico | backend (IT) + UI de login (Vitest) verdes | Autenticação por senha, sessão no Postgres (cookie), rotação, logout; página de login, guard de rota e sessão no header. |
| SEC-003 | A fazer | — | — | — |
| SEC-004 | Em progresso | Claude/junico | catálogo + resolução + bootstrap: AuthorizationIT verde | RBAC global: catálogo/grupo Administradores (seed V5), permissões resolvidas no login, admin de bootstrap por config. Destrava dados reais. Falta CRUD de grupos/membros. |
| SEC-005 | A fazer | — | — | — |
| SEC-006 | A fazer | — | — | — |
| SEC-007 | A fazer | — | — | — |
| SEC-008 | A fazer | — | — | — |
| SEC-009 | A fazer | — | — | — |
| SEC-010 | A fazer | — | — | — |
| SEC-011 | A fazer | — | — | — |
| SEC-012 | A fazer | — | — | — |
| SEC-013 | A fazer | — | — | — |
| SEC-014 | A fazer | — | — | — |
| SEC-015 | A fazer | — | — | — |
| SEC-016 | A fazer | — | — | — |
| BRW-001 | Concluída | Claude/junico | backend (BreweryIT) + tela: verdes | Cadastro/listagem de cervejaria (código único, fuso), auditado; permissões brewery.* no catálogo; tela Cervejarias no shell. Vínculo ao principal/tenant é SEC-005. |
| BRW-002 | A fazer | — | — | — |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

### SEC-004 — RBAC fatia 1 (2026-07-21)

- **Migration `V5__security__rbac.sql`**: `permission_domain`, `security_permission`, `security_group`, `group_permission`, `user_group_membership`. Seed idempotente do catálogo (`recipe.create`, `security.user.read|invite|block|disable`) + grupo de sistema `ADMINISTRATORS` com todas as permissões.
- **RBAC global nesta fatia**: `brewery_id` anulável e **sem FK** para `brewery` (que só existe na BRW-001). Escopo por cervejaria (`access_scope`) e tenant são SEC-005.
- **Resolução no login**: `EffectivePermissionsRepository` (JDBC) junta associações ativas → grupos ativos → permissões ativas; `AuthenticateUserHandler` resolve e o `AuthenticationController` injeta as permissões no `SecurityPrincipal` da sessão. `GET /session` passa a listá-las. **Isto destrava os dados reais** nas telas permissionadas.
- **Permissões cacheadas na sessão** (resolvidas no login). Re-resolução ao vivo após mudança de grupo é refinamento futuro (SEC-006).
- **Bootstrap admin por config** (`brassia.security.bootstrap-admin.*`, ligado no perfil local): runner idempotente que garante uma conta ACTIVE + credencial no grupo `ADMINISTRATORS`. Resolve o ciclo inicial (convidar exige permissão). Conta/associação em transações separadas (a associação por FK exige a conta commitada). Credenciais locais descartáveis (`admin@brassia.local`).
- Débito implícito: seed liga o admin a **todas** as permissões via `INSERT ... SELECT`; novas permissões futuras exigem re-seed/associação. Rastreado por esta nota; CRUD de grupos/permissões chega na próxima fatia.

### SEC-001 — fatia de convite (2026-07-21)

- **Identidade global**: `security_user` não tem `brewery_id` (unicidade por `normalized_email` global), conforme `database/09_security.sql` e `docs/30_SECURITY_MODULE_SPECIFICATION.md`. O vínculo com cervejaria é feito por escopos/grupos (SEC-004/005); aqui `brewery_id`/`actorId` do `SecurityPrincipal` governam autorização e auditoria.
- **Fatia mínima**: entregue o cadastro/convite (usuário `INVITED` + `account_token` de convite por hash, uso único, TTL 7 dias + auditoria `security.user.invite` + 409 de e-mail duplicado). Verificar e-mail, ativar, bloquear/desbloquear e desativar (revogando sessões) são as próximas fatias da própria SEC-001.
- **Entrega de e-mail adiada**: sem SMTP nesta sprint. `NotificationGateway` é um stub que loga (token bruto só em DEBUG, desligado em prod). A entrega real fica para sprint futura. Token nunca aparece em resposta HTTP nem na auditoria.
- **Correção transversal**: `ApiExceptionHandler` passou a traduzir `AccessDeniedException` (lançada por `requirePermission` dentro do controller) em `403`; antes cairia no handler genérico (500). Beneficia também o `RecipeController`.
- Migration nova: `V3__security__users.sql` (`security_user`, `account_token`).

### SEC-001 — fatia de aceite/ativação (2026-07-21)

- Endpoint **público** `POST /api/v1/security/users/accept-invitation`: valida o token de convite (hash, tipo, não usado, não expirado), verifica o e-mail e ativa a conta (`INVITED → ACTIVE`), consumindo o token (uso único). Auditoria `security.user.activate`.
- **Público e isento de CSRF**: o convidado ainda não tem sessão; a requisição é autenticada pelo token do link (sem autoridade ambiente a proteger). Adicionado a `permitAll` e a `ignoringRequestMatchers`.
- **Senha fica para a SEC-003**: aceitar o convite não define senha; ativa a conta. Consequência registrada: uma conta `ACTIVE` sem credencial de senha ainda não autentica (login é SEC-002).
- **Mensagens genéricas** (anti-enumeração): token inválido/expirado/usado → `400 bad_request`; conta fora de `INVITED` → `409`.
- Introduzido o **primeiro ciclo de update de agregado** (load→mutate→save) com **optimistic locking** via `@Version` no `security_user`; o `account_token` é consumido por atualização de `used_at`.

### SEC-001 — fatia de administração de conta (2026-07-21)

- Endpoints autenticados: `POST /api/v1/security/users/{id}/block` (ACTIVE→LOCKED, permissão `security.user.block`), `/unblock` (LOCKED→ACTIVE, `security.user.block`), `/disable` (→DISABLED + revoga sessões, `security.user.disable`). Um caso de uso `AdministerAccountUseCase` com `Operation` {BLOCK,UNBLOCK,DISABLE}, auditado por ação distinta.
- Sem migration nova: o `CHECK` de `status` em `V3` já cobre `LOCKED`/`DISABLED`.
- **Revogação de sessões**: porta `UserSessionRegistry`; adapter via Spring Session `FindByIndexNameSessionRepository` **opcional** (`ObjectProvider`). Enquanto não houver login (SEC-002) criando sessões indexadas pelo id do usuário, a revogação é um **no-op seguro**; passa a valer automaticamente quando o repositório indexado existir. Débito implícito rastreado por esta nota (efetivação plena com SEC-002/SEC-006).
- Transições inválidas → `409` (ex.: desbloquear conta ativa, desativar conta já desativada); conta inexistente → `400`.

### SEC-001 — fatia de UI + shell de layout (2026-07-21)

- **Endpoint de listagem**: `GET /api/v1/security/users` (paginado, permissão `security.user.read`) — necessário para a tabela. `ListUsersUseCase`/`ListUsersHandler` + `findPage`/`count` no repositório.
- **Shell de layout** (`src/app/layout/shell.component.ts`) aplicando o **tema Fila** (Bootstrap 5 admin): sidebar (nav Receitas/Usuários) + header + footer; burger colapsa via atributo `sidebar-data-theme=sidebar-hide`. Todas as telas passam a viver no shell (rota pai).
- **Tela de usuários** (`features/security/users`): tabela com status, formulário de convite e ações (bloquear/desbloquear/desativar por status); estados loading/vazio/erro/ação. Testada com Vitest + `HttpTestingController`.
- **Tema pago em repo público**: os arquivos do Fila **não são versionados** (`/public/assets/fila/` no `.gitignore`); carregados em runtime por `theme-loader.ts`; setup em `frontend/THEME_SETUP.md`. Build/CI passam sem eles.
- **Sem login ainda (SEC-002)**: a tela é construída/testada contra o contrato (HTTP mockado); ao vivo mostra o layout + estados, dados reais só com o login. Guarda de rota e dados ao vivo ficam para SEC-002.

### SEC-002 — Login e sessão segura (2026-07-21)

- **Aceite define a senha**: `POST /accept-invitation` agora exige `{token, password}`; grava `password_credential` (hash via `DelegatingPasswordEncoder`, `encoder_id="delegating"`) — migration `V4__security__password_credential.sql`. Regra mínima de senha (8..200); política plena é SEC-003.
- **Login** `POST /api/v1/security/login`: `AuthenticateUserHandler` valida e-mail normalizado + `status=ACTIVE` + hash; **falha genérica** (mesma resposta para inexistente/errada/inativa) → `401 invalid_credentials`. Auditoria `security.login.success|failure` (sem senha). No sucesso: cria `SecurityContext`, **rotaciona a sessão** (`changeSessionId`), persiste via `HttpSessionSecurityContextRepository` (Spring Session JDBC → Postgres) e emite o cookie.
- **`GET /session`** (autenticado) retorna a identidade; **`POST /logout`** invalida a sessão (`204`).
- **Principal só identidade**: `SecurityPrincipal.breweryId` agora **opcional** + `identityOnly(...)`; permissões vazias. Endpoints permissionados seguem `403` até a SEC-004.
- **Revogação de sessões ativada**: o `Authentication` custom expõe `getName()=userId`, indexando a sessão por usuário — a `UserSessionRegistry` (SEC-001) passa a encontrar/derrubar sessões no `disable`. Cobertura automatizada: `disable→revokeAll` é unit-testado; o round-trip JDBC de sessão/cookie é validado em runtime (MockMvc usa `MockHttpSession`).
- Fora de escopo (histórias próprias): MFA (SEC-009), recuperação de senha (SEC-010), rate limit (SEC-012), gestão de sessões/`login_event` (SEC-006), cervejaria ativa (SEC-005).

### SEC-002 — UI de login (2026-07-21)

- **CSRF bootstrap**: `GET /api/v1/security/csrf` (público) resolve o `CsrfToken` e emite o cookie `XSRF-TOKEN`; o Angular (XSRF nativo) o reenvia no header `X-XSRF-TOKEN` nas requisições mutáveis.
- **Frontend `core/auth`**: `AuthApi` (csrf/login/logout/session), `AuthService` (Signals: `user`/`isAuthenticated`, `ensureSession` cacheia a consulta), `authGuard` (redireciona a `/login?returnUrl=` quando não autenticado).
- **Página de login** (`features/auth/login-page`) no card fiel do tema Fila (rota pública `/login`, fora do shell); o shell passa a ser protegido pelo `authGuard`. O header mostra o nome do usuário e o botão **Sair** (logout → `/login`).
- **Sem dados reais ainda**: após logar, as telas com endpoints permissionados seguem `403` até a SEC-004 (RBAC). Guard/login e identidade funcionam fim a fim.

### BRW-001 — Cadastrar cervejaria (2026-07-21)

- Novo módulo Modulith `brewery` (espelha `recipe`): agregado `Brewery` (code único normalizado, name, `Timezone` via `ZoneId`), `RegisterBreweryUseCase`/`ListBreweriesUseCase`, adapters JPA e `BreweryController` (`POST`/`GET /api/v1/breweries`). Migration `V6__brewery.sql` (tabela + seed das permissões `brewery.read`/`brewery.manage` no catálogo e no grupo Administradores).
- Auditoria `brewery.register`; conflito de código → 409; sem permissão → 403.
- **Tela Cervejarias** no frontend (feature `brewery`, mesmo padrão de `security/users`) + item na sidebar.
- Fora de escopo: vínculo do `brewery` ao principal (cervejaria ativa) e escopo por tenant/FKs do RBAC — **SEC-005**; unidades/moeda/políticas — **BRW-002**.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
