# Status — Sprint 05

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| PLN-001 | Concluída | IA | (local) | Fatia vertical completa: domínio, aplicação, persistência (V35), web (`/planning/schedule` + `/simulate` + `GET`), OpenAPI e frontend (agenda). Backend 18 testes verdes + ModularityTest; frontend 114 (Vitest). |
| PLN-002 | Concluída | IA | (local) | Necessidade de materiais: explosão da receita publicada por volume + perda %, conversão de unidades (KG/L/UNIT), agregação por ingrediente. Endpoints `POST /planning/material-requirement` e `GET /planning/schedule/{id}/materials`; UI "Materiais" na agenda. Sem disponibilidade/faltas (Sprint 06). Backend +11 testes; frontend 116. |
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

### PLN-002 — decisões (confirmadas com o mantenedor)
- **Só necessidade agora**: PLN-002 entrega a necessidade por item (explosão + conversão), **sem disponibilidade/faltas** — comparação com estoque fica na Sprint 06 (inventory). Respeita "não antecipar sprint futura".
- **Dois endpoints**: `POST /planning/material-requirement` (ad-hoc: receita+volume+perda) e `GET /planning/schedule/{id}/materials` (usa receita/volume da entrada da agenda).
- **Perda**: fórmula `necessario_i = qtd_i × (volumeAlvo/volumeReceita) × (1 + lossPercent/100)`. **Fonte definida**: `lossPercent` é entrada explícita (default 0), uniforme para todos os itens — margem de processo/segurança, sem inventar modelo de eficiência oculto. **DÉBITO PLN-002-A**: perdas por ingrediente ou derivadas de eficiência de brassagem são refinamento futuro.
- **Conversão de unidades**: canônica por dimensão — massa→KG, volume→L, contagem→UNIT; agrega o mesmo ingrediente entre etapas/unidades.
- **Composição publicada**: `recipe.RecipeLookup` ganhou `findPublishedComposition` (itens + volume da batelada, só PUBLISHED); nomes de ingrediente resolvidos no frontend via catálogo (sem novo acoplamento no backend). Cálculo é read-only (sem persistência, sem evento; auditoria não aplicável).

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
