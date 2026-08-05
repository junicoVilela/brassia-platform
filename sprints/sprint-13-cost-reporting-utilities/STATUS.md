# Status — Sprint 13

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| CST-001 | Em andamento | IA | `BrewConsumptionIT` (8 testes) | Fecha `TRC-001-C` primeiro: sem consumo confirmado, custo realizado é estimativa |
| CST-002 | A fazer | — | — | — |
| RPT-001 | A fazer | — | — | — |
| UTL-001 | A fazer | — | — | — |
| RPT-002 | A fazer | — | — | — |
| RPT-003 | A fazer | — | — | — |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

### Antes de começar — o que o custo tem e o que não tem

Levantamento feito antes da primeira linha de código. Das cinco parcelas que a CST-001 pede
(insumo, embalagem, utilidade, perda e mão de obra), **duas não tinham fonte nenhuma**:

| Parcela | Fonte |
|---|---|
| Embalagem | real: o envase consome estoque na execução |
| Insumo | **era** só a reserva da OP (intenção) — `TRC-001-C`, fechado nesta sprint |
| Utilidade — água/energia | parcial: o ciclo de limpeza (CLN-005) registra por equipamento, não por lote |
| Utilidade — CO₂ | inexistente: `GAS-001-A`, adiado explicitamente para esta sprint |
| Perda | derivável do balanço de volume do envase e das perdas de transferência |
| Mão de obra | inexistente: não há conceito de hora trabalhada na plataforma |

Além disso, `stock_movement` **não tem coluna de custo** — o preço vive em `stock_lot.unit_cost`. O
custo realizado sai de movimento × preço do lote, o que é rastreável e exige porta publicada do
estoque, porque custo não lê tabela alheia.

### TRC-001-C — consumo do dia de brassa (fechado dentro da CST-001)

- **Foi feito primeiro porque o custo depende dele.** Somar o preço do lote *reservado* daria um
  "custo realizado" que é estimativa com outro nome — e, num recall, já era recolher o lote errado.
- **O sistema propõe, o operador confirma.** A proposta é a reserva viva da OP; confirmar é o ato
  humano que transforma intenção em fato. Assumir a proposta automaticamente seria afirmar que o
  lote separado foi ao moinho, que é exatamente a mentira que o débito existia para evitar — e o
  brewer que trocou de lote porque o reservado acabou precisa poder dizer isso.
- **Libera antes de consumir, e devolve a sobra.** Sem a liberação o mesmo malte contaria duas
  vezes, uma como reservado e outra como consumido; sem a devolução, a OP brassada ficaria segurando
  insumo que já virou cerveja.
- **Consumo confirmado substitui a reserva na genealogia.** Mostrar as duas arestas contaria o mesmo
  malte duas vezes — uma como intenção e outra como fato — que é o `double counting` que o próprio
  README da sprint lista como risco a testar.
- **Registrar duas vezes é recusado.** Dobraria consumo e custo. Corrigir um consumo errado é ajuste
  de estoque, que tem comando e rastro próprios. Na prática a tela nem consegue: depois do registro
  a proposta vem vazia.
- **Lote em fermentação ainda aceita consumo.** Quem esqueceu de registrar no dia consegue no
  seguinte — registrar tarde é melhor do que nunca. Encerrado ou cancelado, não.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
