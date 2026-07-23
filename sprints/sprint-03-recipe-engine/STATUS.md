# Status — Sprint 03

Estado: CONCLUÍDA

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| REC-001 | Concluída | Claude/junico | RecipeTest + CreateRecipeHandlerTest + RecipeIT + ModularityTest verdes; frontend (ng build + Vitest) verde | Expande a receita (composição, equipamento, metas, processo). Itens têm unidade/etapa; capacidade validada via consulta publicada `EquipmentCapacityLookup` do módulo equipment; percentuais de mostura somam 100. Persistência migrada para JDBC (recipe + recipe_item, V26). Endpoints criar/listar/detalhe; permissão `recipe.read` (criação já existia). Tela de receitas com formulário de composição. |
| REC-002 | Concluída | Claude/junico | VolumeBalanceTest (dataset dourado) + RecipeVolumesIT + ModularityTest verdes; frontend (ng build + Vitest) verde | Motor de volumes: a partir do volume final calcula água/absorção/evaporação/perdas por balanço de massa que fecha (cada parcela mostrada); método versionado (GRAIN_ABSORPTION_BOILOFF_V1). Consome perfil do equipment via nova consulta publicada `EquipmentProfileLookup`. `GET /recipes/{id}/volumes` (recipe.read). Tela mostra o balanço por receita. |
| REC-003 | Concluída | Claude/junico | BrewingMetricsTest + RecipeMetricsIT + ModularityTest verdes; frontend (ng build + Vitest) verde | Metas cervejeiras: OG/FG/ABV/IBU/cor/atenuação por método versionado (TINSETH_MOREY v1). Resultado **persistido** (recipe_metrics, V27) com método+versão; **tolerância explícita** por meta vs as metas informadas (desvio + dentro/fora). Consome specs do catalog via nova consulta publicada `IngredientSpecLookup` e o perfil do equipment. POST calcula+persiste (recipe.create), GET lê (recipe.read). Tela mostra metas e tolerância por receita. |
| REC-004 | Concluída | Claude/junico | RecipeTest + RecipePublishIT + ModularityTest verdes; frontend (ng build + Vitest) verde | Publicar versão: DRAFT→PUBLISHED congela a fórmula (imutável); republicar → 409. "Alteração gera nova versão": `POST /versions` cria novo rascunho derivado (v+1, previous_recipe_id), preservando o snapshot publicado. Implementado o `RecipeLookup` publicado (só devolve receita publicada) + evento `RecipePublished`. Unicidade de nome passou a incluir versão (V28). Tela: botões Publicar/Nova versão por status. |
| REC-005 | Concluída | Claude/junico | RecipeCloneScaleCompareTest + RecipeDerivationIT + ModularityTest verdes; frontend (ng build + Vitest) verde | Clonar (cópia independente), escalar (quantidades × razão de volume; **escala respeita capacidade** via EquipmentCapacityLookup) e comparar versões campo a campo (escalares + itens por etapa:ingrediente). Novos endpoints em `RecipeDerivationController` (POST /clone, /scale; GET /compare). Tela com painel derivar/comparar. |
| REC-006 | Concluída | Claude/junico | RecipeExchangeCodecTest + RecipeImportExportIT + ModularityTest verdes; frontend (ng build + Vitest) verde | Importar/exportar BeerJSON e BeerXML (subset). Export via getRecipe; import reusa CreateRecipe (validação atômica → inválida não persiste). Relatório de compatibilidade lista campos desconhecidos do documento. Referências por id interno (interop por nomes de terceiros = follow-up). Tela: exportar JSON/XML por receita + importar (colar documento) com relatório. |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
