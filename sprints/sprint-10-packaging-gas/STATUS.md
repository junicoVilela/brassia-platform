# Status — Sprint 10

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| PKG-001 | Concluída | — | V67 + `PackagingPlanIT` (16 testes) | Novo módulo `packaging` |
| GAS-001 | Concluída | — | V68 + `GasNetworkIT` (16 testes) | Novo módulo `gas` |
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

### GAS-001

- **Gás é rastreado por massa, não por pressão.** Em cilindro de CO₂ com fase líquida o manômetro
  fica praticamente constante enquanto houver líquido, então estimar o restante pela pressão daria
  um número errado com cara de certo. A pressão é medida e guardada como evidência da linha, nunca
  como estimativa de conteúdo.
- **Sobrepressão bloqueia a linha automaticamente.** Leitura acima do teto da rede é preservada
  (medição é evidência) e leva a conexão a `BLOCKED`; só um novo teste de vazamento aprovado
  devolve a linha ao serviço. É regra determinística de segurança, não ação de IA.
- **O teto de pressão da rede é congelado na conexão** (menor limite entre regulador e manifold):
  alterar depois o cadastro do componente não reescreve o que a linha montada suportava.
- **Regulador e manifold vivem no mesmo cadastro** (`gas_network_component`, discriminado por
  `kind`): compartilham identidade, código e limite de pressão; o que muda é o papel na conexão.
- **GAS-001-A — consumo de gás não entra em estoque nem em custo.** O consumo é registrado no
  módulo `gas` (massa por linha), sem movimento de estoque nem rateio por lote. Cilindro é ativo
  em comodato na maioria das cervejarias, e o modelo de custo do gás é assunto da sprint 13.
  Critério de remoção: definir com o cervejeiro se o gás é insumo de estoque ou despesa de
  utilidade, e ligar o consumo ao módulo escolhido.
- **GAS-001-B — periodicidade da requalificação não é calculada pelo sistema.** A data de
  vencimento é informada por cilindro; o sistema não deriva o próximo vencimento a partir de uma
  regra fixa de anos, porque o prazo depende de norma e do tipo de cilindro. Critério de remoção:
  confirmar a norma aplicável e transformá-la em parâmetro da cervejaria.
- O ponto de uso é um equipamento (`equipment.EquipmentProfileLookup` valida existência); um ponto
  recebe um cilindro por vez e um cilindro serve um ponto por vez, garantido por índice parcial
  único além da checagem no comando.
