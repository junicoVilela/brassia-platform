# Limites de módulos

| Módulo | Responsabilidade | Não pode fazer |
|---|---|---|
| security | contas, credenciais, grupos, permissões, escopos, MFA, sessões e auditoria de segurança | decidir regra cervejeira ou criar criptografia própria |
| brewery | preferências, unidades, limites globais | acessar produção diretamente |
| catalog | estilos, ingredientes e materiais | movimentar estoque |
| equipment | capacidade, perdas, manutenção | liberar lote |
| recipe | formulação, cálculos e versões | consumir insumo |
| planning | agenda, necessidade e OP | registrar execução física |
| production | etapas, medições e correções | alterar versão da receita |
| sanitation | POP, ciclo, verificação e liberação | inventar parâmetro químico |
| fermentation | curvas, estágios, levedura e adega | envasar produto |
| packaging | carbonatação, envase e lote final | ajustar saldo diretamente |
| inventory | lotes e movimentos | decidir substituição técnica |
| quality | especificações, amostras, CAPA e sensorial | reescrever medição histórica |
| traceability | genealogia, quarentena e recall | ser origem de saldo |
| costing | snapshots e análises de custo | alterar consumo físico |
| ai | contexto, RAG e recomendações | executar comando sem usuário |
