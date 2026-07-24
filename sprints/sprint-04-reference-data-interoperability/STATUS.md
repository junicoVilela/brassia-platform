# Status — Sprint 04

Estado: CONCLUÍDA

Base declarada pelo mantenedor: Sprints 00–03 já desenvolvidas.

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| REF-001 | Concluída | IA | #51 | Registro de fontes e datasets de referência. |
| REF-002 | Concluída | IA | #52 | Pipeline de staging, validação e publicação. |
| STD-001 | Concluída | IA | #53 | Conjuntos versionados de estilos cervejeiros (perfil interno + estrutura). |
| CAT-003 | Concluída | IA | #54 | Perfis técnicos de referência de ingredientes. |
| WTR-003 | Concluída | IA | #55 | Perfis de água de referência e balanço de cargas. |
| REC-007 | Concluída | IA | #56 | Prévia de importação BeerJSON 1.0 / BeerXML seguro. |
| REC-008 | Concluída | IA | #56 | Importação com validação e mapeamento (mesma PR do REC-007). |
| REC-009 | Concluída | IA | #58 | Assistente de formulação e comparação com estilo. |
| CAL-001 | Concluída | IA | #57 | Hub de calculadoras determinísticas. |
| REC-010 | Concluída | IA | #59 | Substituições técnicas explicáveis (ranking determinístico por propriedades). |

## Decisões e bloqueios

- `REC-006` (importar/exportar BeerJSON/BeerXML) confirmado na Sprint 03; REC-007/008 estenderam com prévia e validação sem duplicar o exportador.
- Decisão permanente do mantenedor: **só perfil interno + estrutura** — BJCP/BA tratados como `LIMITED_PERMISSION`, sem copiar bases globais de terceiros; arquivos de teste BeerJSON/BeerXML sintéticos.
- REC-010: ranking 100% determinístico sobre as propriedades técnicas configuradas por tipo (CAT-003). A IA não calcula score nem inventa propriedades; substituir é sempre alteração explícita na versão em edição.

Decisão permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

## Evidências de encerramento

- Build/commit: main verde após #51–#59; migrations até V33.
- Testes executados: domínio (JUnit 5 + AssertJ), integração Testcontainers (postgres:18), `ModularityTest` (Spring Modulith), frontend Vitest (26) + ESLint + build.
- Migration aplicada: V17–V33 (referência, staging, estilos, perfis técnicos, água de referência).
- Contratos atualizados: novos endpoints de referência, perfis técnicos, água de referência, importação/prévia, calculadoras e substituições (`GET /api/v1/catalog/ingredients/{id}/substitutions`).
- Datasets publicados: apenas perfis internos + estrutura versionada; nenhum catálogo global de terceiros importado.
- Evidência de licença: conteúdo BJCP/BA marcado como `LIMITED_PERMISSION`; nada publicado além do autorizado.
- Riscos remanescentes: importação de fabricantes com dados autorizados fica para sprint futura, quando as licenças forem selecionadas.
- Aceite: 10/10 histórias entregues, revisadas e mescladas em `main`.
