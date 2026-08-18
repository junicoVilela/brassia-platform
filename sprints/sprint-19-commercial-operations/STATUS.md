# Status — Sprint 19

Estado: **CONCLUÍDA em 2026-08-15** — 6/6 histórias entregues e mergeadas, mais a SAL-001-B que nasceu de
uma dúvida respondida no meio do caminho. Aguardando aceite (validação manual).

**A sprint inteira foi construída sob uma premissa declarada, e ela continua valendo:** o núcleo não está
em produção (REL-001 e o ciclo da REL-005 seguem abertos), e o que se assumiu foi que *desenvolver não
exige produção; publicar exige*. Nenhuma história desta sprint deve ir para produção antes de o release
ser validado. Ver DEC-SPR-019.

| História | Estado | Evidência/PR |
|---|---|---|
| CRM-001 | Concluída | PRs #228/#229 · `crm/*`, `V119` · 26 unitários + 13 IT |
| SAL-001 | Concluída | PR #230 · `sales/*`, `V120` · 21 unitários + 10 IT |
| SAL-001-B | Concluída | PR #231 · `packaging/SellableLotLookup`, `V121` · 4 IT |
| SAL-002 | Concluída | PR #232 · `sales/*`, `V122` · 15 unitários + 8 IT |
| SAL-003 | Concluída | PR #233 · `sales/adapter/inbound/portal`, `V123` · 6 unitários + 8 IT |
| FCST-001 | Concluída | PR #234 (demanda) + a capacidade em 2026-08-18 · `forecast/*`, `V124`, `V139` · Ver DUV-FCST-001 |
| INT-008 | Concluída | PR #235 · `integration/*`, `sales/SalesOrder*` · 4 IT |

**A SAL-001-B não estava no backlog.** Ela nasceu da `DUV-SAL-001` — "o que torna um lote vendável" —, que
era metade da SAL-001 e dependia de decisão do mantenedor. Registrá-la como história própria em vez de
enfiá-la na SAL-001 é o que mantém o histórico legível: quem ler daqui a seis meses vê que houve uma
pergunta, uma resposta e um trabalho, e não um escopo que inchou sozinho.

Ordem prevista: **CRM-001 → SAL-001 → SAL-002 → SAL-003 → FCST-001 → INT-008**. Não é arbitrária — pedido
precisa de cliente e de produto com preço, e portal B2B precisa de pedido. A previsão de demanda vem
depois porque ela lê histórico de pedido, e antes disso não há histórico nenhum para ler.

## Decisões e bloqueios

### DEC-SPR-019 — Por que a 19, e o que ela assume que ainda não é verdade

**A escolha real era entre a 18 e a 19.** A Sprint 20 depende explicitamente da 19, então não podia vir
antes; a 21 segue bloqueada por falta de credencial de teste dos provedores (DEC-INT-001).

**Escolhida a 19, por três motivos:**

- **É a única que destrava outra coisa.** A Sprint 20 (contêineres e distribuição) depende dela. A 18 não
  destrava nada — adiá-la custa só o adiamento dela mesma.
- **A 18 é a que aponta para fora.** Biblioteca pública, link compartilhado, fork, denúncia e moderação
  colocam dado de cervejaria fora da cervejaria. Construir exposição pública sobre um release que ninguém
  validou é a combinação de riscos que menos se quer ter — ainda mais com a restauração não medida
  (REL-001). A 19 é interna: cliente, pedido, preço, previsão.
- **As dependências técnicas dela acabaram de amadurecer.** A 19 pede "Sprints 05, 06 e 13 maduras", e a
  Sprint 17 fechou justamente os débitos que faltavam na 13 — mão de obra no custo (CST-001-A), perda
  esperada com desvio (CST-002-A) e o dossiê do lote em PDF (RPT-001-A).

**A ressalva, que não deve ser lida como formalidade.** Tanto a 18 quanto a 19 declaram `Sprint 17
publicada` como dependência, e o `AI_EXECUTION.md` desta sprint diz, com todas as letras: *"implemente a
Sprint 19 como módulo opcional **após o núcleo estar em produção**"*. **O núcleo não está em produção.**
A Sprint 17 encerrou sem declarar o release pronto, porque REL-001 (restauração medida) ficou fora de
escopo e o ciclo em homologação da REL-005 continua aberto.

**A premissa assumida, explícita para poder ser derrubada:** desenvolver a 19 não exige produção;
*publicá-la* exige. O trabalho pode andar, e nenhuma história desta sprint deve ir para produção antes de
o release ser validado. Se o mantenedor discordar, o custo de reverter é zero — nenhuma linha foi escrita
até aqui, e esta decisão é o único artefato.

**O que destravaria de verdade** continua sendo do mantenedor, e não desta sprint: reabrir a REL-001 e
rodar o ciclo de homologação da REL-005.

### DEC-CRM-001 (CRM-001) — Pessoa e organização são coisas diferentes, e a separação é o desenho

**O problema que a separação resolve.** O aceite pede que "cliente, consentimento e retenção sejam
auditáveis" e o plano de testes pede "anonimização". Num modelo com uma tabela só, atender um pedido de
exclusão obriga a escolher entre **apagar a pessoa** e **destruir o histórico comercial** — e não existe
resposta boa para essa escolha. Por isso são dois agregados:

- **`Customer` é a organização compradora** — dado de negócio. Bar, restaurante e distribuidor não têm
  direito ao esquecimento; pedido, nota e custo precisam continuar apontando para eles. **Não se apaga:
  desativa-se**, porque é o histórico de expedição que um recall percorre para saber a quem avisar.
- **`Contact` é a pessoa** — dado pessoal, com prazo e apagamento. **Anonimizar mantém a casca**: o
  identificador continua, e com ele continuam as expedições que apontam para cá. É a diferença entre
  "esta entrega foi para alguém que pediu para ser esquecido" e um buraco que ninguém sabe explicar.

**Consentimento é por finalidade, e finalidade tem base legal.** Esta é a regra que mais mudou o
desenho. Se todo contato exigisse consentimento, revogar a permissão de receber oferta comercial
derrubaria junto o aviso de que a entrega saiu — e a cervejaria ficaria proibida de cumprir o que
vendeu. Então `TRANSACTIONAL` se apoia em **contrato** e não é revogável; `MARKETING` e `SURVEY` se
apoiam em **consentimento** e são revogáveis em separado. O domínio **recusa** registrar consentimento
para finalidade contratual, justamente para que ninguém possa "revogá-lo" depois.

**O histórico é um livro que só cresce, e a consulta é datada.** A pergunta que a auditoria faz não é
"ela aceita?", é **"ela aceitava quando mandamos aquilo?"**. Só um registro append-only responde. Duas
consequências que viraram teste: decisão posterior não contamina a consulta do passado, e a ordem que
vale é a **do mundo** (`at`), não a da digitação — decisão tomada por telefone na segunda pode ser
registrada na quarta, depois de outra.

**Silêncio não é permissão.** Quem nunca decidiu nada não é contactável. Mas "nunca perguntamos" e "ela
recusou" continuam distinguíveis no histórico, porque levam a ações opostas quando alguém revisa a base.

**Retenção é decisão da casa**, no mesmo espírito da PRM-001 e da `CapaPolicy`: sem política, **nada
expira**. Não anonimizar por falta de decisão é reversível; anonimizar cedo demais não é — o dado não
volta, e com ele vai embora o contato que talvez fosse preciso numa convocação de recall.

**Entregue nesta fatia:** `Customer`, `Contact`, `ConsentLedger`, `ConsentEntry`, `ContactPurpose`,
`LegalBasis`, `ConsentDecision`, `RetentionPolicy` e `ContactAnonymizedException` — **26 testes
unitários**, sem banco, sem adaptador e sem tela. O que falta: migration, portas, casos de uso,
endpoints com Problem Details, teste de isolamento por cervejaria e a tela.

### DEC-CRM-002 (CRM-001) — O documento do cliente não é validado, e isso é decisão

`taxId` é texto livre, sem verificação de CNPJ ou CPF. **Cliente estrangeiro não tem CNPJ**, e recusar
cadastro por formato seria a plataforma decidindo com quem a cervejaria pode vender. Além disso o
cadastro costuma nascer antes de o documento chegar, e travar aqui empurraria o vendedor a inventar um
número — que é pior que campo vazio, porque parece preenchido.

Se um dia a emissão fiscal entrar (INT-008), a validação nasce **lá**, onde o formato realmente importa e
onde o provedor homologado já a exige. Validar aqui seria antecipar uma regra da nota para o cadastro.

### DUV-CRM-001 (CRM-001) — **RESOLVIDA em 2026-08-18 por delegação do mantenedor**

**1. Piso mínimo de retenção: não existe no código.** Se houver, é da casa ou da lei — e quem souber
cadastra. O `RetentionPolicy` já recusava prazo não positivo e não embute padrão nenhum; a decisão foi
manter assim, pelo mesmo motivo da periodicidade de inspeção: um número embutido seria o código
respondendo por uma pergunta que ele não tem como responder.

**2. "Último relacionamento" é o mais recente entre pedido, entrega e consentimento.** Cada um sozinho
erra: só pedido ignora o bar que recebe entrega de contrato antigo; só entrega ignora quem comprou e ainda
não recebeu; só consentimento marcaria como inativo quem nunca precisou reassinar nada. As datas vêm de
consultas **publicadas** por `sales` e `distribution` (ADR-0016, direção padrão) — o CRM não lê tabela dos
outros.

**As duas consultas escolhem lados opostos de propósito.** O `OrderHistoryLookup` da previsão **exclui**
pedido cancelado, porque lá a pergunta é "quanto se vendeu". O `CustomerActivityLookup` da retenção
**inclui**, porque aqui a pergunta é "houve relacionamento" — apagar o dado pessoal de quem falou com a
casa mês passado porque a venda não fechou seria confundir "não comprou" com "sumiu". O mesmo vale para
entrega: recusa e ausência contam, porque o caminhão foi até lá.

**3. Anonimização continua ato humano — e ganhou a metade que faltava.** Apagar dado pessoal é
irreversível, e uma varredura automática com um bug de data apagaria contatos de clientes ativos. Mas
exigir revisão manual sem dizer **quem** venceu faz a fila crescer até ninguém olhar. Agora existe a fila,
e **cada linha diz de onde veio a data**: "vence em março" sem dizer que a conta partiu de uma entrega de
2024 é um número que ninguém consegue conferir, e conferir é o ponto.

**Contato sem relacionamento nenhum não entra na fila.** Cadastro que nunca foi usado não é cliente
vencido: tratar a ausência de evidência como evidência de ausência anonimizaria justamente quem acabou de
ser cadastrado.

**Entregue:** `CustomerActivityLookup` (sales), `CustomerDeliveryLookup` (distribution),
`LastRelationship`, `RetentionQueueService`, um endpoint com alçada de anonimizar, 1 caminho no OpenAPI e a
fila na tela. **Sem migration** — os dados já existiam, faltava compô-los. **5 testes de domínio, 2 de
integração e 1 de store.**

**Como estava registrada quando foi aberta:**

Conforme o rito do projeto, ambiguidade que altera regra de negócio vira pergunta e não invenção:

1. **Existe prazo mínimo de retenção que a cervejaria não pode escolher abaixo?** Nota fiscal tem
   guarda legal de anos, mas ela é do *pedido*, não do *contato*. Hoje o domínio aceita qualquer prazo
   positivo. Se houver piso, ele é da casa ou da lei — e precisa vir de alguém que saiba qual.
2. **O que conta como "último relacionamento" para o relógio da retenção?** O último pedido? A última
   entrega? Uma conversa registrada? A escolha muda quem é anonimizado e quando. Deixei o domínio
   recebendo a data pronta, para que essa decisão fique fora dele até ser tomada.
3. **Anonimização é ato humano ou varredura automática?** O domínio suporta os dois — `dueFor` responde
   quem venceu, sem executar nada. Automatizar sem revisão apaga contato de cliente ativo que só ficou
   um ano sem comprar; exigir revisão manual faz a fila crescer até ninguém olhar. É decisão de operação.

### DEC-SAL-001 (SAL-001) — Produto não é lote, e preço tem linha do tempo

**Produto é identidade comercial; lote é a coisa física.** "IPA lata 473 ml" existe antes da primeira
brassa e sobrevive a todos os lotes. Se `sales_product` e `packaging_finished_lot` fossem a mesma coisa,
cada envase criaria um item de catálogo novo e a lista de preço precisaria ser refeita a cada brassa.

**A invariante do preço é uma só: em qualquer dia, no máximo um preço por produto e canal.** Duas
vigências sobrepostas fariam "quanto custa hoje?" ter duas respostas, e o sistema escolheria pela ordem
em que leu as linhas — o pedido sairia com um valor e a fatura com outro, sem ninguém conseguir dizer
qual estava certo.

Disso saiu a decisão de desenho mais importante: **o ato comum não é "inserir vigência", é "a partir de
tal dia passa a custar tanto"**. `priceFrom` fecha sozinho o preço aberto anterior, na véspera. Exigir
que o operador encerre o antigo à mão criaria uma janela sem preço, e o erro apareceria como "produto sem
preço" num dia de venda. Já sobrepor um período **já fechado** é recusado: encurtar o antigo, dividir em
dois, substituir? Adivinhar seria reescrever preço histórico por conta própria.

**A garantia é do banco, não da checagem.** `ex_sales_price_no_overlap` (`EXCLUDE USING gist` com
`daterange [ ]`) é o que impede de verdade — checagem prévia não sobrevive a duas requisições simultâneas,
e sobreposição de preço é exatamente o que duas telas abertas produzem. Há um teste que insere direto no
banco, contornando o domínio, só para provar que a barreira existe lá.

**`Money` nasceu aqui**, com moeda ISO obrigatória, porque o critério transversal da sprint exige decimal
e moeda explícita. Quatro casas: preço unitário de item barato some inteiro num arredondamento para
centavo, e o arredondamento para dinheiro de verdade é no total do pedido (SAL-002). **Não converte** —
conversão exige taxa, data e fonte, e inventar qualquer uma seria inventar dinheiro.

**Canal é tabela, e não enum** — mesma decisão da atividade de mão de obra na CST-001-A. A segmentação de
quem vende no taproom não é a de quem exporta, e um enum exigiria migration para a cervejaria poder
vender por um canal a mais.

**Preço zero é recusado.** Brinde é desconto no pedido, onde fica registrado que alguém decidiu dar; aqui,
zero é engano — e tratar os dois igual esconde o engano.

**Imposto não é calculado** (motor fiscal está fora do escopo da sprint), mas `taxIncluded` viaja junto,
senão alguém compara preço com imposto contra preço sem e conclui errado.

**Entregue:** `Money`, `Product`, `SalesChannel`, `PriceEntry`, `PriceSchedule` e exceções; `V120` com
três tabelas; portas, casos de uso, dois controllers com Problem Details; 6 caminhos e 4 schemas no
OpenAPI; tela de catálogo, canais e linha do tempo de preço. **21 testes de domínio e 10 de integração.**

### DEB-SAL-001 — **RESOLVIDO em 2026-08-18**: o custeio passou a guardar a moeda

`costing` guardava `BigDecimal` puro em custo total, custo por litro e taxa da hora. Enquanto a cervejaria
opera numa moeda só nada quebra — mas a primeira exportação soma real com dólar sem que nada reclame, e o
erro aparece no fechamento do mês, longe da causa.

**A moeda já existia**: `brewery_operational_preferences.currency_code`, desde a Sprint 01. O que faltava
era ela sair do módulo — `brewery` passou a publicar `BreweryCurrencyLookup`, e a `V136` faz backfill a
partir dessa preferência. Nada foi inventado: a migration materializa a suposição que o custeio já fazia
em silêncio.

**A moeda é do custo, e não da linha.** Quem produz uma linha é o contribuinte — estoque, envase,
utilidades, mão de obra — e nenhum conhece dinheiro: eles reportam "consumi 20 kg a 4,50". Exigir moeda na
linha faria a produção precisar saber de moeda para registrar que trabalhou, que é o que a `V117` recusou
ao separar apontamento de hora da taxa da hora.

**O alcance foi maior que o módulo**, porque `CostSummary` é porta publicada: a mudança chegou ao
relatório PDF, à IA e à otimização. No PDF isso tinha consequência real — um documento impresso circula
fora do sistema, e "1.240,00" sem moeda é o número que alguém soma com outro de outra casa.

**Um erro meu no caminho, corrigido:** eu fiz o custeio recusar quando a cervejaria não tem moeda
configurada. Mas a linha de preferências nasce **preguiçosamente** — só quando alguém abre aquela tela —,
e o resultado foi 409 ao perguntar quanto custou a brassa em toda casa que nunca a abriu. A porta passou a
espelhar a decisão que o módulo dono já tomava: devolve o padrão da plataforma, **lido sem gravar linha
nenhuma** (o caso de uso das preferências grava ao responder, e uma consulta de custo não pode ter esse
efeito colateral).

### DUV-SAL-001 — RESOLVIDA em 2026-08-15: o que torna um lote "vendável"

**Decisão do mantenedor: vendável é liberado pela qualidade, dentro da validade e não bloqueado por
quarentena.** Implementado em SAL-001-B (ver DEC-SAL-002).

### DEC-SAL-002 (SAL-001-B) — Liberação é ato assinado, e mora no lote

**Liberação não é dedução.** A alternativa considerada era derivar "liberado" de "não há não conformidade
nem desvio em aberto", sem tabela nova. Foi recusada por dois motivos: um lote **nunca medido** passaria
como liberado — `BatchQualityLookup.unmeasured()` é verdadeiro e nada reclama — e a auditoria que pergunta
"quem liberou este lote?" receberia "o sistema deduziu". Em alimento, liberação é decisão registrada.

**A tabela mora em `packaging`, e a alçada em `quality`.** A liberação é estado do lote; se o registro
morasse em `quality`, a expedição — que já vive em `packaging` — precisaria consultá-lo para recusar lote
não liberado, e isso criaria `packaging → quality` sobre um `quality → packaging` recém-criado: ciclo,
como em CLN-004-A e FDS-004-A. **Desta vez a decisão veio antes do `ModularityTest`, e não depois dele
reclamar** — é o ADR-0016 fazendo o trabalho que ele foi escrito para fazer. A permissão
`quality.lot.release` é crítica e nasce no domínio da qualidade, porque quem decide é a qualidade mesmo
que o dado seja do lote.

**Um quarto impedimento apareceu, e não estava na pergunta: `shelf_life_unknown`.** Lote sem evidência de
oxigênio nem validade registrada não tem validade nenhuma, e **validade desconhecida não é validade em
dia** — vender seria prometer um prazo que ninguém apurou. Bloqueia.

**As três condições são compostas em `packaging.SellableLotLookup`, e não em quem pergunta.** Lote,
validade (FSL-001) e quarentena já estão todos ao alcance de `packaging`, que já depende de
`traceability`. Se `sales` compusesse, precisaria de três dependências para responder uma pergunta só, e
cada critério novo viraria uma dependência nova lá.

**O impedimento vem nomeado, e não como booleano.** Falta de assinatura, validade vencida e quarentena
levam a três ações diferentes; "não disponível" faria o operador ligar para a qualidade perguntar o
motivo. A ordem também é decisão: quarentena primeiro, porque um lote em quarentena não é caso de correr
atrás de assinatura; depois a liberação, que é ação de alguém; por último a validade, que é fato
consumado.

**Não há revogação de liberação.** Lote liberado que depois se mostra problemático é caso de quarentena
ou recall, que alcançam por herança e deixam rastro do porquê. Apagar a liberação faria sumir o fato de
que alguém a assinou — que é o que a investigação precisa saber.

**Entregue:** `V121`, `LotRelease`, `SellableLotLookup`, `SellableLotService`, endpoints de liberação, de
estado de venda e de lotes vendáveis do produto; 3 caminhos e 3 schemas no OpenAPI; a tela mostra os
lotes disponíveis. **4 testes de integração novos**, e `ModularityTest` verde com a aresta
`sales → packaging`.

### DEC-SAL-003 (SAL-002) — As duas garantias do pedido não estão no código

**"Concorrência não vende estoque duas vezes" é critério transversal da sprint, e não dava para cumprir
com checagem.** Ler o disponível e depois gravar deixa uma janela entre as duas operações, e é ela que
duas telas abertas encontram. O Postgres também não expressa "a soma das reservas deste lote cabe no
lote" de forma declarativa — não há assertion entre linhas.

A saída foi **uma linha por lote** (`sales_lot_availability`) com total e reservado, e a reserva virou um
`UPDATE` condicional: `SET reserved_units = reserved_units + :n WHERE reserved_units + :n <=
total_units`. Duas requisições simultâneas disputam a **mesma linha**: a segunda espera o commit da
primeira e relê o valor já atualizado. Se não couber, o `UPDATE` afeta zero linhas — e é assim que quem
perdeu a corrida descobre, sem lock explícito e sem retry. O `CHECK` é a rede embaixo.

**Idempotência por índice único parcial** em `(cervejaria, chave)`. Um duplo clique ou um retry de rede
não pode criar um segundo pedido: ele tiraria do próximo comprador uma cerveja que ninguém vai levar.
Nulo é legítimo — pedido digitado na tela não tem por que ter chave — e é por isso que o índice é
parcial. **No cliente a chave é gerada por tentativa de envio, e não por pedido**: fixa por formulário,
o operador que corrige a quantidade e reenvia receberia o pedido antigo de volta e concluiria que o
sistema ignorou a correção.

**A promessa é validada contra a validade do lote reservado, e manda o que vence primeiro** — quem
entrega tudo junto entrega o mais velho junto. A validade é o **último dia bom**, então prometer
exatamente para ela vale. Sem essa regra o pedido é aceito, o cliente organiza a operação dele em cima
da data, e o problema aparece no dia da carga.

**A reserva é FEFO, e não FIFO.** Sai antes o que *vence* antes, não o que foi produzido antes: são a
mesma coisa quase sempre, e diferentes justamente quando importa — um lote novo com validade curta
precisa sair na frente de um velho com validade longa, ou vence na prateleira.

**O preço é congelado no pedido.** A lista muda (é para isso que ela tem vigência), e se a fatura
relesse a lista, um aumento aplicado depois mudaria o valor de um pedido que o cliente já aceitou.

**A reserva aponta para o lote, e não só para o produto.** É o que faz o pedido manter rastreio: quando
um recall alcança um lote, "quem comprou disto?" precisa ter resposta. Reservar "10 unidades de IPA
lata" obrigaria a avisar todo mundo que comprou IPA.

**O arredondamento é do total, e não da linha.** Duas linhas de R$ 0,0050 dão R$ 0,02 arredondando por
linha e R$ 0,01 somando antes — é o centavo que o cliente encontra ao conferir a nota. `Money` guarda
quatro casas justamente para o arredondamento acontecer uma vez só.

**Não existe rascunho.** Um pedido que não reservou nada não segura lote, e chamá-lo de pedido faria a
cervejaria contar como vendido o que outro cliente ainda pode levar. E não existe DELETE: cancelar
devolve as reservas **na mesma transação** — separadas, haveria um instante com o pedido cancelado e o
estoque preso, e uma falha no meio o deixaria preso para sempre.

**Buraco encontrado e fechado durante a implementação:** a lista de lotes vendáveis não descontava o
reservado, então a tela ofereceria 780 unidades de um lote com 780 já vendidas. Agora `freeUnits` viaja
junto e lote zerado sai da oferta. O desconto é feito em `sales`, e não em `packaging` — pedir ao envase
que conhecesse reservas criaria `packaging → sales` sobre o `sales → packaging` que já existe.

**Entregue:** `V122` com quatro tabelas, `SalesOrder`, `OrderLine`, `LotReservation`, `OrderHandlers`,
dois repositórios, controller com Problem Details para os cinco modos de recusa; 4 caminhos e 3 schemas
no OpenAPI; tela de pedidos com os lotes reservados visíveis. **15 testes de domínio e 8 de integração.**

### DEC-SAL-004 (SAL-003) — O portal isola por endereço, e não por condicional

**O problema.** `SecurityPrincipal` carrega cervejaria e permissões, e todo endpoint de vendas filtra só
por cervejaria. Um usuário externo com `sales.order.read` veria os pedidos de **todos os clientes da
casa** — e o `TenantIsolationTest`, que varre o SQL exigindo `brewery_id`, não cobre esse segundo eixo
porque ele nem sabe que existe.

**Decisão do mantenedor (2026-08-15): endpoints próprios em `/api/v1/portal/**`**, que sempre filtram
pelo cliente do principal e nunca reaproveitam os handlers internos. A alternativa considerada era
acrescentar `customerId` ao principal e filtrar em cada consulta; foi recusada porque a proteção passaria
a depender de **cada endpoint novo lembrar do filtro** — exatamente o padrão que a OBS-REL-001 encontrou
em dez escritas.

O isolamento vira **estrutural**: o cliente vem do vínculo lido pelo identificador do usuário
autenticado, e **nunca do caminho nem do corpo**. Se viesse de fora, bastaria trocá-lo. E um endpoint
interno novo não pode vazar por aqui, porque o portal não passa por ele.

**Usuário de portal é `security_user` comum**, não identidade paralela. Duplicar autenticação
significaria duplicar MFA, bloqueio por tentativa, expiração e recuperação de senha — e a segunda cópia é
a que fica para trás quando algo é corrigido. `portal.access` é a **única** permissão que ele recebe, e
ela não abre nada interno; ela também **não entra no grupo de administração**, porque concedê-la a todos
faria o portal deixar de ser do cliente.

**Permissão sem vínculo não abre o portal.** A permissão diz que ele pode entrar; o vínculo diz de quem
ele é. Sem o segundo não há a quem mostrar nada.

**Pedido de outro cliente responde 404, e não 403** — distinguir contaria que o identificador existe em
algum lugar, que é um oráculo entre clientes.

**O portal não mostra os lotes reservados.** Eles são rastro interno da cervejaria: o cliente precisa
saber o que comprou e para quando, não de qual brassa saiu.

**Item sem preço ou sem disponibilidade não aparece no catálogo.** Mostrá-lo faria o cliente pedir e ser
recusado depois — e no portal não há um vendedor por perto para explicar.

**A recompra repete a intenção, e não o valor.** Reaproveitar o preço antigo faria o cliente comprar por
um valor que não vale mais, e a cervejaria vender abaixo da lista sem ninguém ter decidido isso. Preço,
disponibilidade e teto são os de hoje.

**O teto é conferido antes de reservar.** Recusar depois deixaria o estoque preso até alguém perceber, e
o cliente veria "sem crédito" num lote que ele mesmo travou.

**Entregue:** `V123` com duas tabelas, `CreditLimit`, `PortalController` (catálogo, pedidos, recompra,
crédito), `PortalAdminController` (conceder acesso, definir teto); 7 caminhos e 3 schemas no OpenAPI;
tela do portal. **6 testes de domínio e 8 de integração.**

### DEB-SAL-002 — **RESOLVIDO em 2026-08-18**

**O que era.** O comprometido somava pedidos confirmados. Os dois erros apareciam no mesmo cliente: quem
pagava continuava com o limite ocupado, e **um pedido entregue e não pago saía da conta**. A limitação
estava declarada no domínio, na migration, no contrato e num teste que existia só para documentá-la.
Faltava a baixa de pagamento.

**O recebimento é evento, e não saldo.** Um campo "valor pago" no pedido responderia "quanto falta" e
perderia "quem pagou o quê, e quando" — que é a pergunta de qualquer conferência com o financeiro. E um
saldo que se sobrescreve não se audita.

**O parcial conta na proporção do que entrou.** Metade na entrega e metade em trinta dias é como boa parte
do comércio funciona. Ignorar o parcial faria um cliente que pagou 90% ocupar o limite inteiro — e o
vendedor recusaria a venda de alguém que está em dia. Exigir o valor cheio faria o operador lançar o que
não recebeu para o sistema parar de reclamar.

**Estorno é evento compensatório, e não edição.** Recebimento lançado errado não se apaga: registra-se o
estorno, e os dois ficam. Mesmo princípio da prova de entrega da LOG-002, e pela mesma razão — o registro
que se reescreve parece original e diz outra coisa. O estorno vai pelo **valor cheio** (estornar parte
seria corrigir o valor, e correção se faz estornando inteiro e lançando de novo) e **exige motivo**: sem
ele, quem confere seis meses depois não sabe se foi engano de digitação, cheque devolvido ou pedido
cancelado, e as três levam a conversas diferentes.

**Um estorno por recebimento, garantido pelo índice único parcial** (`ux_payment_reversal`). Estornar duas
vezes o mesmo lançamento tiraria da conta um dinheiro que só entrou uma vez, e o cliente ganharia limite
que não tem. É a décima vez nesta base que a invariante que atravessa linhas mora no banco.

**Não se recebe mais do que o pedido deve.** É o que pega o zero a mais: 1.200,00 num pedido de 120,00 é
digitação, e não pagamento. A recusa devolve o saldo de verdade, porque é o número que resolve. E o
`GREATEST(devido − recebido, 0)` é **por pedido**: sem ele, pagar a mais num pedido geraria crédito nos
outros.

**A conciliação é manual nesta fatia.** O meio de pagamento é obrigatório porque sem ele a conferência com
o extrato vira adivinhação — "R$ 1.200 no dia 12" existe três vezes num extrato movimentado. Integração
bancária inventaria contrato de terceiro, que é a mesma razão que suspendeu a Sprint 21 (DEC-INT-002).

**Entregue:** `V140__sales_payment.sql` (tabela append-only, `CHECK` de valor positivo, `CHECK` de "estorno
exige motivo", índice único do estorno, duas permissões — `sales.payment.reverse` é **crítica**, porque
tira dinheiro da conta e devolve limite ao cliente); `Payment`, `PaymentHandlers`, `PaymentController`
(lançar, estornar, listar com saldo); `committedAmount` reescrito para medir recebível (`PLACED` +
`FULFILLED` menos os recebimentos); 2 caminhos e 2 schemas no OpenAPI; os recebimentos dentro do pedido na
tela de vendas. **8 testes de domínio, 8 de integração e 4 de store.**

**A `V123` continua com o comentário antigo, e é de propósito:** mudar texto de migration já aplicada
muda o checksum e quebra o `validate` de quem já subiu. A correção mora na `V140`, no domínio, no contrato
e neste STATUS.

**O que continua fora:** o excedente pago não vira crédito do cliente — crédito é conta corrente de
cliente, e não baixa de pedido. Quem paga a mais tem o lançamento recusado, e não um saldo escondido.

### DEB-SAL-003 — **RESOLVIDO em 2026-08-18**

**O que foi feito.** A máquina que constrói um lote de cerveja saiu para `support.BrewScenario`: login,
equipamento, ingrediente, ordem liberada, brassa iniciada, transferência, embalagem recebida, linha limpa,
plano com checklist e reserva, execução, frescor e liberação. Quem precisa de um lote acabado agora escreve
uma linha.

**Ela é construída por API, e não por SQL.** Inserir as linhas direto no banco seria mais curto e produziria
um lote que nenhum caminho do sistema consegue produzir — a fixture pararia de quebrar quando uma regra
mudasse, que é exatamente quando ela precisa quebrar.

**E ela tem teste próprio** (`BrewScenarioIT`). Uma fixture que constrói errado faz dezenas de testes
mentirem juntos, e todos passam: este é o único lugar onde o que ela produz é verificado como resultado, e
não usado como pressuposto.

**O `PackagingRunIT` passou a delegar**, e não a duplicar: os construtores viraram chamadas de uma linha à
fixture, e o arquivo caiu de **1.219 para 1.087 linhas**. Isso importa mais que o tamanho — duas cópias do
mesmo cenário divergem na primeira regra nova, e a segunda a mudar não avisa a primeira. Agora o cenário
tem **um dono**.

**O primeiro uso externo já pagou:** o `DEB-CON-001` foi fechado com ela, e o dublê de lote acabado deixou
de existir.

**A repartição veio depois, em passo próprio** — misturá-la com a extração faria um diff que não se
revisa. O arquivo caiu de **1.087 para 436 linhas**, e cada assunto passou a morar onde alguém vai
procurá-lo: `LotReleaseIT` (SAL-001-B), `SalesOrderIT` (SAL-002), `CustomerPortalIT` (SAL-003),
`CommercialOutboxIT` (INT-008).

**A base compartilhada evitou trocar um problema por outro.** Repartir sem extrair o cenário comercial
teria virado quatro cópias do mesmo cenário — e cópias divergem na primeira regra nova, que é exatamente a
dívida que a `DEB-CON-002` custou a fechar. `CommercialTestSupport` guarda o que os quatro compartilham;
`BrewScenario` ganhou a cena comercial (produto, canal, preço, cliente).

**Um teste pegou uma regressão da própria repartição.** A reescrita mecânica engoliu um parâmetro: o teste
"sem preço no canal o pedido é recusado" passava *outro* canal, e a substituição fez ele usar o canal da
cena — que tem preço. O pedido passou, e o teste falhou dizendo isso. É o argumento a favor de repartir com
a suíte inteira verde do lado.

**Como estava registrado quando foi aberto:**

Ele tem **1.100 linhas** porque é onde mora a máquina que constrói um lote acabado de verdade — plano,
checklist, limpeza, reserva, execução —, e as histórias de venda precisam dela: liberação, lote vendável,
pedido, portal. Duplicar isso em cada IT novo custaria mais que o tamanho do arquivo.

**Critério de remoção:** extrair o cenário para um *fixture* compartilhado de teste, e repartir o arquivo
por assunto. Não foi feito no meio da história de propósito — refatorar a base de teste enquanto se
escreve teste novo é a hora errada.

### DEC-FCST-001 (FCST-001) — A previsão diz o que não sabe, e às vezes se recusa a responder

**O aceite pede quatro coisas juntas — dados, versão, erro e confiança — porque o número sozinho mente.**
"Vamos vender 400 latas em março" parece um fato e é um resumo: pode vir de doze meses estáveis ou de
dois que por acaso deram parecido. As duas produzem a mesma média e significam coisas opostas para quem
vai decidir uma brassa.

**Abaixo de três meses não há previsão.** `INSUFFICIENT` não é um número baixo, é a **ausência dele** — e
devolver um número aqui seria o pior resultado possível, porque ele viraria plano de produção e a cerveja
que não vender vence na prateleira. Mesmo espírito do `Confidence.INSUFFICIENT` do gêmeo digital, com um
agravante que a estimativa de brassagem não tem: **demanda tem sazonalidade**, e três meses de verão não
dizem nada sobre o inverno.

**O erro vem de backtest, e não de fórmula.** Os últimos três meses ficam fora do treino, são previstos e
comparados com o que aconteceu — é a única forma de dizer o quanto o método erra *neste* produto. Sem
histórico para separar treino e teste, o erro vem **vazio**, e vazio é honesto: um zero diria que o método
acerta sempre. Mês sem venda é pulado no cálculo do erro, porque percentual sobre zero é infinito e
inventar um teto faria o erro parecer melhor ou pior conforme o teto escolhido.

**Média móvel, e não algo mais sofisticado.** Com o histórico de uma cervejaria pequena, um modelo com
sazonalidade ajusta ruído e apresenta o ajuste como conhecimento. A média móvel erra de um jeito que se
enxerga no backtest; um modelo complexo erra de um jeito que parece previsão.

**Quatro regras de janela que decidem mais que o algoritmo:**

- **O mês corrente fica de fora** — está incompleto, e incluí-lo faria a previsão baixar todo dia 1º e
  subir até o dia 31 sem nada mudar na demanda.
- **Mês sem venda entra como zero**, e não é omitido — omitir encurtaria a série e faria a média descrever
  só os meses bons. É o erro mais fácil de cometer aqui e o mais difícil de perceber depois.
- **Meses iniciais sem venda nenhuma são cortados** — são "o produto ainda não existia", e não "ninguém
  quis". Sem isso, um lançamento recente pareceria fracasso.
- **Pedido cancelado não conta** — foi intenção que não virou venda. Contá-lo teria defesa (cancelamento
  por falta de estoque *é* demanda reprimida), mas a plataforma não distingue quem cancelou nem por quê, e
  tratar os dois casos como um só inventaria informação.

**Nada é persistido.** A previsão é derivada no momento da pergunta, como o custo aberto do lote: guardá-la
criaria uma segunda verdade que envelhece a cada pedido novo, e alguém decidiria em cima de um número do
mês passado sem saber. A `V124` é só a permissão.

**Previsão não cria OP nem compra**, por critério transversal da sprint — e a regra virou método no
domínio (`mayDriveProductionAlone()`, sempre falso) com teste próprio, em vez de comentário. Só existe
`GET` no controller.

**Entregue:** `DemandForecast`, `ForecastConfidence`, `ForecastMethod`, `sales.OrderHistoryLookup`
(consulta publicada, direção padrão do ADR-0016), serviço, endpoint e tela. **11 testes de domínio e 6 de
integração.**

### DUV-FCST-001 (FCST-001) — **RESOLVIDA em 2026-08-18 por delegação do mantenedor**

**A decisão: a casa declara o ciclo de ocupação por tanque, e o sistema multiplica.** O que faltava era
exatamente o tempo de ciclo — inferi-lo de lotes passados daria um número que parece cálculo e é média de
coisas diferentes, porque uma IPA e uma lager não ocupam o tanque pelo mesmo tempo.

**Sem tanque declarado, a resposta é "não sei" — e não zero.** Zero diria que a cervejaria não consegue
produzir nada, e alguém planejaria em cima disso. Mesma escolha que a previsão de demanda já fazia com
histórico curto: `INSUFFICIENT` é a ausência do número, não um número baixo.

**O lote que não termina no período não conta.** O piso na divisão é deliberado — contar pela fração
incluiria cerveja que ainda estará fermentando quando o mês virar.

**É um teto otimista, e isso muda como se lê o resultado.** Ele ignora turno, calendário, limpeza entre
lotes e gargalo fora do fermentador — maturação a frio, linha de envase, mão de obra. **Se a demanda não
cabe nele, certamente não cabe na fábrica; o contrário não vale.** Está dito no domínio, no contrato e na
tela: é o que impede o número de virar promessa.

**A política de ocupação dos fermentadores**, que a dúvida original citava como faltante, é justamente o
que a casa passou a declarar. O que continua fora: tempo de ciclo **por receita** — a declaração é por
tanque, e uma casa que varia muito de estilo verá um teto médio. Fica como limitação conhecida, e não como
promessa.

**Entregue:** `V139`, `ProductionCapacity`, porta e repositório de ciclo, `CapacityService`,
`EquipmentSummaryLookup` publicado por `equipment`, três endpoints, 3 caminhos no OpenAPI e a capacidade ao
lado da previsão. **7 testes de domínio, 5 de integração e 2 de store.**

### DUV-FCST-001 — como estava registrada quando foi aberta

O título da história é "previsão de demanda **e capacidade**". A demanda está feita; a capacidade **não**,
e de propósito.

Capacidade de produção num período exige **tempo de ciclo por receita** e **política de ocupação dos
fermentadores** — quantos dias uma IPA ocupa o tanque, quantos tanques a casa aceita deixar parados, qual
o intervalo de limpeza entre lotes. A plataforma não modela nenhum dos dois. O `EquipmentCapacityLookup`
que existe dá **litros de um tanque**, que é outra pergunta.

Inventar esses números produziria uma "capacidade" que parece cálculo e é chute — exatamente o que esta
história existe para não fazer com a demanda. Registrado em vez de inventado.

### DEC-INT-001b (INT-008) — A porta já existia, e a história foi acrescentar fatos a ela

**A descoberta que definiu o tamanho da história.** O outbox da INT-002 já nasceu com a propriedade que o
aceite da INT-008 pede. O Javadoc do `WebhookDelivery` diz, com todas as letras, que mandar o webhook
dentro do caso de uso faria *"o critério 'falha não bloqueia domínio' ser exatamente o oposto do que
acontece"* — e é por isso que existe uma linha no outbox em vez de um POST direto.

Então a INT-008 **não construiu integração nova**: acrescentou quatro eventos à allowlist fechada
(`sales_order.placed`, `sales_order.cancelled`, `sales_order.fulfilled`, `finished_lot.released`) e os
publicou no mesmo commit do fato. Fiscal, contábil, POS e e-commerce consomem o que já entrega com retry,
backoff e limite de cinco tentativas.

**`BEFORE_COMMIT`, como o resto.** Se a transação do pedido reverter, a entrega reverte junto — um
webhook "pedido confirmado" não sai para um pedido que não existe, e um webhook não se desmanda.

**O payload não leva dado pessoal, e há teste para isso.** `customerId` é a organização compradora, que é
dado de negócio; contato, e-mail e telefone ficam de fora. Mandá-los furaria a regra que a CRM-001 existe
para sustentar: consentimento é por finalidade, e "integrar com o POS" não é finalidade que alguém
consentiu. Quem precisar do contato pede pela API, com alçada.

**Campo nulo vai como nulo, e não some do corpo.** `promisedFor` sem data e `bestBefore` sem validade
apurada viajam nulos: um campo ausente faria quem integra achar que a versão do payload mudou.

**A plataforma não calcula imposto** — motor fiscal está fora do escopo da sprint. Ela avisa que o fato
aconteceu, com total e moeda; quem emite nota emite nota.

**Achado durante a implementação: `Money` guardava quatro casas e o total saía como `120.0000`.** As
quatro casas existem para o arredondamento acontecer uma vez só, no total (SAL-002) — mas o total de um
pedido é dinheiro de nota, e são duas. Nasceu `Money.toMinorUnit()`, com a distinção entre
**armazenamento e apresentação** escrita no código. Sem isso, quem integra teria que adivinhar se
`120.0000` era precisão ou descuido.

**Entregue:** quatro eventos publicados (`SalesOrderPlaced`, `SalesOrderCancelled`, `SalesOrderFulfilled`,
`FinishedLotReleased`), duas portas de publicação com adaptadores Spring, quatro tipos na allowlist e os
listeners do outbox. **4 testes de integração**, incluindo *o pedido sobrevive à falha da entrega* e *o
corpo não leva dado pessoal*. Sem migration: o outbox e as assinaturas já existem.

## Evidências de encerramento

- **Build/commit:** sete PRs, um por história, mergeados em série na `main` — #228/#229 (CRM-001), #230
  (SAL-001), #231 (SAL-001-B), #232 (SAL-002), #233 (SAL-003), #234 (FCST-001), #235 (INT-008).
- **Testes executados:** `mvnw clean verify` verde na árvore final — **1.342 unitários e 855 de
  integração** contra PostgreSQL real via Testcontainers, zero falhas. Frontend: **532 testes em 87
  arquivos**, build e lint limpos. `ModularityTest` verde nas quatro arestas novas (`sales → packaging`,
  `forecast → sales`, `integration → sales`, `integration → packaging`) e `TenantIsolationTest` verde.
  A sprint começou com 829 testes de integração e terminou com 855.
- **Migration aplicada:** `V119` a `V124`. Nenhuma destrutiva. As duas de maior consequência não criam
  tabela: a restrição de exclusão `ex_sales_price_no_overlap` (preço) e o contador
  `sales_lot_availability` (reserva) são **as garantias que o código não consegue dar**.
- **Contratos atualizados:** `contracts/openapi.yaml` — **284 caminhos**, 28 a mais que no encerramento da
  Sprint 17, sem `$ref` órfã nem chave duplicada.
- **Riscos remanescentes** (retrato do encerramento; o que fechou depois está anotado ao lado):
  - **A premissa de produção.** Toda a sprint pressupõe que alguém vai validar o release. Enquanto
    REL-001 e o ciclo da REL-005 seguirem abertos, isto é software que funciona e não opera. **Continua
    aberto — é o único desta lista.**
  - **`DEB-SAL-001`** — o custeio guarda `BigDecimal` sem moeda. Não quebra enquanto a cervejaria opera
    numa moeda só. **Resolvido em 2026-08-18** (`V136`, e o `Money` em `shared`).
  - **`DEB-SAL-003`** — o `PackagingRunIT` chegou a ~1.200 linhas por ser a casa do cenário de lote
    acabado, que quatro histórias precisaram. **Resolvido em 2026-08-18** (`BrewScenario`, e o arquivo
    repartido em cinco).
  - **`DUV-CRM-001`** e **`DUV-FCST-001`** — duas perguntas do mantenedor que travam trabalho concreto: a
    varredura de retenção e a metade "capacidade" da previsão. **Ambas resolvidas em 2026-08-18 por
    delegação do mantenedor.**
- **Aceite:** pendente de validação manual. Junto com os aceites das Sprints 09, 16 e 17.

### O que esta sprint ensinou, e que vale carregar

**A invariante que atravessa linhas mora no banco.** Aconteceu três vezes, sempre com o mesmo formato:
sobreposição de preço (`EXCLUDE USING gist`), liberação única do lote (chave primária) e reserva de
estoque (`UPDATE` condicional numa linha por lote). Em todos, o código continua checando — mas **para dar
mensagem boa, não para garantir**. Checagem prévia não sobrevive a duas requisições simultâneas, que é
exatamente o que duas telas abertas produzem.

**Recusar é um resultado, e às vezes o único honesto.** A previsão sem histórico devolve a ausência de
número; o lote sem validade apurada não é vendável; o produto sem preço não aparece no portal. Nos três,
a alternativa barata seria devolver zero — e zero teria virado plano de produção, venda de cerveja vencida
e pedido recusado depois de aceito.

**Decidir a direção da dependência antes do teste reclamar.** Nas sprints anteriores, dois ciclos entre
módulos foram descobertos pelo `ModularityTest`. Aqui, a liberação do lote foi colocada em `packaging` —
e não em `quality`, onde a alçada está — justamente porque a expedição precisaria consultá-la e fecharia
ciclo. É o ADR-0016 fazendo o trabalho para o qual foi escrito.

**Dado pessoal é uma fronteira, e ela atravessa módulos.** A CRM-001 separou pessoa de organização; a
INT-008 manteve a separação ao escolher o que sai no webhook. Uma decisão de modelagem só vale se a
próxima história a respeitar.
