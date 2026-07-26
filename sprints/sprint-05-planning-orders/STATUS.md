# Status — Sprint 05

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| PLN-001 | Concluída | IA | (local) | Fatia vertical completa: domínio, aplicação, persistência (V35), web (`/planning/schedule` + `/simulate` + `GET`), OpenAPI e frontend (agenda). Backend 18 testes verdes + ModularityTest; frontend 114 (Vitest). |
| PLN-002 | A fazer | — | — | — |
| BOP-001 | A fazer | — | — | — |
| BOP-002 | A fazer | — | — | — |
| BOP-003 | A fazer | — | — | — |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

### PLN-001 — decisões de modelagem (confirmadas com o mantenedor)

- **Modelo de tempo**: janela `[scheduled_start, scheduled_end)` (timestamptz). Conflito de equipamento = sobreposição de janelas no mesmo `equipment_id`. Janelas que apenas se tocam não conflitam.
- **Responsável**: `assigned_user_id` referencia um usuário da cervejaria (módulo security).
- **Equipamento**: um equipamento principal por entrada (`equipment_id`).
- **Evento de domínio**: PLN-001 **não** emite evento (não há evento canônico de agenda em `docs/09_DOMAIN_EVENTS.md`); apenas auditoria no comando de criação. Eventos ficam para as ordens (`BrewOrderReleased` etc.).
- **DÉBITO PLN-001-A**: validação de que `assigned_user_id` é membro da cervejaria fica adiada para **BOP-002** (que "valida responsável" na liberação). Em PLN-001 o id é armazenado como metadado; a UI restringe a escolha aos usuários da cervejaria. Critério de remoção: BOP-002 validar o responsável contra a associação da cervejaria.
- Reuso de APIs publicadas (Spring Modulith): `recipe.RecipeLookup.findPublished` (só receita publicada) e `equipment.EquipmentCapacityLookup.capacityLiters` (existência + capacidade). O módulo `planning` não acessa tabelas de outros módulos.
- **Concorrência**: além do pré-check no caso de uso, há um backstop no banco — exclusion constraint `ex_planning_schedule_no_overlap` (`btree_gist`, `tstzrange &&`) impede janelas sobrepostas do mesmo equipamento; a `DataIntegrityViolationException` é traduzida para 409 na `PlanningConfiguration` (fora do `@Repository`, que re-traduziria a exceção própria). Testado de forma determinística (`ScheduleIT.databaseBackstopRejectsConcurrentOverlap`).
- **DÉBITO PLN-001-B (frontend)**: o seletor de "Responsável" carrega usuários via `/api/v1/security/users` (best-effort), que exige `security.user.read`. Um planejador sem essa permissão vê o seletor vazio. Critério de remoção: expor um read leve de "membros da cervejaria" (sem exigir permissão de segurança) — candidato à Sprint 05/BOP ou a um ajuste de contrato.
- **E2E**: o repositório não tem harness de browser (`e2e/` só com README). O fluxo ponta-a-ponta é coberto pelo `ScheduleIT` (HTTP real + Postgres). No frontend, specs de store/api (Vitest).

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
