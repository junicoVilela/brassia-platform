# Status — Sprint 01-C

Estado: CONCLUÍDA

Contexto: fecha o débito de frontend das capacidades de administração/governança de segurança entregues como "fatia 1" (só backend) na Sprint 01. Depende do gate de permissão (SEC-F11) da Sprint 01-B.

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| SEC-F04 | Concluída | IA | #64 | Memberships no detalhe do usuário: listar/associar/remover + bloqueio por segregação. Exigiu GET aditivo /users/{id}/memberships. |
| SEC-F05 | Concluída | IA | #65 | Acesso temporário: solicitar/aprovar/revogar + lista com vigência/estado/aprovador. Backend completo (só frontend). |
| SEC-F06 | Concluída | IA | #66 | Revisão de acessos (criar/listar/itens KEEP-REMOVE) + regras de segregação (criar/listar). Backend completo (só frontend). |
| SEC-F07 | Concluída | IA | #67 | Alertas de segurança: listar (filtro por estado), reconhecer/resolver, evidência. Backend completo (só frontend). |
| SEC-F08 | Concluída | IA | #68 | Auditoria: viewer com filtros no cliente (termo/resultado/período) sobre os 50 recentes. Débito: filtro/paginação server-side. |
| SEC-F09 | Concluída | IA | #69 | Contas de serviço + API keys: criar conta, emitir credencial (segredo uma vez), revogar; escopos visíveis. Débito: backend não lista credenciais existentes. |
| SEC-F10 | Concluída | IA | #70 | Administração de federação SAML/OIDC (criar/listar/validar metadata). SCIM admin fica como débito (sem endpoint de sessão; provisionamento é via IdP/API key). |

## Decisões e bloqueios

- Nenhuma migration nova em toda a sprint.
- SEC-F04: exceção à regra "só frontend" (decisão do mantenedor) — o backend não tinha leitura de memberships; adicionado `GET /users/{userId}/memberships` (aditivo, sem migration, sem alterar endpoints existentes; perm `security.membership.manage`). Demais histórias seguem só-frontend salvo gaps semelhantes.
- SEC-F09/F10 podem migrar para a Sprint 15 (integrações/PWA) conforme prioridade.
- SEC-F08: ~~o `GET /audit-events` devolve os 50 mais recentes sem filtro/paginação~~ — **RESOLVIDO pelo SEC-B03** (#73): o endpoint agora aceita filtros (ação/recurso/ator/resultado/período) e paginação; a tela filtra e pagina no servidor (não mais no cliente).
- SEC-F09: ~~o `GET /service-accounts` não retorna as credenciais existentes~~ — **RESOLVIDO pelo SEC-B04** (#71): novo `GET /service-accounts/{id}/credentials` (prefixo/escopos/estado, sem segredo); a tela agora lista e revoga as credenciais persistidas por conta, além das emitidas na sessão.
- SEC-F10: administração de federação (SAML/OIDC) entregue por completo (criar/listar/validar). A parte SCIM não tem endpoint de sessão — `/scim/v2/**` e os mapeamentos de grupo são consumidos/criados pelo IdP via API key (provisionamento máquina-a-máquina), não por tela admin. Vincular identidade externa existe (POST /identities) mas não é listável. Débitos: endpoint admin para mapeamentos SCIM e para listar identidades vinculadas; fluxo de login SSO no browser (já fora de escopo desde o BACKLOG).
- Fluxo real de SSO no browser e LDAP real permanecem fora de escopo.

## Evidências de encerramento

- Build/commit: `main` verde após #64–#70; frontend build e ESLint limpos.
- Testes executados: Vitest 98/98 (memberships, temporary-access, access-review, alerts, audit, service-accounts, federation) + backend `AccessManagementIT` (SEC-F04) e `ModularityTest` verdes.
- Contratos consumidos: memberships (GET aditivo + grant/revoke), acesso temporário, revisão/segregação, alertas, auditoria, contas de serviço/API keys, federação SAML/OIDC. Uma adição aditiva de backend (SEC-F04 GET memberships); nenhuma migration.
- Riscos remanescentes: débitos de leitura server-side (auditoria com filtro/paginação, credenciais de service account, mapeamentos/identidades SCIM) e SSO no browser — todos registrados acima.
- Aceite: 7/7 histórias (SEC-F04..F10) entregues, revisadas e mescladas em `main`.
