# Status — Sprint 07

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| PRD-001 | Concluída | IA | (local) | Iniciar lote: `POST /brew-orders/{id}/start` (RELEASED→IN_PRODUCTION, transição única) publica `BrewOrderStarted`; novo módulo `production` escuta (síncrono, mesmo commit) e cria o `Batch` (1:1 com a OP, snapshot receita nome+versão, roteiro derivado dos estágios da receita). `GET /production/batches` e `/{id}`. Migration V50; permissão `production.batch.read`. UI: "Iniciar produção" nas OPs + página "Lotes de produção". Backend +8 testes; frontend +3. |
| PRD-002 | Concluída | IA | (local) | Modo passo a passo: etapa com estado sequencial (PENDING/ACTIVE/DONE) + marcos server-aware (started_at/completed_at); 1ª etapa nasce ativa. `POST /production/batches/{id}/steps/{stepId}/complete` (`production.batch.manage`, V51) conclui a ativa e ativa a próxima; fora de ordem/já concluída → 409; retomar mantém estado. UI: roteiro com status, cronômetro (tick por segundo derivado de started_at) e "Concluir etapa". Backend +2 testes. |
| PRD-003 | Concluída | IA | (local) | Registrar medição: `Measurement` imutável (append-only) com valor, unidade, temperatura, método, origem e operador; vocabulário fechado de grandezas (DENSITY/TEMPERATURE/VOLUME/PH/COLOR/IBU) com unidades válidas — incompatível → 400. Etapa opcional (se informada, deve ser do lote). Só lote em andamento. `POST /production/batches/{id}/measurements` (`production.measurement.record`, V52) + `GET`. UI: painel de medições (form dependente da grandeza + histórico). Backend +7 testes. |
| PRD-004 | Concluída | IA | (local) | Correções determinísticas (pré-visualização): reusa o motor versionado (Sprint 04). Engine ganhou concentração por evaporação, correção de densidade por temperatura e ajuste de volume (diluição já existia); publicado `calculator.CalculatorEngine` p/ reúso. `GET /production/corrections` + `POST /production/batches/{id}/corrections/preview` (só lote em andamento; restrito às 4 correções; read-only — nada aplicado). UI: painel de Correções com inputs dinâmicos + impacto/alertas. Backend +8 testes. |
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

### PRD-003 — decisões (confirmadas com o mantenedor)
- **Vocabulário fechado de grandezas** (`MeasurementKind`) com unidades válidas: DENSITY(SG/PLATO), TEMPERATURE(C/F), VOLUME(L/ML), PH(PH), COLOR(EBC/SRM), IBU(IBU). Unidade fora da grandeza → **400** (validação no domínio). Origem `MANUAL/DEVICE/IMPORTED`.
- **Etapa opcional**: a medição é do lote; se `stepId` informado, deve pertencer ao lote (senão 400). Não exige etapa ativa. Só lote **IN_PROGRESS** (medição fora de contexto → 409).
- **Imutável**: append-only (`production_measurement`, V52), sem edição/exclusão. Registrar exige `production.measurement.record`; leitura reusa `production.batch.read`.

### PRD-004 — decisões (confirmadas com o mantenedor)
- **Fronteira com CAL-002**: PRD-004 = **pré-visualização** (impactos), read-only, nada persiste. Aplicar com evento + planejado vs realizado fica no **CAL-002**.
- **Tipos**: diluição, concentração (evaporação), correção de densidade por temperatura e ajuste de volume. **DÉBITO PRD-004-A**: correção de **sais** (química de água/ppm por perfil de íon) fica de fora por exigir perfil de água alvo.
- **Reúso do motor**: as correções são calculadoras determinísticas versionadas no `calculator` (mesma fórmula/versão), expostas por `calculator.CalculatorEngine` (API publicada) — sem replicar. A produção valida o lote (IN_PROGRESS) e restringe aos ids de correção; `production → calculator` (Modulith verde).

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
