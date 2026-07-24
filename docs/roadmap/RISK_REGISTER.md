# Registro de riscos

| Risco | Impacto | Mitigação |
|---|---|---|
| Escopo funcional amplo | atraso e produto incompleto | uma fatia vertical por sprint, núcleo fechado e edições pós-release |
| Fórmula incorreta | perda de lote | motor versionado, datasets dourados e revisão técnica |
| Medição ruim | correção errada | metrologia, calibração e confiança da leitura |
| Isolamento falho | vazamento de dados | testes tenant cruzado e filtro na persistência |
| IA alucinar | ação insegura | ferramentas determinísticas, fonte e confirmação |
| Química/pressão | acidente | POP/FISPQ, bloqueios e nenhuma automação autônoma |
| Offline conflitante | histórico divergente | idempotência, fila e resolução explícita |
| Rastreio incompleto | recall ineficaz | genealogia e simulado periódico |
| Dependência de fornecedor | lock-in | portas, formatos abertos e exportação |
| Estilo publicado sem permissão | remoção de conteúdo e risco jurídico | gate de licença, atribuição e nível de conteúdo por dataset |
| Catálogo externo incorreto | receita ou compra inadequada | fonte, versão, revisão, snapshot e valores específicos por lote |
| API de concorrente mudar | sincronização interrompida | adapters isolados, BeerJSON/BeerXML, circuit breaker e importação manual |
| Mesclagem de homônimos | ingrediente técnico errado | chave por fabricante/código e revisão humana |
| Arquivo de importação hostil | indisponibilidade ou exposição | limites, parser seguro, staging, varredura e processamento assíncrono |
| Sincronização bidirecional conflitar | perda ou sobrescrita de dados | começar read-only, ownership explícito, prévia e resolução de conflito |
