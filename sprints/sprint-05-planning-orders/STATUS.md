# Status — Sprint 05

Estado: CONCLUÍDA (5/5)

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| PLN-001 | Concluída | IA | (local) | Fatia vertical completa: domínio, aplicação, persistência (V35), web (`/planning/schedule` + `/simulate` + `GET`), OpenAPI e frontend (agenda). Backend 18 testes verdes + ModularityTest; frontend 114 (Vitest). |
| PLN-002 | Concluída | IA | (local) | Necessidade de materiais: explosão da receita publicada por volume + perda %, conversão de unidades (KG/L/UNIT), agregação por ingrediente. Endpoints `POST /planning/material-requirement` e `GET /planning/schedule/{id}/materials`; UI "Materiais" na agenda. Sem disponibilidade/faltas (Sprint 06). Backend +11 testes; frontend 116. |
| BOP-001 | Concluída | IA | (local) | Criar OP: só de receita publicada, com snapshot JSONB (cálculo + equipamento) e código único OP-<ano>-<n> (sequência atômica); nasce DRAFT; falha "snapshot incompleto" (409) sem métricas. `POST/GET /brew-orders` + `GET /{id}`; UI de ordens com snapshot. Backend +12 testes; frontend 122. |
| BOP-002 | Concluída | IA | (local) | Liberar OP: DRAFT→RELEASED com responsável; bloqueios (estado, responsável, equipamento) listados em 409; estoque/sanitização = débito S06/S08. Emite `BrewOrderReleased` + auditoria; guarda de concorrência. `POST /brew-orders/{id}/release`; UI com seletor de responsável e bloqueios. Backend +7 testes; frontend 125. |
| BOP-003 | Concluída | IA | (local) | Cancelar OP: DRAFT/RELEASED→CANCELLED com motivo obrigatório; iniciada/encerrada → 409; **idempotente** (recancelar = 200 no-op); liberar reservas = no-op (débito S06). Auditoria; guarda de concorrência. `POST /brew-orders/{id}/cancel`; UI com motivo. Backend +9 testes; frontend 127. |

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

### BOP-001 — decisões (confirmadas com o mantenedor)
- **Snapshot JSONB** congelando receita (id, versão, nome, métricas og_sg/fg_sg/abv/ibu/color) + equipamento (capacidade, dead space, eficiência, boil-off). `recipe.RecipeLookup.findPublishedForOrder` expõe cabeçalho + equipmentId + volume + métricas (Optional). Snapshot completo é invariante do `OrderSnapshot` (métricas obrigatórias) → sem métricas calculadas, "snapshot incompleto" (409 via `SnapshotIncompleteException`).
- **Código único** `OP-<ano>-<n>` (4 dígitos), sequencial por cervejaria/ano via tabela `brew_order_sequence` com upsert atômico `RETURNING` (seguro sob concorrência); unique `(brewery_id, code)`.
- **Entrada**: receita publicada + volume (equipamento vem da receita). OP nasce `DRAFT`. Auditoria no create; **sem evento** (o evento é `BrewOrderReleased` na BOP-002).
- **Módulo**: `BrewOrder` vive no módulo `planning` (o domínio o classifica como planejamento); permissões `planning.order.read/manage`. Endpoints `POST/GET /brew-orders` + `GET /brew-orders/{id}` (detalhe com snapshot).
- Nota: `JdbcBrewOrderRepository` usa um `ObjectMapper` próprio p/ o JSONB (não há bean de `ObjectMapper` para injeção fora do web).

### BOP-002 — decisões (confirmadas com o mantenedor)
- **Bloqueios verificados agora**: estado=DRAFT, responsável presente, equipamento do snapshot existe. **Estoque (S06) e sanitização (S08) = débito** — respeita "não antecipar sprint futura".
- **Responsável** informado no `POST /brew-orders/{id}/release` (corpo `{assignedUserId}`); ausência é **bloqueio** (não erro de validação). A OP passa a guardar `assigned_user_id`/`released_at` (migration V37).
- **Falha lista bloqueios**: 409 Problem Details com extensão `blockers: [{code,message}]` (advice local `PlanningExceptionHandler`).
- **Sucesso**: DRAFT→RELEASED via update guardado por estado (fecha corrida de liberação concorrente) + emite `BrewOrderReleased` (porta `BrewOrderEventPublisher`, `ApplicationEventPublisher` como no recipe) + auditoria.
- **DÉBITO BOP-002-A**: validar que o responsável é membro da cervejaria (mesma pendência do PLN-001-A); hoje só exige presença.

### BOP-003 — decisões (confirmadas com o mantenedor)
- **Estados canceláveis**: DRAFT e RELEASED → CANCELLED. Iniciada (IN_PRODUCTION+, inalcançável até S07) e CLOSED → 409 "ordem iniciada ou encerrada não pode ser cancelada".
- **Idempotência**: recancelar uma OP já CANCELLED → 200 no-op (sem novo efeito/auditoria).
- **Motivo** obrigatório (`@NotBlank`, ≤500) → 400 se ausente. Migration V38 (`cancel_reason`, `cancelled_at`).
- **Liberar reservas** = no-op até o módulo de inventário (Sprint 06) — hook documentado no handler.
- Sem evento canônico de cancelamento; auditoria `planning.order.cancel`. Guarda de concorrência via update por estado.

## Evidências de encerramento

- Build/commit: `main` verde após #78–#81 (PLN-001/002, BOP-001/002); BOP-003 em PR final. Backend e frontend build/lint limpos em cada PR.
- Testes executados: pacote `planning` completo (domínio + ITs Testcontainers) + `ModularityTest`; frontend Vitest (127). Cobrem sucesso, limite, falha, outra cervejaria e repetição/idempotência por história.
- Migration aplicada: V35 (agenda), V36 (OP + sequência), V37 (liberação), V38 (cancelamento) — aplicadas desde banco vazio nos ITs.
- Contratos atualizados: `contracts/openapi.yaml` com `/planning/schedule*`, `/planning/material-requirement`, `/brew-orders*` (criar/listar/detalhe/release/cancel) + schemas.
- Riscos remanescentes: estoque (reservas/faltas) e sanitização dependem das Sprints 06/08 — débitos registrados (PLN-002/BOP-002/BOP-003). Membership do responsável (PLN-001-A/BOP-002-A). Evento entregue via `ApplicationEventPublisher` (sem outbox físico).
- Aceite: 5/5 histórias (PLN-001, PLN-002, BOP-001, BOP-002, BOP-003) entregues com testes de domínio, integração, autorização e tenant verdes; frontend com loading/vazio/erro/conflito/acesso negado.
