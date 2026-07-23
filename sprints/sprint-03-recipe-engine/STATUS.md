# Status — Sprint 03

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| REC-001 | Concluída | Claude/junico | RecipeTest + CreateRecipeHandlerTest + RecipeIT + ModularityTest verdes; frontend (ng build + Vitest) verde | Expande a receita (composição, equipamento, metas, processo). Itens têm unidade/etapa; capacidade validada via consulta publicada `EquipmentCapacityLookup` do módulo equipment; percentuais de mostura somam 100. Persistência migrada para JDBC (recipe + recipe_item, V26). Endpoints criar/listar/detalhe; permissão `recipe.read` (criação já existia). Tela de receitas com formulário de composição. |
| REC-002 | A fazer | — | — | — |
| REC-003 | A fazer | — | — | — |
| REC-004 | A fazer | — | — | — |
| REC-005 | A fazer | — | — | — |
| REC-006 | A fazer | — | — | — |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
