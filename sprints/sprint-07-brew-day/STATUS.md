# Status — Sprint 07

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| PRD-001 | Concluída | IA | (local) | Iniciar lote: `POST /brew-orders/{id}/start` (RELEASED→IN_PRODUCTION, transição única) publica `BrewOrderStarted`; novo módulo `production` escuta (síncrono, mesmo commit) e cria o `Batch` (1:1 com a OP, snapshot receita nome+versão, roteiro derivado dos estágios da receita). `GET /production/batches` e `/{id}`. Migration V50; permissão `production.batch.read`. UI: "Iniciar produção" nas OPs + página "Lotes de produção". Backend +8 testes; frontend +3. |
| PRD-002 | A fazer | — | — | — |
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

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
