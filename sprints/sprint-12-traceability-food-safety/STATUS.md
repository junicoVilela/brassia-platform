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
- ~~**`TRC-001-B` — o envase não gera lote de produto acabado.**~~ **Fechada** antes da FDS-002, em
  PR próprio: a execução do envase passa a criar um `FinishedLot` no mesmo commit, com código
  derivado do lote de produção (`LOTE-100/1`). Ver a seção própria abaixo. O que sobrou da lacuna
  virou **`TRC-001-D`**.
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

#### Segundo achado fora do escopo: o Problem Details nunca chegava às telas

O `problemDetailsInterceptor` **desembrulha** o corpo do erro — quem trata recebe o Problem Details,
com `code`, `detail` e as extensões no primeiro nível. **Sete stores liam `e.error?.code`**, que
depois do interceptor é sempre `undefined`. O efeito: toda recusa específica da plataforma caía na
mensagem genérica. "Você já enviou ficha para a amostra 473" virava "Não foi possível concluir a
operação"; os impedimentos da conexão de gás, os bloqueios da liberação de OP e a recusa do plano de
controle, idem.

Não havia como o teste de unidade pegar: **os specs simulavam o erro na forma errada também**, então
teste e store concordavam entre si e discordavam da aplicação. Quem pegou foi a jornada E2E da tela
de genealogia, contra a stack real — o segundo defeito de integração que o harness encontra, depois
dos endpoints paginados tratados como array.

Corrigido nas nove stores afetadas (metrologia, sensorial, envase, qualidade, gás, ordens,
sanitização, parâmetros e rastreabilidade) e nos specs correspondentes, com o contrato agora escrito
por extenso no próprio interceptor.

### TRC-001-B — lote de produto acabado

- **O lote nasce da execução, no mesmo commit.** Não existe comando de criação: criar um lote à mão
  seria afirmar que existe cerveja envasada que nenhuma execução registrou. `UNIQUE (run_id)` faz a
  criação idempotente.
- **Fica no envase, não no estoque.** `stock_lot` é sobre insumo comprado — tem `supplier_id`
  obrigatório e aponta para item de catálogo. Produto acabado não tem fornecedor e não é
  ingrediente; forçá-lo ali exigiria um fornecedor falso e um tipo de ingrediente que não é
  ingrediente. O agregado mora onde o fato acontece.
- **Só as unidades boas entram.** Rejeito consumiu embalagem e não virou produto; contá-lo faria o
  recall procurar latas que ninguém pode devolver.
- **Não guarda validade.** Ela vem da evidência de oxigênio (FSL-001), por plano, e pode ser
  sobreposta com justificativa. Copiá-la para o lote criaria uma segunda verdade que divergiria do
  override em silêncio — o mesmo motivo pelo qual o grafo da TRC-001 também é derivado.
- **O código é derivado, não digitado:** lote de produção mais a ordem do envase. Ele vai impresso na
  embalagem e precisa levar de volta à origem sem consulta nenhuma; um código digitado quebraria essa
  leitura no primeiro erro de digitação. Um lote de produção dividido em vários envases gera vários
  lotes de produto acabado, cada um com identidade própria — foram latas diferentes, em momentos
  diferentes, e um recall pode atingir só uma delas.
- **`TRC-001-D` — não há expedição nem destino.** É a metade de fora da fábrica, que a TRC-001-B não
  fecha: o lote existe, mas a plataforma não registra para onde ele foi. A lacuna agora é declarada
  no nó do produto acabado, e é o que a **FDS-003** vai precisar para dizer a quem o recall se
  dirige. *Critério de remoção:* registrar expedição com destino e contato, ligando-a ao lote.

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
