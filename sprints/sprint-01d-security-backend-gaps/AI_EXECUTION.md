# Prompt de execução — Sprint 01-D

Você está implementando a Sprint 01-D — Segurança: leituras e administração (backend) da BrassIA.

Leia, nesta ordem: `AGENTS.md`, `docs/00_PRODUCT_IDENTITY.md`, `.ai/PROJECT_CONTEXT.md`, `.ai/DEVELOPMENT_RULES.md`, `docs/01_ARCHITECTURE.md`, `docs/02_MODULE_BOUNDARIES.md`, esta pasta e as Sprints 01/01-B/01-C para os contratos e débitos já registrados.

Histórias disponíveis: SEC-B01, SEC-B02, SEC-B03, SEC-B04, SEC-B05, SEC-B06.

Trabalhe em uma história por vez. São adições de backend: **não altere contratos existentes**; crie migration só onde inevitável (SEC-B02). Antes de editar, apresente invariantes, contrato, dados, autorização (permissão + `brewery_id`), riscos e testes. Implemente uma fatia vertical mínima com Testcontainers. Ao concluir uma história, **remova o débito correspondente** no STATUS da Sprint 01-B/01-C e simplifique a tela que embarcou o workaround.

Ao finalizar: execute testes, migrations e inspeção arquitetural (`ModularityTest`); informe arquivos alterados, comandos executados, evidências, riscos e pendências; atualize o STATUS da sprint.
