# Grupos, permissões e escopos

## Modelo

O sistema usa **RBAC + escopo/atributos**. Usuário recebe grupos; grupo agrega permissões; escopo limita onde a permissão vale. A decisão final ainda considera cervejaria ativa, ownership, estado do recurso, vigência e segregação de funções. A ausência de concessão significa negação.

Convenção da permissão: `<domínio>.<ação>`, por exemplo `recipe.create`, `recipe.publish`, `inventory.adjust`, `sanitation.override`, `security.user.manage` e `security.audit.read`.

## Grupos iniciais

| Grupo | Finalidade | Restrições recomendadas |
|---|---|---|
| Administrador de segurança | contas, grupos, políticas, MFA e sessões | MFA/passkey obrigatório; não aprova o próprio acesso temporário |
| Administrador da cervejaria | preferências e usuários da própria cervejaria | sem acesso a segredos/credenciais de outra cervejaria |
| Mestre cervejeiro | receita, produção e decisão técnica | override crítico exige justificativa |
| Operador de produção | executar etapas e registrar medições | não publica receita nem ajusta saldo |
| Qualidade | liberar, bloquear, investigar e auditar | segregação opcional da execução de produção |
| Estoque e compras | lotes, reservas, inventário e pedidos | ajuste manual pode exigir aprovação |
| Auditor | consulta e exportação controlada | somente leitura; exportação auditada |
| Consulta | dashboards e registros autorizados | sem comandos de alteração |
| Integração | API key para caso de uso específico | sem login web; menor escopo; expiração obrigatória |

Não codificar grupos em `if/else`. Casos de uso consultam permissões e escopos. Grupos de sistema são seed versionado; grupos customizados são configuráveis.

## Regras críticas

- `security.*`, `inventory.adjust`, `sanitation.override`, `quality.release`, `traceability.recall` e reaberturas exigem autenticação recente.
- Autoconcessão e autoaprovação de privilégio crítico são proibidas.
- Mudança de grupo, permissão, escopo ou política revoga/atualiza sessões afetadas.
- Revisão trimestral é baseline para administradores e credenciais de integração; a frequência pode ser ajustada por risco.
