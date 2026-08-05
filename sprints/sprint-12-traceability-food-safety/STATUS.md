# Status — Sprint 12

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| TRC-001 | Concluída | IA | V80 + `TraceabilityIT` (11 testes); PRs #142, #143, #144 | Novo módulo `traceability`; porta `LineageSource`. TRC-001-B fechada em #144 |
| FDS-001 | Concluída | IA | V82 + `FoodSafetyIT` (9 testes) + 17 de domínio; PR #145 (backend) e a tela | Novo módulo `foodsafety`; fecha `PKG-004-A` |
| FDS-002 | Concluída | IA | V83 + `QuarantineIT` (11 testes) + 11 de domínio; PR #147 (backend) e a tela | Bloqueio derivado do grafo; alçadas separadas |
| FDS-003 | Backend concluído | IA | V84 + V85 + `RecallIT` (9 testes) + 9 de domínio | Fecha `TRC-001-D` e `FDS-002-A`. Tela em PR separado |
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

### FDS-001 — matriz de alergênicos

- **A lista de alergênicos é da casa, não da plataforma.** Era a decisão a tomar antes de qualquer
  linha de código, e a tentação era um enum com a lista da RDC 26/2015. Embutir a norma no código
  a congela: ela muda, difere por país e quem responde por ela é quem assina o rótulo — o mesmo
  motivo pelo qual `LabelRegulatoryRule` já é por cervejaria. A cervejaria cadastra o vocabulário e
  a plataforma cuida de propagá-lo. O domínio garante só a forma do código (caixa alta, estável),
  para que "GLUTEN" e "gluten" nunca sejam dois alergênicos no meio de um recall.
- **Não declarado ≠ declarado isento, e a estrutura do banco é que garante isso.** A declaração é
  uma linha própria, separada dos alergênicos declarados: ingrediente com linha de declaração e
  nenhum alergênico foi declarado isento; ingrediente sem linha nenhuma é lacuna. As duas produzem
  o mesmo conjunto vazio em memória, e confundi-las é o que imprime "não contém glúten" numa
  cerveja de cevada. A distinção atravessa tudo — `AllergenDeclaration.missing`, `complete()` no
  perfil, o campo `declared` na matriz.
- **O perfil do lote é derivado, como o grafo da TRC-001.** É a soma das declarações dos
  ingredientes da receita publicada, lida na hora. Guardá-lo faria o lote continuar dizendo
  "isento" depois de alguém declarar o alergênico do malte.
- **A matriz liga quando a casa adere, e essa foi a correção mais importante do desenho.** A
  primeira versão bloqueava toda troca de produto sem declaração completa — o que pararia a linha
  de quem nunca pediu a funcionalidade, no dia do deploy. Sem alergênico cadastrado, a matriz não
  está em uso e a troca não é avaliada; ela passa a valer no instante em que a cervejaria cadastra
  o primeiro alergênico. É o precedente da validade do CIP (PRM-001), que sem prazo configurado
  não expira. Ganhar um campo não pode mudar o comportamento de quem não o preencheu.
- **A lacuna só bloqueia onde mudaria a resposta.** Em equipamento sem uso anterior não há troca a
  avaliar, e exigir declaração ali seria burocracia com cara de segurança. Onde há uso anterior —
  ou dedicação declarada — a lacuna bloqueia, porque "não sei" não pode valer como "não tem". Quem
  guarda a verdade do que a cerveja contém é o rótulo, que continua sem imprimir o campo sem fonte.
- **A carga residual é a diferença entre os perfis, não o perfil anterior.** Se os dois lados têm o
  mesmo alergênico, não há troca a fazer. POP exigido sem motivo é POP que se aprende a ignorar.
- **Dedicação não se resolve com limpeza.** Equipamento é compartilhado por omissão; declarar
  dedicação afirma o contrário. Dedicação com conjunto vazio é a linha *livre de alergênicos*, a
  mais restritiva que existe — nenhum POP a resgata, porque ali a garantia é o alergênico nunca ter
  entrado, não o procedimento.
- **O envase pergunta, a segurança de alimentos responde.** `ChangeoverCheck` é porta publicada e o
  uso anterior é parâmetro: quem sabe qual foi o último lote da linha é quem agenda (hoje o envase,
  amanhã a produção). O envase não conhece alergênico, e não deveria. `PackagingPlanRepository.
  lastLineUse` passou a devolver lote + instante — a limpeza responde "quando", a troca "o quê".
- **`PKG-004-A` fechada.** O campo de alergênicos do rótulo tem fonte: a matriz. Perfil incompleto
  não escreve nada, e ausente obrigatório barra a impressão — que é o comportamento correto, porque
  imprimir isenção que ninguém assinou é pior do que não imprimir. A revalidação do rótulo sai de
  graça: a prévia é remontada das fontes a cada impressão, então declaração alterada é reavaliada
  contra a matriz vigente, não contra a que valia na primeira tiragem.
- **A tela mostra todo equipamento, e não só os dedicados.** A API devolve apenas os dedicados —
  quem não está lá é compartilhado. Uma tela que listasse só o que a API devolve esconderia
  exatamente o caso de risco, que é o equipamento compartilhado. O cruzamento é feito no store,
  contra o cadastro de equipamentos e os POPs da sanitização.
- **A jornada E2E cobre o fan-out de três endpoints.** É a forma de defeito que os testes de
  unidade não pegam — um deles é paginado (`/equipment`), e foi assim que as telas de envase e gases
  quebraram em silêncio na sprint 10.
- **`FDS-001-A` — a embalagem não entra no perfil.** O perfil vem da composição da receita; a lata
  ou garrafa do plano de envase não é avaliada, embora possa ter revestimento com alergênico
  declarável. *Critério de remoção:* incluir o item de embalagem do plano nas contribuições do
  perfil, quando houver fonte que declare alergênico de material de embalagem.
- **`FDS-001-B` — a troca só é checada no envase.** A brassagem e a fermentação usam equipamento
  compartilhado e não consultam a matriz: nenhuma delas registra hoje "qual lote ocupou este tanque
  antes". *Critério de remoção:* produção e fermentação passarem a responder qual foi o uso
  anterior do equipamento e a chamar `ChangeoverCheck`, como o envase já faz.

### FDS-002 — quarentena

- **A intenção propaga, e essa foi a decisão de negócio da história.** Uma aresta `INTENDED` — hoje
  a reserva de insumo, que registra o lote *separado* para a OP e não o que foi ao moinho — alcança
  o descendente e **bloqueia**, mas chega marcada como suspeita. O raciocínio: o custo de bloquear a
  mais é estoque parado; o de bloquear a menos é produto na rua. O que não se pode é bloquear sem
  dizer qual dos dois casos é, porque quem investiga precisa saber onde apertar primeiro. Quando
  dois caminhos chegam ao mesmo nó, o confirmado vence — basta um caminho de fato para o alcance
  deixar de ser suposição. Se a `TRC-001-C` for fechada (consumo por lote no dia de brassa), essas
  suspeitas viram confirmações sozinhas, sem uma linha de código mudar aqui.
- **O que se guarda é a origem; o alcance é derivado.** Congelar a lista de descendentes na abertura
  seria mais rápido e estaria errada no dia seguinte: um envase criado depois não estaria nela e
  passaria. A `QuarantineIT` fixa exatamente esse caso. Mesmo princípio do grafo (TRC-001) e do
  perfil de alergênicos (FDS-001) — a tabela `traceability_quarantine` não tem coluna de alcance, e
  a ausência dela é a decisão.
- **A travessia é a mesma, lida dos dois lados.** "O que este lote contaminou" é `FORWARD` a partir
  da origem; "que bloqueio alcança este plano" é `BACKWARD` a partir do plano. Um `Spread` só, e um
  bean só servindo à tela e aos módulos que bloqueiam — duas implementações acabariam divergindo, e
  a tela mostraria um descendente que o envase deixou passar.
- **Abrir e liberar são alçadas separadas.** Foi a única forma de dar sentido a "liberação exige
  alçada": uma permissão só, usada nos dois comandos, tornaria a liberação tão barata quanto a
  abertura. A justificativa obrigatória é a outra metade — liberar é afirmar que o produto pode
  seguir, e quem assina precisa ter dito por quê.
- **O envase é impedido na reserva e na execução.** Só na reserva não bastaria: a investigação quase
  sempre começa depois que o plano foi reservado, e é justamente o envase que ela precisa impedir.
- **`FDS-002-A` — a expedição não é impedida porque não existe.** O critério da história fala em
  "envase/expedição são impedidos"; a metade de fora da fábrica continua sendo a `TRC-001-D`. O nó
  do produto acabado é quarentenável e o bloqueio o alcança, mas não há operação de saída para
  recusar. *Critério de remoção:* existir expedição e ela consultar `QuarantineCheck`, como o envase
  já faz.
- **`FDS-002-B` — o bloqueio não alcança além de seis saltos.** A contenção usa a mesma profundidade
  padrão da genealogia. O corte é declarado (`truncated`) na consulta de detalhe, mas o bloqueio em
  si não avisa quando o nó ficou fora do alcance por profundidade. *Critério de remoção:* medir a
  profundidade real das cadeias em produção e, se seis for pouco, tornar o teto parâmetro (PRM-001).

- **Quarentenar mora na genealogia, não numa tela de cadastro.** A investigação começa olhando a
  cadeia do lote suspeito; obrigar a sair dali, abrir outra tela e recolar o id seria trocar o
  contexto no pior momento. A lista de quarentenas responde à outra pergunta — o que está parado.
- **O alcance é buscado do servidor a cada abertura, também no frontend.** Guardá-lo no store
  recriaria no navegador a mesma cópia envelhecida que o backend recusa a manter.

### FDS-003 — abrir recall (e a expedição que ela exigiu)

- **A `TRC-001-D` foi fechada dentro desta história, e tinha de ser.** O critério pede "identificar
  origem, destinos, contatos e ações", e sem expedição o recall identificava a origem e não
  alcançava ninguém. A fatia é mínima de propósito: não há pedido, nota, preço nem cliente
  cadastrado — distribuição comercial é assunto das sprints 19 e 20, e antecipá-la criaria um
  cadastro de clientes pela porta dos fundos que aquelas sprints teriam de desmanchar. O destino é
  texto de quem expediu; quem digita é quem responde.
- **O escopo é derivado; a comunicação é guardada.** É a divisão que estrutura a história. O escopo
  sai do grafo a cada leitura, como o da quarentena — "escopo reproduzível", que o critério pede, é
  a mesma origem com a mesma profundidade dando a mesma resposta. Já notificar um cliente é *fato
  sobre o que a cervejaria fez*: derivá-lo apagaria a prova de que ele foi avisado, então cada
  destino alcançado na abertura vira linha própria, com os dados copiados da expedição. Aqui a cópia
  é a coisa certa justamente porque o registro é sobre o passado.
- **Destino descoberto depois não entra calado entre os avisados.** Um lote que sai *depois* da
  abertura aparece em `newDestinations`, separado. Misturá-lo com os notificados faria a cobertura
  subir sozinha e o dossiê afirmar comunicação que não houve.
- **A cobertura mede o que se conhece, e as lacunas dizem o que falta conhecer.** Lote no escopo sem
  expedição registrada é caixa de cerveja que ninguém sabe onde está; ler a cobertura sem as lacunas
  superestima o alcance do recall.
- **Não se encerra com destino pendente.** Encerrar assim declararia terminada uma operação que
  deixou cerveja na prateleira de quem não foi avisado — e o dossiê diria isso para sempre. Quem
  precisa encerrar sem ter falado registra a comunicação com o canal e a observação do que
  aconteceu; o que não se pode é omitir. O canal é obrigatório: "avisamos" sem dizer como não prova
  nada.
- **`FDS-002-A` fechada de tabela.** Lote em quarentena não é expedido — era a metade da contenção
  que faltava, porque a quarentena impedia envasar e deixava passar justamente o embarque.
- **A porta dos destinos é da rastreabilidade, e o envase a implementa.** A primeira versão fazia o
  contrário (o recall consultava uma porta publicada do envase) e o `ModularityTest` reprovou:
  envase já depende de rastreabilidade, pela linhagem e pela quarentena, e a consulta fechava o
  ciclo. Inverter resolveu, e trouxe o mesmo efeito colateral do `LineageSource`: quando existir
  saída por outro caminho — venda direta, doação —, o módulo dono implementa `DestinationSource` e o
  recall passa a alcançar aquele destino sem que uma linha da rastreabilidade mude.
- **O recall não abre quarentena sozinho.** São decisões diferentes, com alçadas diferentes, e um
  comando que dispara o outro em silêncio esconderia metade do que aconteceu. A tela oferece as
  duas; quem decide é quem investiga.
- **`FDS-003-A` — a expedição não tem correção nem estorno.** Registrar é fato; devolução, cancelamento
  e transferência entre destinos não existem. *Critério de remoção:* as sprints 19/20 definirem
  movimentação comercial, com o recall passando a enxergar a saída líquida.

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
