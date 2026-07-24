# Sprint 04 — Dados de referência e interoperabilidade

## Objetivo

Evoluir o que já foi entregue nas Sprints 00–03 com dados cervejeiros versionados, curados e rastreáveis, além de importação/exportação compatível com o ecossistema.

## Premissa de execução

As Sprints 00–03 já foram desenvolvidas. Esta sprint deve adaptar-se ao código existente, criar migrations apenas aditivas e preservar contratos já publicados. Não reescreva a fundação, segurança, catálogos básicos ou o motor de receitas sem uma incompatibilidade comprovada e registrada.

## Módulos

reference-data, catalog, water, recipe, shared

## Dependências

Sprints 02 e 03 concluídas

## Histórias

- `REF-001` — Registro de fontes, versões, licença e proveniência
- `REF-002` — Pipeline de staging, validação, revisão e publicação
- `STD-001` — Conjuntos versionados de estilos cervejeiros
- `CAT-003` — Enriquecimento de maltes, lúpulos, culturas e adjuntos
- `WTR-003` — Perfis de água, laudos e alvos versionados
- `REC-007` — Importar e exportar BeerJSON 1.0
- `REC-008` — Compatibilidade legada BeerXML 1.0
- `REC-009` — Assistente de formulação e comparação com estilo
- `CAL-001` — Hub de calculadoras cervejeiras
- `REC-010` — Substituições técnicas explicáveis

## Entregáveis técnicos

- Modelo de dados de referência e migrations aditivas
- API de fontes, datasets, estilos, ingredientes e jobs de importação
- Catálogo interno publicado por versão
- BeerJSON como contrato canônico externo
- Adapter BeerXML com relatório de compatibilidade
- Comparador de estilo sem reprovação automática
- Calculadoras determinísticas reutilizando o motor de domínio
- Auditoria, checksum e snapshot de toda referência aplicada
- Telas de curadoria, prévia de importação e comparação

## Restrições

- Não alterar as Sprints 00–03 para simular que o trabalho ainda será feito.
- Não copiar bases globais do Brewfather, Brewer's Friend, Grainfather, BeerTools ou Breww.
- Não publicar texto integral BJCP/BA sem permissão compatível.
- Não consultar API externa durante cálculo, abertura de receita ou execução de lote.
- Não usar IA como fonte de valores técnicos.
- Não fazer sincronização autenticada com concorrentes nesta sprint; conectores ficam na Sprint 15.

## Ordem recomendada

1. `REF-001` e `REF-002`
2. `STD-001`, `CAT-003` e `WTR-003`
3. `REC-007` e `REC-008`
4. `CAL-001`, `REC-009` e `REC-010`

## Riscos que precisam de teste

- conteúdo publicado sem licença ou atribuição;
- atualização externa alterando receita histórica;
- mesclagem incorreta de ingredientes homônimos;
- perda semântica na conversão BeerXML;
- arquivo malicioso ou grande demais;
- divergência de unidades, arredondamento ou versão de fórmula.

## Fora do escopo

Sincronização Brewfather/Brewer's Friend, sensores, comunidade, vendas, CRM, logística, controle remoto de equipamento e catálogo proprietário de terceiros.
