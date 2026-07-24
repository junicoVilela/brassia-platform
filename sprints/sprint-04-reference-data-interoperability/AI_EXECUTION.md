# Prompt de execução — Sprint 04

Você está implementando a Sprint 04 — Dados de referência e interoperabilidade da BrassIA.

As Sprints 00–03 já foram desenvolvidas. Primeiro inspecione o código, migrations, contratos e testes existentes. Preserve o comportamento entregue; toda alteração incompatível exige justificativa, plano de migração e ADR.

Leia, nesta ordem:

1. `AGENTS.md`
2. `docs/00_PRODUCT_IDENTITY.md`
3. `.ai/PROJECT_CONTEXT.md`
4. `.ai/DEVELOPMENT_RULES.md`
5. `docs/01_ARCHITECTURE.md`
6. `docs/02_MODULE_BOUNDARIES.md`
7. `docs/39_BREWING_ECOSYSTEM_BENCHMARK.md`
8. `docs/40_REFERENCE_DATA_AND_CATALOGS.md`
9. `docs/41_EXTERNAL_API_INTEGRATION_STRATEGY.md`
10. `docs/42_STYLE_GUIDELINES_LICENSING.md`
11. `docs/43_CALCULATOR_CATALOG.md`
12. todos os arquivos desta pasta.

Histórias disponíveis: REF-001, REF-002, STD-001, CAT-003, WTR-003, REC-007, REC-008, REC-009, CAL-001 e REC-010.

Execute uma história por vez na ordem do `README.md`. Antes de editar, apresente:

- diferença entre o estado atual e o objetivo;
- invariantes e compatibilidade;
- contrato, dados, eventos e autorização;
- fonte/licença envolvida;
- riscos e testes.

Implemente uma fatia vertical mínima. Use catálogo interno versionado. BeerJSON é formato canônico externo, não API ou catálogo. BeerXML é adapter legado. Não copie base global de concorrente, não publique texto restrito e não crie conector autenticado antes da Sprint 15.

Ao finalizar, execute testes, migrations desde banco vazio e desde a Sprint 03, validação dos schemas, inspeção arquitetural e E2E. Atualize OpenAPI/ADR quando necessário e registre arquivos alterados, comandos, evidências, riscos e pendências.
