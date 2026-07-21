# Ambiente local e bootstrap

Dependências mínimas: Java 25 LTS, Maven Wrapper 3.9.16, Node 24 LTS, npm com lockfile, Docker/Compose e Git. A matriz exata fica em `docs/23_VERSION_POLICY.md`.

## Sequência reproduzível

1. Copiar `.env.example` para `.env` e trocar apenas segredos locais.
2. Subir PostgreSQL e coletor de telemetria pelo Compose.
3. Executar migrations do backend em banco vazio.
4. Criar tenant e usuário de demonstração por seed idempotente exclusivo de ambiente local.
5. Executar backend, frontend e smoke test.

O projeto deve fornecer comandos únicos para `setup`, `up`, `test`, `lint`, `e2e`, `down` e `restore-test`. Produção nunca executa seed de demonstração e não usa credenciais do arquivo de referência.
