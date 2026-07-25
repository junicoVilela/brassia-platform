# Status — Sprint 01-C

Estado: EM ANDAMENTO

Contexto: fecha o débito de frontend das capacidades de administração/governança de segurança entregues como "fatia 1" (só backend) na Sprint 01. Depende do gate de permissão (SEC-F11) da Sprint 01-B.

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| SEC-F04 | Concluída | IA | #64 | Memberships no detalhe do usuário: listar/associar/remover + bloqueio por segregação. Exigiu GET aditivo /users/{id}/memberships. |
| SEC-F05 | A fazer | — | — | Acesso temporário: solicitar/aprovar/revogar. |
| SEC-F06 | A fazer | — | — | Revisão de acessos + regras de segregação. |
| SEC-F07 | A fazer | — | — | Alertas de segurança. |
| SEC-F08 | A fazer | — | — | Auditoria consultável com filtros. |
| SEC-F09 | A fazer | — | — | Contas de serviço + API keys. |
| SEC-F10 | A fazer | — | — | Administração de federação/SCIM. |

## Decisões e bloqueios

- Nenhuma migration nova em toda a sprint.
- SEC-F04: exceção à regra "só frontend" (decisão do mantenedor) — o backend não tinha leitura de memberships; adicionado `GET /users/{userId}/memberships` (aditivo, sem migration, sem alterar endpoints existentes; perm `security.membership.manage`). Demais histórias seguem só-frontend salvo gaps semelhantes.
- SEC-F09/F10 podem migrar para a Sprint 15 (integrações/PWA) conforme prioridade.
- Fluxo real de SSO no browser e LDAP real permanecem fora de escopo.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Contratos consumidos:
- Riscos remanescentes:
- Aceite:
