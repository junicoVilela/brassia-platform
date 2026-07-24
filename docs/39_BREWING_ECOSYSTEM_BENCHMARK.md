# Benchmark do ecossistema cervejeiro

## Objetivo

Este documento registra as ideias aproveitadas de referências do mercado sem copiar telas, textos, bases proprietárias ou regras sem licença. A BrassIA deve combinar a profundidade técnica de ferramentas de formulação com rastreabilidade de produção e uma experiência orientada ao cervejeiro.

Data da revisão: 2026-07-23.

## Referências analisadas

| Referência | Pontos fortes observados | Aplicação na BrassIA |
|---|---|---|
| BJCP | Estilos, impressão geral, atributos sensoriais e estatísticas vitais | Catálogo de padrões versionado, comparação de receita e avaliação sensorial |
| BeerJSON | Intercâmbio moderno de receitas, ingredientes, processos, equipamentos e água | Formato canônico de importação e exportação |
| Brewfather | Designer de receitas, água, estoque, lotes, dispositivos, notificações e API v2 | Inspiração de fluxo e conector opcional de dados do usuário |
| Brewer's Friend | Calculadoras, água, sessões, inventário, shopping list, receitas e API | Calculadoras independentes e importação opcional |
| Grainfather | Criação guiada, escala inteligente, brew day móvel, alertas, sensores e ampla coleção de calculadoras | Assistente passo a passo, PWA, alertas e catálogo de calculadoras |
| BeerTools | Formulação, comparação com estilo, ingredientes, equipamento e automação de cálculos | Editor técnico com explicação de cada resultado |
| Breww | Produção, QA, estoque rastreável, CRM, contêineres, distribuição e relatórios | Extensão comercial pós-release, isolada do núcleo cervejeiro |

## Capacidades que entram no núcleo

1. Editor de receita por seções com atualização imediata dos resultados.
2. Comparação simultânea com BJCP, Brewers Association e perfil personalizado.
3. BeerJSON 1.0 como formato preferencial; BeerXML 1.0 como legado.
4. Catálogo próprio de maltes, lúpulos, culturas, água, sais, ácidos, adjuntos e materiais.
5. Origem, versão, licença, checksum, revisão e vigência em cada dado importado.
6. Perfis de equipamento e escala por volume, OG ou eficiência.
7. Explicação do balanço de volumes e de cada fórmula aplicada.
8. Calculadoras utilizáveis dentro da receita e também de forma independente.
9. Criação guiada a partir de estilo, equipamento, volume e intenção sensorial.
10. Planejado versus realizado sem sobrescrever o histórico.
11. Alertas de adições, mudança de estágio, dry hop, cold crash, envase e manutenção.
12. Gráficos de fermentação com leituras manuais ou provenientes de sensores.

## Capacidades posteriores

- biblioteca de receitas públicas/privadas, fork, compartilhamento e colaboração;
- sales/CRM, tabela de preços, pedidos e previsão de demanda;
- rastreio de keg/barril, retorno, entrega e prova de entrega;
- integrações com e-commerce, POS, contabilidade e transportadoras;
- relatórios regulatórios específicos por país;
- controle remoto de hardware somente após protocolo documentado e análise de segurança.

## Diferenciais propostos para a BrassIA

- **Fonte visível:** nenhum estilo ou ingrediente aparece sem origem e data de revisão.
- **Cálculo auditável:** resultado inclui entradas, método, versão, arredondamento e tolerância.
- **Catálogo sem dependência externa:** cálculos usam snapshots locais aprovados.
- **Modo intenção:** o usuário informa perfil sensorial desejado e recebe metas, não uma receita gerada sem explicação.
- **Substituição justificada:** alternativas são ranqueadas por propriedades técnicas e impacto estimado.
- **Assistente de correção:** durante a produção, sugere ações determinísticas e exige confirmação.
- **Edições progressivas:** Homebrewer/Core não carrega módulos comerciais; Brewery Pro ativa recursos operacionais adicionais.

## Decisões negativas

- Não extrair ou copiar bases globais de concorrentes.
- Não consultar API externa durante cálculo de receita ou execução de lote.
- Não usar IA como fonte de números técnicos.
- Não tratar limites de estilo como regra de reprovação absoluta.
- Não permitir sincronização bidirecional antes de resolver conflito, idempotência e propriedade do dado.

## Fontes

- https://www.bjcp.org/bjcp-style-guidelines/
- https://www.bjcp.org/faq/i-want-to-use-your-style-guidelines-can-i/
- https://beerjson.github.io/beerjson/
- https://github.com/beerjson/beerjson
- https://docs.brewfather.app/
- https://docs.brewersfriend.com/api/recipes
- https://uk.grainfather.com/pages/app-showcase
- https://www.beertools.com/store/product.php?prodid=prod_Rnk2Cs6XXpZnGg
- https://breww.com/features/

