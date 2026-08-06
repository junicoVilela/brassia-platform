# Status — Sprint 13

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| CST-001 | Concluída | IA | V87 + `BatchCostIT` (10) + 5 de domínio + `BrewConsumptionIT` (8); PRs #153, #154 e a tela | Novo módulo `costing`; porta `CostContributor`. Fecha `TRC-001-C` |
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

### CST-001 — custo realizado

- **Derivado enquanto aberto, congelado quando fechado.** É a mesma distinção que a sprint 12 firmou
  entre escopo e comunicação: o que é sobre o presente se deriva, o que é sobre o passado se guarda.
  Um custo aberto tem de acompanhar o que ainda acontece — um envase a mais muda o custo por litro;
  um custo fechado é a resposta daquele dia. A tabela nasce vazia: enquanto ninguém fecha, não há
  linha nenhuma, e a consulta responde do ledger.
- **Fechar é ato, não consequência.** O custo não fecha sozinho quando o lote termina: terminar de
  produzir e terminar de apurar são coisas diferentes, e a segunda tem dono, alçada e assinatura.
  Fechar duas vezes é recusado — evidência que se sobrescreve não é evidência.
- **`CostContributor` é a mesma inversão do `LineageSource`.** Somar o custo exigiria ler estoque,
  envase, sanitização e gás; em vez disso cada módulo responde pelo que sabe custar. O efeito
  colateral é o argumento: módulo que não implementa não contribui parcela, e a ausência aparece
  **como lacuna declarada** em vez de virar um zero somado no total.
- **O recorte que o custo passa é mínimo — lote e ordem.** Quem sabe quais planos de envase
  pertencem ao lote é o envase, pela consulta publicada dele. A primeira versão buscava isso na
  genealogia e o `ModularityTest` reprovou: `TraceabilityQueries` é porta interna, não tipo exposto.
  A correção deixou o desenho melhor — o custo não precisa conhecer o mundo inteiro para somá-lo.
- **Preço do lote, não preço médio.** O custo do insumo sai de `quantidade × unit_cost do lote que
  saiu`. É a razão de a `TRC-001-C` ter vindo antes: sem consumo por lote não há preço a aplicar.
- **Reserva não é custo.** Só movimento de consumo entra; somar reserva daria um custo que some
  quando a OP é cancelada.
- **O divisor do custo por litro é o volume transferido**, não o planejado. Dividir pelo planejado
  embelezaria exatamente o lote que rendeu menos, que é o lote sobre o qual se precisa saber.
- **Perda não é parcela somada, e isso protege do `double counting`** que o README lista como risco.
  A cerveja perdida não tem custo próprio: ela é o mesmo insumo já somado, e lançá-la de novo como
  "perda" contaria duas vezes. A perda aparece no indicador — custo por litro sobe quando o lote
  rende menos —, não em linha nova.
- **`CST-001-A` — mão de obra não tem fonte.** Não há hora trabalhada registrada em lugar nenhum da
  plataforma. Inventar um cadastro de horas aqui seria criar regra de negócio sem fonte; somar zero
  seria mentir por omissão. A parcela é declarada como lacuna. *Critério de remoção:* existir
  apontamento de hora por lote ou por etapa, e um contribuinte implementar a porta.
- **A tela distingue o custo que ainda muda do que não muda mais.** Um aberto e um fechado com a
  mesma cara fariam alguém decidir preço em cima de um total que ainda vai crescer. E as lacunas
  ficam ao lado do total, não no rodapé: sem mão de obra e sem utilidade, o número é menor que a
  verdade, e quem lê precisa saber disso enquanto lê.
- **`CST-001-B` — utilidade não tem fonte por lote.** Água e energia são medidas por ciclo de
  limpeza, por equipamento (CLN-005); atribuí-las a um lote exigiria uma regra de rateio que ninguém
  definiu. O CO₂ não tem preço nem vínculo com lote — o `GAS-001-A` continua aberto, e o lugar dele
  é a UTL-001 (consumo por litro), não o custo do lote. *Critério de remoção:* haver rateio definido
  pela casa, ou medição por lote.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
