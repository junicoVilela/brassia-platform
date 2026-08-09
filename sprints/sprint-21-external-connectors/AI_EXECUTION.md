# Prompt de execução — Sprint 21

Você está implementando a Sprint 21 — Conectores externos da BrassIA.

Leia, nesta ordem: `AGENTS.md`, `docs/00_PRODUCT_IDENTITY.md`, `.ai/PROJECT_CONTEXT.md`,
`.ai/DEVELOPMENT_RULES.md`, `docs/01_ARCHITECTURE.md`, `docs/02_MODULE_BOUNDARIES.md`, a pasta da Sprint 04
(pipeline canônico de receita), a pasta da Sprint 15 (inbox/idempotency e outbox) e esta pasta.

Histórias disponíveis: INT-004, INT-005, INT-007.

**Antes de começar, confirme o desbloqueio de `BLQ-INT-001`**: sem credencial de teste do provedor, os
critérios de paginação, rate limit, backoff, timeout e revogação não são verificáveis. Se não houver
credencial, registre o bloqueio e pare — não entregue a história coberta apenas por dublê.

Trabalhe em uma história por vez. Antes de editar, apresente invariantes, contrato, dados, eventos,
autorização, riscos e testes. Implemente uma fatia vertical mínima. Trate todo dado externo como não
confiável. Não crie microserviço, não acesse tabela de outro módulo e não habilite escrita no provedor.

Ao finalizar: execute testes, migrations e inspeção arquitetural; atualize OpenAPI/ADR quando necessário;
informe arquivos alterados, comandos executados, evidências, riscos e pendências.
