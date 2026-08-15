# Status — Sprint 19

Estado: **ATIVA desde 2026-08-15** — escolhida como próxima sprint. Nenhuma história iniciada.

| História | Estado | Evidência |
|---|---|---|
| CRM-001 | Em execução — domínio e testes prontos | `crm/domain/*`, 26 testes unitários |
| SAL-001 | Em execução — domínio, fatia de fora e tela | `sales/*`, `V120`, 21 unitários + 10 IT |
| SAL-002 | Em execução — domínio, fatia de fora e tela | `sales/*`, `V122`, 15 unitários + 8 IT |
| SAL-003 | A fazer | — |
| FCST-001 | A fazer | — |
| INT-008 | A fazer | — |

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

### DUV-CRM-001 (CRM-001) — Três perguntas que não invento, registradas em vez de decididas

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

### DEB-SAL-001 — O custeio usa dinheiro sem moeda

Descoberto ao escrever `Money`: `costing` guarda `BigDecimal` puro em custo total, custo por litro e taxa
da hora. Enquanto a cervejaria opera numa moeda só, nada quebra — mas a primeira exportação faz somar
real com dólar sem que nada reclame, e o erro aparece no fechamento do mês, longe da causa.

**Critério de remoção:** `costing` passar a persistir e expor moeda junto do valor, reaproveitando o
`Money` da SAL-001 (ou uma versão dele promovida a `shared`). **Não ampliei o escopo aqui** porque mexer
no custeio significa migration em tabela com dado, e isso é história própria.

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

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
