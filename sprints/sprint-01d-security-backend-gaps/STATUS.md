# Status — Sprint 01-D

Estado: EM ANDAMENTO (5/6 — resta apenas SEC-B02, opcional)

Contexto: fecha os débitos de backend descobertos ao entregar o frontend de segurança (01-B/01-C). Ao concluir cada história, o débito correspondente nas Sprints 01-B/01-C é removido e a tela simplificada.

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| SEC-B04 | Concluída | IA | #71 | GET /service-accounts/{id}/credentials (sem segredo) + tela lista/revoga credenciais persistidas. Remove o débito do SEC-F09. |
| SEC-B01 | Concluída | IA | #72 | GET /totp/status (mfaEnabled + recoveryCodesRemaining); Minha conta indica ativo/inativo no load. Remove o débito do SEC-F01. |
| SEC-B03 | Concluída | IA | #73 | GET /audit-events com filtros (ação/recurso/ator/resultado/período) + paginação; tela deixou de filtrar no cliente. Remove o débito do SEC-F08. |
| SEC-B06 | Concluída | IA | #74 | GET /federation-providers/{id}/identities (subject/e-mail/usuário/data); tela lista vínculos por provedor. Remove parte do débito do SEC-F10. |
| SEC-B05 | Concluída | IA | #75 | GET/POST/DELETE /federation-providers/{id}/scim-mappings (reutiliza federation.read/manage). Tela: painel de mapeamentos SCIM por provedor. Remove o débito SCIM do SEC-F10. |
| SEC-B02 | A fazer | — | — | Origem mascarada no histórico (opcional; requer migration). |

## Decisões e bloqueios

- Endpoints aditivos; não alterar contratos existentes. Migration só onde inevitável (SEC-B02).
- SEC-B05: por decisão do mantenedor, os mapeamentos SCIM reutilizam `security.federation.read`/`manage` (sem permissão dedicada, sem migration); mapeamentos pendem de um provedor de federação. `create` do repo (fluxo máquina em /scim/v2) preservado; a administração usa `upsert` (reativa) e `deactivate`.
- SEC-B07 (SSO no browser) fica na Sprint 15; QR inline do MFA é débito de frontend (SEC-F01).

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
