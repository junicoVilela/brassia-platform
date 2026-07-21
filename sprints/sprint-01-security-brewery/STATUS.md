# Status — Sprint 01

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| SEC-001 | Em progresso | Claude/junico | convite + aceite + administração: domínio+aplicação+web+IT verdes | Convite (INVITED), aceite (ACTIVE) e administração (bloquear/desbloquear/desativar) entregues. Falta a UI (tela de usuários). |
| SEC-002 | A fazer | — | — | — |
| SEC-003 | A fazer | — | — | — |
| SEC-004 | A fazer | — | — | — |
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
| BRW-001 | A fazer | — | — | — |
| BRW-002 | A fazer | — | — | — |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

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

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
