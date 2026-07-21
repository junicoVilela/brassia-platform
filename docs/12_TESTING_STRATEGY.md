# Estratégia de testes

- Unitário: valores, agregados, políticas, fórmulas e máquinas de estado.
- Integração: adapters, JPA, Flyway, Outbox e PostgreSQL via Testcontainers.
- Arquitetura: Spring Modulith `verify()` e ArchUnit apenas para regras adicionais.
- Contrato: OpenAPI, Problem Details RFC 9457 e schemas de evento/IA.
- Frontend: Vitest para unidade/componentes/formulários e Playwright no fluxo crítico.
- Segurança: autorização negativa, tenant cruzado, upload e rate limit.
- IA: datasets dourados, prompt injection, fonte ausente e JSON inválido.

O pipeline bloqueia merge quando unitários, integração do módulo, arquitetura, lint, migrations ou contrato falham.
