# Status — Sprint 13

Estado: CONCLUÍDA

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| CST-001 | Concluída | IA | V87 + `BatchCostIT` (10) + 5 de domínio + `BrewConsumptionIT` (8); PRs #153, #154 e a tela | Novo módulo `costing`; porta `CostContributor`. Fecha `TRC-001-C` |
| CST-002 | Concluída | IA | V89 + `BatchVarianceIT` (10) + 9 de domínio + 9 de montagem + 6 de store + E2E (1); PR #157 e a tela | Sem tabela: a variação é derivada. Base de preço = lotes que a OP separou |
| RPT-001 | Concluída | IA | V90 + `BatchReportIT` (9) + 9 de montagem + 7 de store + E2E (1); PR #158 e a tela | Novo módulo `reporting`, só consumidor. Sem tabela: o dossiê é consolidação |
| UTL-001 | Concluída | IA | V88 + `UtilityIndicatorIT` (9) + 10 de domínio + 6 de store + E2E (2); PR #156 e a tela | Novo módulo `utilities`; portas `UtilityReadingSource` e `PackagedVolumeSource`. Sem tabela: o indicador é derivado |
| RPT-002 | Concluída | IA | V91 + `DashboardIT` (9) + 13 de domínio + 7 de store + E2E (2); PR #159 e a tela | Porta `IndicatorSource` no compartilhado; cinco módulos contribuem. Sem tabela |
| RPT-003 | Concluída | IA | V92 + `SavedReportIT` (12) + 20 de domínio + 8 de store + E2E (2); PR #160 e a tela | Aqui **tem** tabela: definição é acordo, não derivação. Execução usa a alçada do dono |

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
- ~~**`CST-001-A`**~~ — **FECHADO EM 2026-08-14**, com as duas metades do critério: apontamento de hora
  por lote (`production_labor_entry`) e um contribuinte implementando a porta (`LaborCostContributor`).
  A hora mora na produção e o dinheiro no custeio. Ver DEC-CST-001 na Sprint 17.
- **A tela distingue o custo que ainda muda do que não muda mais.** Um aberto e um fechado com a
  mesma cara fariam alguém decidir preço em cima de um total que ainda vai crescer. E as lacunas
  ficam ao lado do total, não no rodapé: sem mão de obra e sem utilidade, o número é menor que a
  verdade, e quem lê precisa saber disso enquanto lê.
- **`CST-001-B` — utilidade não tem fonte por lote.** Água e energia são medidas por ciclo de
  limpeza, por equipamento (CLN-005); atribuí-las a um lote exigiria uma regra de rateio que ninguém
  definiu. O CO₂ não tem preço nem vínculo com lote — o `GAS-001-A` continua aberto, e o lugar dele
  é a UTL-001 (consumo por litro), não o custo do lote. *Critério de remoção:* haver rateio definido
  pela casa, ou medição por lote.

### RPT-003 — relatórios salvos e entrega programada

- **Aqui tem tabela, e a diferença com as três histórias anteriores é o ponto.** Indicador, variação
  e dossiê são sobre o presente e se derivam. Uma definição de relatório é um **acordo**: alguém
  decidiu que este recorte, com esta periodicidade, vai para estas pessoas. Acordo não se deriva de
  nada — ele foi feito, tem autor e data, e some se não for guardado. A execução também se guarda:
  o que foi entregue, para quem e quando é passado, e refazer a consulta amanhã daria outro número.
- **A execução roda com a autorização efetiva do proprietário técnico, resolvida no instante da
  execução.** Não a de quem apertou o botão, não a que ele tinha quando salvou a definição, e nunca
  um privilégio de sistema. Congelar as permissões na definição criaria um privilégio que sobrevive
  à demissão do dono; rodar como sistema entregaria, todo mês, dados que ninguém autoriza mais.
- **Quem pede não empresta a própria alçada.** Um administrador que dispare o relatório de outra
  pessoa recebe o que a alçada *daquela pessoa* permite. Fosse o contrário, pedir a execução seria
  uma forma de ler o que não se pode ler.
- **Dono sem alçada recusa, não falha.** A execução acontece, registra `REFUSED` e o motivo. Sumir em
  silêncio faria a fábrica achar que o relatório continua indo.
- **Destinatários são usuários da plataforma, não e-mails digitados.** Não há campo de endereço
  livre, e é deliberado: só de usuário se sabe a alçada. É o que "destinatários autorizados" quer
  dizer, e o que evita entregar dado da fábrica a um endereço que ninguém verificou.
- **O link é temporário, pessoal e auditado.** Um token com prazo, e não o id da execução: o id vive
  no banco para sempre, o link tem de morrer. O token diz *qual* artefato e a sessão diz *quem* — um
  link que autenticasse sozinho viraria senha em texto claro no corpo de um e-mail. Token vencido,
  token de outro e token inexistente respondem a mesma coisa, porque a diferença entre eles só
  interessa a quem está testando tokens.
- **Ter alçada de gerir não dá acesso ao conteúdo.** Só destinatário e dono recebem link. Gerir a
  programação e ler o que ela produziu são coisas diferentes.
- **A idempotência é do banco, e a chave sai do calendário no fuso da definição.** O agendador não
  guarda "quando rodou por último" — ele tenta a chave do período atual e o índice único decide.
  Marcador de última execução seria uma segunda verdade que se perde numa restauração de backup, e
  aí o relatório sairia duas vezes ou nenhuma. Por isso também é seguro rodar o agendador de hora em
  hora: um relatório diário produz um artefato por dia, não vinte e quatro.
- **Falha de entrega não regenera nem duplica.** A chave (execução, destinatário) faz a reentrega
  atualizar a linha e contar a tentativa. O artefato já existe; reenviar nunca refaz a consulta.
- **Redefinir sobe a versão.** Sem isso, uma execução de março diria ter saído da mesma definição
  depois de alguém trocar os filtros em agosto — e o relatório antigo passaria a mentir sobre a
  própria origem. Dono e tipo não se redefinem: mudá-los seria outro relatório.
- **O formulário não tem campo de e-mail, e a ausência é a funcionalidade.** Destinatário é escolhido
  de uma lista de usuários, porque só de usuário se sabe a alçada. Um campo livre convidaria a mandar
  dado da fábrica para um endereço que ninguém verificou.
- **Execução recusada aparece como execução, não como erro.** É o caso que a história existe para
  tornar visível — o dono perdeu a permissão e o relatório parou de sair —, e escondê-lo atrás de um
  alerta passageiro faria a fábrica achar que ele continua indo.
- **Baixar sempre emite link novo.** A tela não guarda token: ele é pessoal, tem prazo e cada
  abertura é auditada. Reaproveitar um token guardado seria transformar o link numa credencial
  permanente.
- **`RPT-003-A` — não há transporte de entrega.** A plataforma registra a entrega, com tentativa e
  motivo, mas não envia e-mail: não existe infraestrutura de correio aqui, e inventá-la nesta
  história seria ampliar o escopo por iniciativa própria. É a mesma disciplina das notificações de
  recall na sprint 12, que registram a comunicação sem executá-la. *Critério de remoção:* existir
  transporte configurado (SMTP ou serviço) e política de reenvio definida pela casa.
- **`RPT-003-B` — o agendador é de instância única.** Duas instâncias rodando o mesmo cron tentam a
  mesma chave e o índice único resolve, então não há duplicação; o que não há é distribuição de
  carga nem eleição de líder. *Critério de remoção:* haver mais de uma instância em produção e
  alguém medir que o trabalho do agendador pesa.

### RPT-002 — painel operacional

- **Definição, período e drill-down são invariantes de construção, não convenção.** O construtor do
  indicador recusa os três ausentes. "Indicador sem definição" está listado como risco da sprint, e
  a única forma de um risco desses não voltar é tornar impossível criar o objeto errado: um número
  de painel sem definição escrita vira, em três meses, um número que cada pessoa da fábrica
  interpreta de um jeito.
- **Quem define é quem mede.** O que conta como "desvio em aberto" é assunto da qualidade. Um painel
  que escrevesse essas definições estaria legislando sobre domínio alheio, e a definição
  envelheceria sem que ninguém percebesse quando a regra do outro módulo mudasse.
- **A porta `IndicatorSource` mora no `shared`, e é a única posição que funciona.** O relatório já
  depende de produção, envase, custo e qualidade; se esses módulos implementassem uma porta do
  relatório, o ciclo apareceria. Com a forma no compartilhado — que é módulo `OPEN`, de apoio
  técnico — ninguém depende de ninguém: cada módulo implementa, o painel coleta. É a mesma federação
  do `LineageSource` e do `UtilityReadingSource`, com o ponto de encontro deslocado.
- **`from` nulo significa posição, não ausência.** Estoque vencendo é foto do instante; lotes
  iniciados é acumulado do intervalo. Tratar os dois como a mesma coisa faria a fábrica ler "3 lotes
  em andamento" como "3 lotes começaram no mês", que é outro número inteiramente.
- **O drill-down é recurso e filtro, não rota.** A rota é da interface, e o dia em que ela mudar não
  pode obrigar o backend a mudar junto. O painel diz "isto se abre nos lotes, filtrados assim".
- **Fonte que falha derruba o painel, de propósito.** A tentação é engolir a exceção e mostrar o
  resto; o resultado seria um painel com dois blocos a menos, indistinguível de um painel normal, e
  alguém decidiria sobre uma fábrica que acha que está vendo inteira. Pela mesma razão, a contagem
  de fontes vai na resposta.
- **Percentual sobre zero medição vem com ressalva.** Conformidade de 0% ou de 100% sobre nenhuma
  medição engana igual: a fábrica que não mediu nada não é a fábrica que passou em tudo. Mesma
  coisa para a média de custo por litro sem custo fechado no período.
- **Só custo fechado entra no painel.** O custo aberto ainda muda, e somá-lo daria um total
  diferente a cada visita sem nada ter acontecido. Quando algum dos fechados tem lacuna declarada,
  a média avisa que é menor que a verdade.
- **Média ponderada por volume, não média das médias** — senão o lote de 50 L pesaria igual ao de
  400 L.
- **A tela são cartões, não gráficos.** Cada número é um valor atual, e valor atual é cartão — uma
  barra sozinha não diz mais do que o número que ela representa. Não há série temporal nesta
  história, então não há eixo a desenhar.
- **A definição fica no cartão, não num tooltip.** É o que separa um painel de uma parede de
  números: o texto que o backend obriga a existir não pode ficar escondido atrás de um passar de
  mouse que ninguém faz.
- **A ressalva usa cor, ícone e texto.** Cor sozinha não é informação para quem não a distingue.
- **Recurso sem tela ainda vira cartão sem link**, e diz isso, em vez de link para lugar nenhum — é
  o caso das não conformidades hoje.
- **Sem tabela de painel e sem tabela de definição.** Materializar o painel mostraria a foto de
  ontem; uma tabela de definições editável por fora acabaria descrevendo um cálculo que o código não
  faz.

### RPT-001 — relatório do lote

- **Consolidação, não cálculo.** Nada no relatório soma o que outro módulo já não somou; ele junta e
  diz de onde veio cada pedaço. Recalcular custo ou rendimento aqui criaria uma segunda aritmética
  que um dia divergiria da primeira — e a divergência apareceria justamente no documento levado a
  auditor, que é o pior lugar possível.
- **Sem tabela, pela terceira vez na sprint.** O dossiê é montado a cada pedido. Guardá-lo criaria
  uma versão salva que discordaria da produção no dia seguinte, e seria essa a versão impressa. Por
  isso `generatedAt` vai no corpo: relatório derivado sem data de geração é indefensável.
- **Seção que não pôde ser preenchida vira lacuna nomeada.** Um relatório sem a seção de qualidade e
  um com a seção vazia dizem coisas opostas — "não perguntei" e "não houve medição". Só o segundo é
  aceitável, e ele diz isso com todas as letras: `unmeasured` não é aprovação. Vale igual para
  genealogia com elo faltando ou truncada, que não prova rastreabilidade completa.
- **`reporting` é o único módulo que pode depender de quase todos.** Ele só consome, e ninguém
  consome ele. Se algum módulo passasse a consultar o relatório, o relatório viraria dependência de
  produção e a plataforma inteira giraria em torno de um documento.
- **O resumo do custo passa pela consulta do custo, não por SQL próprio.** Fosse por SQL, o
  relatório responderia do que está gravado e a tela do que está derivado, e um lote aberto teria
  dois custos diferentes na mesma casa.
- **A genealogia entra resumida, nas duas pontas.** O relatório quer "de que insumos veio" e "para
  onde foi"; a topologia do meio é assunto da tela de rastreabilidade, que existe para isso. São
  duas travessias, porque subir e descer respondem perguntas diferentes.
- **Ler e exportar são verbos diferentes, com alçadas diferentes.** Ler é consulta; **exportar tira
  o documento de dentro do sistema** — a partir dali ele vive num e-mail, num pen drive, na mão de
  um auditor, e nada o traz de volta. Por isso a exportação é POST e é auditada: não pelo que ela
  calcula, mas pelo que ela permite. O registro sai depois de o relatório existir, e exportação
  recusada não deixa rastro de exportação — auditar uma que falhou seria afirmar que o documento
  saiu.
- **A tela põe as lacunas antes das seções, não depois.** É o documento que sai da casa: quem
  imprime precisa ver o que o relatório *não* prova antes de mandá-lo a um cliente que vai lê-lo
  como se provasse tudo. A data de geração fica no topo pelo mesmo motivo.
- **Exportar na tela passa pelo servidor, e não salva o que já está em memória.** O arquivo sairia
  idêntico, e sem registro nenhum de que saiu — a chamada existe pelo rastro, não pelo conteúdo.
- **`RPT-001-A` — a exportação é JSON, não PDF.** O critério da história é o documento sair com
  rastro, e isso o JSON entrega. PDF exigiria biblioteca de renderização, decisão de layout e
  identidade visual da cervejaria — escopo de apresentação, não de consolidação, e ampliar por
  iniciativa própria seria contrariar a regra da sprint. *Critério de remoção:* a casa decidir o
  layout do documento impresso e existir decisão sobre marca e assinatura.

### CST-002 — planejado versus real

- **A conta fecha, e é o critério da história.** Para cada insumo, `variação de preço + variação de
  consumo = custo real − custo planejado`, exatamente. É por isso que a variação de preço multiplica
  pela quantidade **real** e a de consumo pelo preço **planejado**: qualquer outra combinação
  deixaria um pedaço da diferença sem dono ou contaria o mesmo pedaço duas vezes — o
  `double counting` que o README da sprint lista como risco. As somas não arredondam; quem apresenta
  arredonda. Um relatório que não fecha por um centavo faz desconfiar até dos números certos, e por
  isso o `reconciles` vai no contrato: dá para conferir em vez de confiar.
- **A plataforma não tem custo padrão, e a história não inventou um.** Ninguém cadastra "quanto o
  malte deveria custar". O que existe é a decisão que a ordem tomou ao separar lotes concretos a
  preços concretos — e é contra ela que o consumo se mede. A pergunta que isso responde é a do
  brewer: *"paguei mais caro do que o que eu tinha separado?"*. **Registro da dúvida:** se a casa
  quiser variação contra custo-padrão de verdade, é preciso decidir quem cadastra esse padrão e com
  que periodicidade ele é revisto; até lá, a base é a reserva.
- **A base sobrevive porque o ledger é append-only.** Registrar consumo *libera* a reserva
  (TRC-001-C), mas o movimento de reserva continua lá. Sem esse histórico, a base de preço sumiria
  exatamente no instante em que ela passa a interessar.
- **Quantidade planejada vem da receita que a ordem congelou — ou não vem.** Se a receita foi
  republicada depois da ordem, comparar o consumo com a explosão de hoje seria comparar com uma
  receita que ninguém brassou: a versão viaja junto e a divergência vira lacuna declarada, com o
  plano vazio em vez de errado. Mesma coisa se a receita saiu de publicação.
- **Nulo e zero são coisas diferentes, e a distinção é o coração da história.** `plannedQuantity`
  nulo é "não se sabe o que a receita pedia"; zero é "não pedia nada e a brassagem usou assim
  mesmo", que é consumo extra e desvio de verdade. Coagir nulo para zero transformaria toda falta de
  base em desvio de 100%.
- **Insumo sem preço planejado sai do dinheiro em vez de virar variação de preço.** Se a ordem não
  separou aquele lote, não há base; somar o custo real sem par no planejado inflaria a "variação de
  preço" com uma diferença que é, na verdade, ausência de base. Ele aparece na lista com as
  quantidades que se conhece e vira lacuna nominal.
- **Insumo planejado e não usado tem o preço da base como preço real.** Não saiu lote nenhum, então
  não há preço real; usar o da base zera a variação de preço e joga a diferença inteira para o
  consumo, que é onde ela de fato está — o desvio foi não ter usado, não ter pago diferente.
- **Em volume, o sinal sozinho não basta.** Render 10 L a menos é ruim; perder 2 L a menos é bom. Por
  isso cada comparação de volume diz por si se é desfavorável, em vez de deixar a interface adivinhar
  pelo sinal.
- ~~**`CST-002-A`**~~ — **FECHADO EM 2026-08-14**, com a perda esperada **na receita** e não no
  equipamento como o critério sugeria: a perda característica é da cerveja tanto quanto do tanque, e a
  receita já carrega eficiência e volumes. Percentual, porque perda de trub e absorção de lúpulo escalam
  com o tamanho da brassa. Sem cadastro, a perda continua entrando como fato — o raciocínio original
  segue valendo para quem ainda não mediu. Ver DEC-CST-002 na Sprint 17.
- **Alçada separada da leitura do custo.** A variação expõe preço de compra por ingrediente, que é
  informação comercial: quem pode ver o total do lote não necessariamente pode ver por quanto a casa
  comprou o malte. Daí `costing.variance.read`, e não `costing.cost.read`.
- **Sem tabela, e derivada mesmo com o custo fechado.** O custo é a resposta daquele dia; a
  explicação é sobre os fatos, e os fatos continuam sendo corrigidos depois do fechamento. Congelar
  a explicação criaria uma segunda verdade ao lado do custo.
- **A tela põe preço e consumo lado a lado, na primeira linha.** São causas diferentes com donos
  diferentes: preço é conversa com fornecedor, consumo é conversa com a brassagem. Quem abre a tela
  quer saber com quem falar.
- **O que não tem base não é apresentado como desvio.** Insumo sem preço planejado sai da tabela do
  dinheiro e ganha uma tabela própria, com "não se sabe" e "não confirmado" por extenso; perda sem
  esperado aparece como fato, sem cor de alerta. Um relatório que chama de desvio o que não tem
  contra o que medir ensina o brewer a ignorar o relatório.
- **A tela avisa quando a conta não fecha.** Se `reconciles` vier falso, o aviso é vermelho e diz
  para não decidir nada com aquele número. É o caso que nunca deveria acontecer — e justamente por
  isso precisa ser barulhento se acontecer.
- **Direção das dependências.** O estoque já depende do custo (contribui parcelas), então os fatos
  de material vêm por porta invertida (`costing.MaterialActualSource`, implementada pelo estoque). O
  plano, o rendimento e o envase vêm de consultas publicadas — `planning.OrderPlanLookup`,
  `production.BatchOutcomeLookup`, `packaging.PackagingOutcomeLookup` —, porque nenhum desses
  módulos depende do custo. Escolher a direção errada em qualquer um deles fecharia um ciclo que o
  `ModularityTest` pega.

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
- **A tela põe a cobertura ao lado do número, não no rodapé.** Um consumo por litro calculado sobre
  um terço dos ciclos parece um indicador da fábrica; quem lê precisa saber que é de um terço dela
  enquanto lê. Utilidade sem cobertura declarada diz isso por extenso em vez de exibir um selo de
  completude que não tem lastro. Onde há estimativa, o texto repete qual é o número de auditoria.
- **Sem envase, o cartão mostra "—" e não "0".** É a mesma decisão do domínio levada à interface: o
  traço obriga a ler a explicação, o zero seria lido como resultado bom.
- **O fim do período é inclusivo para quem pede e exclusivo para quem calcula.** Quem escolhe "até
  31/08" quer o dia 31 inteiro; a conversão para a meia-noite seguinte é feita uma vez na store,
  para a tela não ter de explicar essa aritmética a ninguém.
- **`GAS-001-A` segue aberto, e adiado de propósito.** A sprint 13 previa fechá-lo, mas o critério
  desta história é consumo por litro, não custo por litro: criar preço de cilindro é escopo
  comercial (compra de gás), e enfiá-lo aqui ampliaria a história por iniciativa própria.

## Evidências de encerramento

- **Build/commit:** PRs #153 (TRC-001-C), #154 e #155 (CST-001), #156 (UTL-001), #157 (CST-002),
  #158 (RPT-001), #159 (RPT-002) e #160 (RPT-003). Todos com os cinco checks da CI verdes —
  backend, contratos, E2E, frontend e verificação de segredos.
- **Testes executados:** backend **783** (domínio, integração com PostgreSQL real via Testcontainers,
  autorização negativa, isolamento entre cervejarias e `ModularityTest`); frontend **342** em 64
  arquivos; E2E **28** contra a stack real, em 14 specs.
- **Migration aplicada:** V87 (custo do lote), V88 (permissão do indicador de utilidades), V89
  (permissão da variação), V90 (permissões do relatório), V91 (permissão do painel) e V92
  (relatórios salvos, execuções, links e entregas). Todas testadas em banco limpo pela CI.
  **Quatro das seis criam só permissão:** indicador, variação, dossiê e painel são derivados, e a
  única história que ganhou tabela foi a RPT-003, porque uma definição de relatório é acordo.
- **Contratos atualizados:** OpenAPI com custo do lote, planejado × real, consumo por litro,
  relatório do lote e sua exportação, painel operacional, relatórios salvos, execuções, link
  temporário e download; Problem Details novos documentados junto das rotas que os produzem.
- **Riscos remanescentes:** os débitos da tabela abaixo, nenhum bloqueando o uso do que foi
  entregue. Os dois que mais afetam quem opera são o **`CST-001-A`/`CST-001-B`** — o custo do lote
  não tem mão de obra nem utilidade, e portanto é sempre menor que a verdade, ainda que declare isso
  — e o **`RPT-003-A`**: a entrega programada registra, mas não envia. Quem depender de receber o
  relatório por e-mail vai precisar buscá-lo na tela.
- **Aceite:** **Valdemir Vilela Junior, 2026-08-07** — aceita com as ressalvas registradas em
  `ACCEPTANCE.md`. Um débito foi fechado (`TRC-001-C`) e sete foram abertos; o `GAS-001-A`, que esta
  sprint previa fechar, segue aberto por decisão registrada. O aceite libera a sprint, não os
  débitos.

## Débitos abertos ao fim da sprint

Todos com critério de remoção registrado nas seções acima.

| Débito | O que falta |
|---|---|
| ~~`CST-001-A`~~ | **Fechado em 2026-08-14**: apontamento de hora no lote + taxa da casa (V117) |
| `CST-001-B` | Utilidade não tem fonte por lote: água e energia são medidas por equipamento |
| ~~`CST-002-A`~~ | **Fechado em 2026-08-14**: perda esperada por etapa na receita (V118) |
| `UTL-001-A` | O CO₂ não declara cobertura: não existe consumo esperado contra o qual comparar |
| `RPT-001-A` | A exportação é JSON, não PDF: layout e identidade visual não foram decididos |
| ~~`RPT-003-A`~~ | **Fechado em 2026-08-11 por decisão**: a plataforma registra e não envia; enviar exige SMTP, bounce e LGPD. Ver DEC-DEBT-001 na Sprint 17 |
| `RPT-003-B` | O agendador é de instância única: não duplica, mas não distribui carga |
| `GAS-001-A` | Custo e estoque do gás. **Adiado de novo em 2026-08-11, agora por decisão registrada**: entra quando alguém reclamar da ausência dele no custo do lote. Ver DEC-DEBT-001 na Sprint 17 |

Fechado nesta sprint: **`TRC-001-C`** — o dia de brassa passou a registrar consumo por lote, o que
era pré-requisito do custo realizado.

Seguem abertos, herdados e sem relação com o escopo desta sprint: `TRC-001-A`, `FDS-001-A`,
`FDS-001-B`, `FDS-002-B`, `FDS-003-A` e `FDS-004-A` (sprint 12), além dos anteriores registrados nos
`STATUS.md` das sprints 08 a 11.
