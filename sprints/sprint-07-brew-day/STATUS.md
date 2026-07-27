# Status — Sprint 07

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| PRD-001 | Concluída | IA | (local) | Iniciar lote: `POST /brew-orders/{id}/start` (RELEASED→IN_PRODUCTION, transição única) publica `BrewOrderStarted`; novo módulo `production` escuta (síncrono, mesmo commit) e cria o `Batch` (1:1 com a OP, snapshot receita nome+versão, roteiro derivado dos estágios da receita). `GET /production/batches` e `/{id}`. Migration V50; permissão `production.batch.read`. UI: "Iniciar produção" nas OPs + página "Lotes de produção". Backend +8 testes; frontend +3. |
| PRD-002 | Concluída | IA | (local) | Modo passo a passo: etapa com estado sequencial (PENDING/ACTIVE/DONE) + marcos server-aware (started_at/completed_at); 1ª etapa nasce ativa. `POST /production/batches/{id}/steps/{stepId}/complete` (`production.batch.manage`, V51) conclui a ativa e ativa a próxima; fora de ordem/já concluída → 409; retomar mantém estado. UI: roteiro com status, cronômetro (tick por segundo derivado de started_at) e "Concluir etapa". Backend +2 testes. |
| PRD-003 | A fazer | — | — | — |
| PRD-004 | A fazer | — | — | — |
| PRD-005 | A fazer | — | — | — |
| PRD-006 | A fazer | — | — | — |
| CAL-002 | A fazer | — | — | — |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

### PRD-001 — decisões (confirmadas com o mantenedor)
- **Módulo novo `production`** com o agregado `Batch`. A transição da OP (RELEASED→IN_PRODUCTION) fica no **planning** (`POST /brew-orders/{id}/start`, `planning.order.manage`, transição única guardada pelo estado). O planning publica `BrewOrderStarted`; `production` escuta (síncrono, mesmo commit) e cria o Batch — mantém `production → planning`/`recipe`, sem ciclo (Modulith verde).
- **Reservas ao iniciar**: apenas informativo — iniciar **não** exige nem dispara reservas (decisão do mantenedor). O acoplamento com a reserva atômica (STK-003-A) fica opcional/manual.
- **Roteiro derivado da receita**: etapas a partir dos estágios da composição publicada — Mostura (se houver MASH), Fervura + Whirlpool (se houver BOIL) e Transferência (sempre). **DÉBITO PRD-001-A**: parâmetros ricos do roteiro (tempos de mostura/fervura, rampas) exigem expor `boilTimeMinutes`/rampas no `RecipeLookup` — fica para PRD-002.
- **Snapshot preservado** no Batch como nome+versão da receita (a OP mantém o snapshot completo); Batch é 1:1 com a OP (unique `order_id`), idempotente (re-início → 409; listener não duplica).

### PRD-002 — decisões (confirmadas com o mantenedor)
- **Avanço sequencial**: só a etapa **ATIVA** pode ser concluída; ao concluir, a próxima (menor `step_order` pendente) vira ativa. Fora de ordem/já concluída → **409** (guarda no domínio e na escrita, `step_status='ACTIVE'`). Permissão `production.batch.manage` (V51).
- **Cronômetro server-aware**: cada etapa guarda `started_at` (ao ativar) e `completed_at` (ao concluir); o decorrido é derivado desses marcos (sobrevive a recarga/reconexão). Sem meta de duração por etapa nesta história (rampas/tempos de fervura dependem do débito **PRD-001-A**).
- 1ª etapa nasce **ATIVA** ao abrir o lote (o cronômetro começa no início). Migration V51 ativa a 1ª etapa dos lotes já existentes.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
