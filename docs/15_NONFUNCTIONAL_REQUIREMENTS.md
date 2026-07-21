# Requisitos não funcionais

- Disponibilidade inicial compatível com operação artesanal; degradação segura para IA e integrações.
- p95 de leitura simples abaixo de 500 ms no ambiente-alvo; comando abaixo de 1 s sem integração externa.
- Operações críticas idempotentes e recuperáveis.
- Backup diário e restauração ensaiada; RPO/RTO definidos antes de produção.
- Isolamento completo entre cervejarias.
- Interface responsiva e acessível.
- Auditoria pesquisável e retenção configurável.
- Import/export para portabilidade; nenhum lock-in de provedor de IA.
