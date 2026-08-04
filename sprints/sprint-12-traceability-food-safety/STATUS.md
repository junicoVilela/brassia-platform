# Status — Sprint 12

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| TRC-001 | Backend concluído | IA | V80 + `TraceabilityIT` (11 testes) | Novo módulo `traceability`; porta `LineageSource`. Tela em PR separado |
| FDS-001 | A fazer | — | — | — |
| FDS-002 | A fazer | — | — | — |
| FDS-003 | A fazer | — | — | — |
| FDS-004 | A fazer | — | — | — |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

### TRC-001

- **O grafo é derivado, não materializado.** Guardá-lo criaria uma segunda verdade, que envelheceria
  no instante seguinte a qualquer envase ou reserva. Mesmo princípio do saldo de estoque (STK-002) e
  da aptidão do instrumento (MTR-001). A migration V80 não cria tabela: cria a permissão e um índice
  em `stock_movement (brewery_id, reference, type)`, que a busca para trás passou a exigir.
- **Não é uma CTE recursiva única, e essa foi a decisão mais consequente.** Os entregáveis da sprint
  falavam em "consulta de grafo em SQL/CTE inicial", mas uma consulta recursiva só teria de ler as
  tabelas de cinco módulos — o que o `AGENTS.md` proíbe e o `ModularityTest` reprova. Em vez disso,
  o módulo `traceability` declara a porta publicada `LineageSource` e **cada módulo dono do dado a
  implementa**, lendo apenas as próprias tabelas; a travessia em largura acontece no domínio de
  rastreabilidade. Cinco implementações hoje: estoque, planejamento, produção, fermentação e envase.
- **O efeito colateral vale mais que a decisão.** Módulo que não implementa a porta não contribui
  aresta nenhuma, e a lacuna aparece **como lacuna** — em vez de virar uma junção silenciosamente
  vazia no meio de um SQL grande. Implementação nova entra sem que uma linha do serviço mude.
- **A aresta tem força: `CONFIRMED` ou `INTENDED`.** É a distinção mais importante da história. A
  reserva de insumo registra qual lote foi *separado* para a OP, não qual foi ao moinho. Num recall,
  tratar intenção como fato é recolher o lote errado — então a API devolve os dois rotulados, e
  nunca promove um ao outro.
- **Nó inexistente é 404, não grafo vazio.** "Não há elo" e "não há nó" são respostas opostas:
  uma diz que a rastreabilidade tem uma lacuna, a outra que a pergunta estava errada.
- **O corte de profundidade se declara (`truncated`).** Dez saltos é o teto; o padrão é seis. Um
  recall que parece completo sem ser é pior do que um declaradamente parcial.
- **A identidade do nó é tipo+id, e o rótulo não entra.** O mesmo lote descoberto por dois caminhos
  chega com rótulos montados por provedores diferentes; se o rótulo distinguisse nós, a travessia
  deixaria de fechar ciclo e andaria em círculo — e ciclo existe de verdade, na linhagem de levedura
  (lote → coleta → lote).
- **A coluna `stock_movement.reference` não tem tipo.** Ela aponta para uma OP quando é reserva e
  para um plano quando é consumo do envase, sem nada no banco dizendo qual. O provedor de estoque
  desempata pelo `reason = 'envase'`; sem isso, um consumo lançado à mão com referência qualquer
  viraria no grafo um "plano de envase" inexistente — aresta inventada é pior que aresta faltando.

#### As três lacunas, que são o entregável tanto quanto o grafo

O critério da história é "ausência de elo é evidenciada". As três aparecem em `gaps`, com motivo:

- **`TRC-001-A` — não existe blend.** O objetivo cita "insumo, OP, lote, blend e embalagem", mas a
  plataforma não tem nenhum conceito de misturar lotes: nem tabela, nem agregado, nem comando.
  Modelar blend aqui seria inventar regra de negócio sem fonte. *Critério de remoção:* existir um
  agregado de mistura, com origem e proporção, e o provedor de produção passar a devolver a aresta.
- **`TRC-001-B` — o envase não gera lote de produto acabado.** A cadeia para a frente termina na
  execução do envase: sem lote de saída não há expedição, sem expedição não há destino, e sem
  destino não há a quem avisar. **É a lacuna mais cara da plataforma hoje e ela cai em cima da
  FDS-003**, que precisa justamente de origem, destinos e contatos. *Critério de remoção:* o envase
  criar lote de produto acabado no estoque e a expedição registrar o destino.
- **`TRC-001-C` — o dia de brassa não registra consumo por lote de insumo.** Só existe a reserva da
  OP, que é intenção. *Critério de remoção:* lançar movimento de consumo por lote no encerramento da
  brassagem, e a aresta passar de `INTENDED` a `CONFIRMED`.

#### Achado fora do escopo, corrigido porque não dava para deixar

O advice global (`ApiExceptionHandler`) tem um `@ExceptionHandler(Exception.class)` que pega tudo, e
advices sem ordem declarada **empatam** — o desempate acaba sendo a ordem de descoberta dos beans,
isto é, alfabética por pacote. Os módulos anteriores a "shared" venciam por acidente; `traceability`
é o primeiro depois, e as recusas dele viravam **500 em vez de 404 e 400**. Descoberto pela IT.
Corrigido para todos: os sete advices de módulo declaram `@Order(0)` e o catch-all declara a menor
precedência possível. Nenhum outro módulo mudou de comportamento — a suíte inteira confirma.

### Antes de começar

- **Jornada E2E de negócio — herdada do aceite da sprint 11 (2026-08-04).** O item de E2E do DoD
  ficou aberto lá: o harness cobre navegação, sessão e integração frontend↔API, não um fluxo de
  negócio ponta a ponta. Esta sprint é o lugar natural para fechá-lo — o simulado de recall
  (`FDS-004`) é, por definição, um fluxo de negócio completo com começo, meio e prova de que
  funcionou. Se a jornada couber na FDS-004, o item do DoD deixa de ser débito.
- **`FDS-001` fecha `PKG-004-A`** (alergênicos), aberto desde a sprint 10.
- **A genealogia é a fundação das outras quatro.** Quarentena, recall e simulado se apoiam no grafo
  de `TRC-001`, e o próprio README lista "elo ausente" como risco. Rodar `TRC-001` sozinha primeiro
  expõe os elos faltantes antes de três histórias dependerem deles.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
