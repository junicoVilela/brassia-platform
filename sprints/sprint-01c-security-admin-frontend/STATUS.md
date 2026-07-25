# Status — Sprint 01-C

Estado: EM ANDAMENTO

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
| SEC-F10 | A fazer | — | — | Administração de federação/SCIM. |

## Decisões e bloqueios

- Nenhuma migration nova em toda a sprint.
- SEC-F04: exceção à regra "só frontend" (decisão do mantenedor) — o backend não tinha leitura de memberships; adicionado `GET /users/{userId}/memberships` (aditivo, sem migration, sem alterar endpoints existentes; perm `security.membership.manage`). Demais histórias seguem só-frontend salvo gaps semelhantes.
- SEC-F09/F10 podem migrar para a Sprint 15 (integrações/PWA) conforme prioridade.
- SEC-F08: o `GET /audit-events` devolve os 50 mais recentes sem parâmetros de filtro/paginação. A tela filtra no cliente (ação/recurso/ator/período). Débito: filtro e paginação server-side no módulo `audit` para grandes volumes.
- SEC-F09: o `GET /service-accounts` não retorna as credenciais existentes (`credentialPrefixes` vem vazio) e não há listagem de credenciais. As credenciais emitidas ficam apenas na sessão (segredo mostrado uma vez); revogar só vale para as emitidas na sessão. Débito: expor credenciais por conta (prefixo/escopos/estado) para listar/revogar as já existentes.
- Fluxo real de SSO no browser e LDAP real permanecem fora de escopo.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Contratos consumidos:
- Riscos remanescentes:
- Aceite:
