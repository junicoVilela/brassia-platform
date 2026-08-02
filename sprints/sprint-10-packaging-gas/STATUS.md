# Status — Sprint 10

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| PKG-001 | Concluída | — | V67 + `PackagingPlanIT` (16 testes) | Novo módulo `packaging` |
| GAS-001 | A fazer | — | — | — |
| PKG-002 | A fazer | — | — | — |
| PKG-003 | A fazer | — | — | — |
| FSL-001 | A fazer | — | — | — |
| GAS-002 | A fazer | — | — | — |
| PKG-004 | A fazer | — | — | — |

## Decisões e bloqueios

### PKG-001

- **Limpeza da linha vem do ciclo de sanitização, não do checklist.** O envase consulta
  `sanitation.CleaningReleaseLookup` (última liberação do equipamento) em vez de aceitar um "ok"
  digitado, para a evidência de limpeza ser rastreável ao ciclo. Isso evita criar estado de
  limpo/bloqueado em `equipment` — o débito CLN-004-A da sprint 08 continua aberto e sem dono.
- **PKG-001-A — validade da limpeza por tempo não foi decidida.** A regra implementada
  (`LineCleanliness`) exige liberação anterior ao início planejado e posterior ao último envase na
  linha. Falta o prazo de validade do CIP (quantas horas uma liberação cobre sem novo uso): o número
  depende do POP e da cervejaria e inventá-lo criaria regra de negócio sem fonte. Critério de
  remoção: definir o prazo com o cervejeiro e passá-lo a um parâmetro da cervejaria.
- **Disponibilidade da linha = cadastro ativo + agenda de manutenção + agenda de envase.**
  Publicada como `equipment.EquipmentAvailabilityLookup`; conflito entre planos é resolvido dentro
  do próprio `packaging`.
- **Só lote em `FERMENTING` aceita plano de envase.** Lote em brassagem (`IN_PROGRESS`) não é
  envasável; `COMPLETED` passa a existir com PKG-003 e será reavaliado lá.
- **O teto do plano é a cerveja que está no tanque, não o volume da ordem.** A transferência tem
  perdas: uma ordem de 400 L que transferiu 390 L só pode envasar 390 L. Planejar contra o volume
  planejado inventaria cerveja que não existe. `production.BatchLookup` expõe
  `packageableVolumeLiters` (o volume transferido quando já houve transferência, senão o planejado)
  e é ele que limita o plano. Coberto por `capsThePlanByWhatWasTransferredNotByWhatWasOrdered`, que
  separa os dois números — os demais casos passavam com qualquer um dos dois tetos.
- Consultas publicadas ampliadas em PKG-001: `production.BatchLookup` (passou a expor código,
  volume planejado, volume envasável e estado do lote), `catalog.IngredientSpecLookup` (ganhou
  `volumeMl` e `useUnit`).
