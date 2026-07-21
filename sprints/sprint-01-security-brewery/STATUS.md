# Status — Sprint 01

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| SEC-001 | Em progresso | Claude/junico | fatia de convite: domínio+aplicação+web+IT verdes | Cadastro/convite entregue (INVITED + token hash + auditoria + 409). Faltam verificar/ativar/bloquear/desativar e a UI. |
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

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
