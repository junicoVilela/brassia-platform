# Status — Sprint 01

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| SEC-001 | Concluída | Claude/junico | convite + aceite + administração + UI: backend (IT) e frontend (Vitest) verdes | Ciclo de conta completo (convidar/verificar/ativar/bloquear/desbloquear/desativar) + GET de listagem + tela de usuários no shell do tema Fila. |
| SEC-002 | Concluída | Claude/junico | backend (IT) + UI de login (Vitest) verdes | Autenticação por senha, sessão no Postgres (cookie), rotação, logout; página de login, guard de rota e sessão no header. |
| SEC-003 | Concluída | Claude/junico | política + histórico + troca: PasswordIT verde | Blocklist de senhas comprometidas (offline) aplicada no aceite; endpoint autenticado de trocar senha com verificação da atual, política e histórico (não reusar últimas N). Sem expiração periódica. |
| SEC-004 | Concluída (fatia 3) | Claude/junico | create/update grupos + UI: AccessManagementIT + ManageGroupHandlerTest + Vitest | RBAC completo da sprint: catálogo, memberships, criar/atualizar grupos customizados e tela Grupos. |
| SEC-005 | Concluída (fatia 1) | Claude/junico | cervejaria ativa + escopo: AuthorizationIT + Vitest verdes | Login resolve acessíveis/ativa + permissões escopadas; troca de cervejaria; FKs de tenant (V7); brewery_id do principal (não do corpo); seletor no header. access_scope MODULE/RESOURCE fica para depois. |
| SEC-006 | Concluída (self-service) | Claude/junico | sessões + histórico: SessionIT verde | Habilitado Spring Session JDBC (sessão real no Postgres + repo indexado); listar/revogar as próprias sessões; histórico de login (login_event, IP/UA em hash). Admin-sobre-terceiros e dispositivos ficam para depois. |
| SEC-007 | Concluída | Claude/junico | persistência + consulta: AuditEventIT verde | Trilha `AuditTrail` agora persiste (append-only) em `audit_event` além de logar; diff mascarado, traceId; `GET /audit-events` por cervejaria (security.audit.read). Uma auditoria para todos os módulos. |
| SEC-008 | Concluída | Claude/junico | concessão + aprovação + revogação: TemporaryAccessIT + resolução verde | Acesso temporário: permissão pontual com vigência e justificativa; comum vige na janela, crítica exige aprovação de 2º usuário (≠ solicitante); revogação; tudo auditado e efetivo na resolução do login. |
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

### SEC-005 — Cervejaria ativa e escopo (2026-07-21)

- **Fronteira de módulo**: `security` não lê a tabela `brewery`; usa a porta exposta `br.com.brew.brassia.brewery.BreweryDirectory` (`findAll`/`findById` + `BreweryRef`). A cervejaria **default** é criada por bootstrap do próprio módulo `brewery` (`brassia.brewery.bootstrap.*`, ligado no local — `MATRIZ`). Modulith: `security → brewery` (tipo exposto).
- **Resolução escopada**: `EffectivePermissionsRepository.findByUserId(userId, activeBreweryId)` filtra `brewery_id IS NULL OR = ativa` (associação global vale sempre; escopada só na sua cervejaria). `BreweryAccessRepository` (JDBC): `hasGlobalMembership`/`scopedBreweryIds`.
- **`SessionContextResolver`**: resolve acessíveis (global → todas; escopado → subconjunto), ativa (pedida se acessível, senão a primeira por código; pedida não acessível → `AccessDeniedException`/403) e permissões escopadas. Usado no login (default) e na troca.
- **Endpoints**: `POST /api/v1/security/session/brewery {breweryId}` (troca, 403 se não acessível); `GET /session` passa a devolver `activeBrewery` + `accessibleBreweries` + `permissions`. O principal (`SecurityPrincipal.breweryId`) carrega a **cervejaria ativa**; novo `requireBrewery()`.
- **Tenant como autoridade**: `brewery_id` vem do principal (não do corpo) — `RecipeController` passou a usar `requireBrewery()`. Migration **V7** amarra as FKs de tenant (`user_group_membership`/`security_group` → `brewery`).
- **Frontend**: `AuthService.switchBrewery` + **seletor de cervejaria** no header (ativa + acessíveis).
- Fora de escopo: `access_scope` MODULE/RESOURCE e checagem cruzada por recurso; preferência persistente de cervejaria ativa; refresh de sessão on mudança de escopo (SEC-006).

### SEC-004 — fatia 2: administração de acessos (2026-07-21)

- **Leitura**: `GET /api/v1/security/permissions` (catálogo) e `GET /groups` (grupos + permissões via `array_agg`), com `security.permission.read`/`security.group.read`. `SecurityCatalogRepository` (JDBC) + `AccessCatalogQuery`.
- **Associações**: `POST /users/{id}/memberships {groupId}` e `DELETE /users/{id}/memberships/{groupId}` (`security.membership.manage`). A associação é **sempre escopada à cervejaria ativa do principal** (`requireBrewery()`) — `brewery_id` não vem do corpo. Grupo inativo/inexistente e usuário inexistente rejeitados; duplicada → 409; auditoria `security.membership.grant|revoke`.
- **Migration `V8`**: unicidade da associação por `(user_id, group_id, brewery_id)` (permite o mesmo grupo em cervejarias diferentes); seed das 3 permissões novas no catálogo + Administradores.
- **Efeito ponta a ponta** (`AccessManagementIT`): usuário sem grupo → `GET /users` 403; admin associa ao grupo Administradores (cervejaria ativa) → ao relogar, `GET /users` 200.
- Fora de escopo (fatia 3): criar grupos + editar permissões dos grupos; UI de gestão de acessos; associação global via UI (segue só no bootstrap).

### SEC-004 — fatia 3: criar/atualizar grupos + UI (2026-07-22)

- **Escrita**: `POST /api/v1/security/groups` e `PATCH /groups/{id}` com `security.group.manage`. Grupo customizado nasce na cervejaria ativa; código normalizado `[A-Z][A-Z0-9_]{1,79}`; `ADMINISTRATORS` (sistema) é imutável; ator só atribui permissões que já possui (anti-autoelevação); optimistic locking via `version`.
- **Migration `V13`**: seed de `security.group.manage` no catálogo + Administradores.
- **GET /groups** passa a expor `description` e `version`.
- **UI**: feature `security/groups` (listar, criar, editar permissões) + item no shell; associação usuário↔grupo permanece na API (UI de membership fica como refinamento).
- Testes: `ManageGroupHandlerTest`, `AccessManagementIT` (create/update/403/sistema), Vitest `GroupsApi`.

### SEC-003 — Política e histórico de senha (2026-07-21)

- **Blocklist offline** (`security/common-passwords.txt`): `CompromisedPasswordChecker` + adapter carregam a lista curada; `PasswordPolicy.validate` rejeita senha comum/comprometida (case-insensitive). Aplicada no `AcceptInvitationHandler` (primeiro set).
- **Trocar senha** `POST /api/v1/security/password/change` (autenticado, self-service): verifica a senha atual, valida a nova pela política e **barra reutilização** da atual e das últimas N (`password_history`, `history-size` default 5). Grava a nova credencial e arquiva a antiga; auditoria `security.password.change` (sem senha).
- **Migration `V9`**: `password_history` (só hashes) + índice por usuário/tempo.
- **Sem expiração periódica** (NIST); política aceita Unicode/frases (só comprimento + blocklist).
- **`PasswordIT`**: reuso/atual-incorreta/blocklist → 400; troca válida → 204; a antiga não loga (401), a nova sim; não pode voltar à anterior (histórico).
- Fora de escopo: recuperação/reset de senha (SEC-010); verificação online (HIBP); UI de troca de senha.

### SEC-006 — self-service: sessões e histórico de login (2026-07-21)

- **Correção de base**: o Boot 4 não traz o autoconfig de sessão — as sessões estavam **em memória** do servlet, não no Postgres. Adicionado `@EnableJdbcHttpSession` (`SessionConfiguration`) + `CookieSerializer` por perfil: sessões passam a persistir em `spring_session` e ficam **indexadas pelo id do usuário**. Isso também ativa de fato a revogação da SEC-001. `SessionRepositoryProbeIT` guarda contra regressão.
- **Histórico** `login_event` (migration `V10`): o login grava sucesso/falha com **identificador/IP/UA em hash** (SHA-256, pseudonimizado) + traceId; falha não vincula usuário (anti-enumeração). `GET /api/v1/security/login-events` devolve o histórico do próprio usuário (occurredAt/outcome/reasonCode).
- **Sessões** (self-service): `GET /sessions` lista as próprias com **ref mascarada** (prefixo do id — o id nunca é exposto); `DELETE /sessions/{ref}` revoga uma; `DELETE /sessions` revoga todas as outras (mantém a atual). Só opera sobre as sessões do próprio usuário (isolamento por índice de principal).
- `SessionIT`: histórico sem plaintext; semear/listar/revogar sessões e isolamento entre usuários.
- Fora de escopo: admin ver/revogar sessões e histórico de terceiros (fatia seguinte); dispositivos confiáveis, geolocalização e alertas de novo acesso (SEC-012); UI.

### SEC-007 — Auditoria de segurança (2026-07-21)

- **Persistência append-only**: o módulo `audit` ganhou `JdbcAuditTrail` (persiste em `audit_event` com metadados **mascarados** via `SensitiveDataMasker`, `trace_id` e `change_summary` jsonb) e um `CompositeAuditTrail` (`@Primary`) que registra **log + banco**. Todos os módulos (security/brewery/recipe) já usam `AuditTrail` → uma trilha única do sistema.
- **Consulta**: porta exposta `AuditQuery` (`recent(breweryId, limit)`) + `JdbcAuditQuery`. `GET /api/v1/security/audit-events` (permissão `security.audit.read`, semeada na `V11`) devolve os eventos **da cervejaria ativa** (tenant-scoped; eventos globais como login ficam na visão de login-events da SEC-006).
- Migration `V11__audit_event.sql` (tabela + seed da permissão). Sem FK para `brewery` (auditoria não acopla ao schema de outro módulo).
- Testes: `JdbcAuditTrailTest` (metadados mascarados, sem segredo); `AuditEventIT` (ação auditada → persistida → `GET` tenant-scoped; global fora; `403` sem permissão).
- Fora de escopo: filtros (ator/ação/intervalo), exportação, campos extras do modelo de referência (`actor_type`/`reason`/`ip_hash`), retenção e UI de auditoria.

### SEC-008 — Acesso temporário (2026-07-21)

- **Modelo**: `temporary_access_grant` (migration `V12`) concede **uma permissão** a um usuário na cervejaria ativa, com `reason`, janela `valid_from`/`valid_until`, `requested_by`/`approved_by` (CHECK `approved_by <> requested_by`) e `revoked_at`/`revoked_by`. Sem FK para `brewery` (mesma razão do `audit_event`); `scope_id`/`access_scope` ficam para SEC-005.
- **Aprovação por criticidade**: permissão `critical=false` **vige na janela** ao ser solicitada; permissão `critical=true` fica **pendente** e só vige após aprovação de um **segundo usuário** (segregação garantida no handler + CHECK). Seed das permissões `security.temporary-access.request|approve|revoke` (críticas) e `.read` no catálogo + Administradores.
- **Efetiva no login**: `JdbcEffectivePermissionsRepository` passou a **somar (UNION)** as concessões vigentes (não revogadas, dentro da janela, e `critical=false OR approved_by IS NOT NULL`) às permissões dos grupos — vale a partir do próximo login do alvo, consistente com o modelo de membership.
- **Endpoints** em `/api/v1/security/temporary-access`: `POST` (solicita, `.request`), `POST /{id}/approve` (`.approve`, 403 se o ator for o solicitante, 409 se não está pendente), `DELETE /{id}` (`.revoke`, 409 se já revogada), `GET` (`.read`, visão administrativa com status derivado). `brewery_id` e solicitante vêm do principal, nunca do corpo. Auditoria `security.temporary-access.request|approve|revoke`.
- **Testes**: `TemporaryAccessGrantTest` (regras de vigência/segregação no domínio), `TemporaryAccessHandlerTest` (comum vs crítica, segregação, conflitos, revogação), `TemporaryAccessResolutionIT` (UNION efetivo/expirado/revogado/pendente/outra cervejaria), `TemporaryAccessIT` (solicitar/listar/revogar via HTTP; auto-aprovação → 403; sem permissão → 403).
- Fora de escopo: `scope_id`/`access_scope` (SEC-005); expiração ao vivo em sessão já aberta; job de expiração/limpeza; UI; notificação ao alvo/aprovador.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
