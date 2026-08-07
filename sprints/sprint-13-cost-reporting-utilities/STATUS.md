# Status — Sprint 13

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| CST-001 | Concluída | IA | V87 + `BatchCostIT` (10) + 5 de domínio + `BrewConsumptionIT` (8); PRs #153, #154 e a tela | Novo módulo `costing`; porta `CostContributor`. Fecha `TRC-001-C` |
| CST-002 | A fazer | — | — | — |
| RPT-001 | A fazer | — | — | — |
| UTL-001 | Em andamento | IA | V88 + `UtilityIndicatorIT` (9) + 10 de domínio; PR do backend | Novo módulo `utilities`; portas `UtilityReadingSource` e `PackagedVolumeSource`. Sem tabela: o indicador é derivado |
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

### UTL-001 — água, energia e CO₂ por litro envasado

- **O indicador não tem tabela, e é a decisão da história.** Ele é aritmética sobre medições que já
  estão guardadas nos módulos que medem. Persistir o número criaria uma terceira verdade que
  envelheceria a cada ciclo lançado com atraso; o critério pede o contrário — o mesmo período
  responde o mesmo enquanto os fatos não mudam, e muda quando eles mudam, porque a água foi gasta.
  Mesma disciplina da sprint 12: o que é sobre o presente se deriva, o que é sobre o passado se
  guarda. A V88 cria só permissão.
- **Medido e estimado não somam num número só.** Um indicador que mistura leitura de hidrômetro com
  conta de padeiro não prova nada a auditor nenhum e não diz se a fábrica melhorou. `measuredPerLiter`
  é o que se leva a auditoria; `perLiter` existe para quem quiser somar os dois sabendo o que fez.
  Hoje nada estima — o campo existe para o dia em que alguém estimar, e é o que impede a estimativa
  de entrar disfarçada de medição.
- **Sem litro envasado não há indicador, e não é zero.** A fábrica que limpou tanque sem envasar
  gastou água sem produzir cerveja; responder "0 L/L" seria chamá-la de eficiente. O `perLiter` vem
  nulo e o consumo aparece do mesmo jeito. Pela mesma razão, utilidade que ninguém mediu não é
  listada zerada: listar as quatro faria a cervejaria que nunca mediu energia parecer uma que não
  gasta energia.
- **A cobertura é metade da resposta, e quem mede é quem a declara.** 3 L/L calculado sobre um terço
  dos ciclos é um indicador de um terço da fábrica. Só a sanitização sabe quantos ciclos encerrou,
  então a cobertura vem pela porta e não é estimada pelo indicador — e ela vale só para o que aquela
  fonte mede: a cobertura dos ciclos de limpeza não diz nada sobre o CO₂. Sem cobertura declarada,
  `fullyMeasured` é **falso**: não saber quanto foi medido não é o mesmo que ter medido tudo.
- **`UtilityReadingSource` é a mesma inversão do `LineageSource` e do `CostContributor`**, e o
  `PackagedVolumeSource` também. Se o indicador fosse buscar o volume numa consulta publicada do
  envase, utilidades dependeria de envase, que depende de sanitização, que implementa a outra porta
  daqui — o ciclo que o `ModularityTest` pegou no recall. Invertendo, utilidades não depende de
  ninguém, e um medidor novo (água na brassagem, energia na câmara fria) entra implementando a porta.
- **O divisor é o envasado, não o produzido**, e sai das execuções, não dos planos. Dividir pelo que
  ficou no tanque melhoraria o número sem melhorar a cervejaria; dividir pelo planejado daria um
  indicador que melhora quando a fábrica planeja demais.
- **O instante que conta é o do registro do consumo**, não o do início do ciclo: é quando alguém leu
  o instrumento. Um ciclo de julho com consumo lançado em agosto pertence a agosto — o contrário
  faria o número de um mês já fechado mudar depois.
- **`UTL-001-A` — o CO₂ não declara cobertura.** O consumo de gás é lançado à mão a partir da pesagem
  do cilindro, e não existe "consumo esperado" contra o qual comparar; o gás que vazou sem ninguém
  pesar não aparece. Declarar cobertura cheia seria afirmar completude que não se tem.
  *Critério de remoção:* existir baseline de consumo esperado de CO₂ (por lote envasado ou por
  período) contra o qual a pesagem possa ser comparada.
- **`GAS-001-A` segue aberto, e adiado de propósito.** A sprint 13 previa fechá-lo, mas o critério
  desta história é consumo por litro, não custo por litro: criar preço de cilindro é escopo
  comercial (compra de gás), e enfiá-lo aqui ampliaria a história por iniciativa própria.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
