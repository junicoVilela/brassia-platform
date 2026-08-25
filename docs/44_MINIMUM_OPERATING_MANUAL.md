# 44 — Manual mínimo de operação

Entregável da história `REL-005`. Cobre o caminho que uma cervejaria percorre do insumo recebido à cerveja
expedida, na ordem em que o sistema aceita — e as portas que ele fecha quando a ordem não é essa.

> **O que este manual não é.** Não é referência de tela nem catálogo de campos: cada tela tem rótulo,
> validação e mensagem próprios, e duplicá-los aqui criaria uma segunda fonte que envelhece na primeira
> mudança de layout. O que está aqui é a **sequência** e o **porquê de cada porta** — que é o que não se
> descobre olhando a tela.

O núcleo descrito abaixo — do insumo recebido ao simulado de recall, seções 2.1 a 2.7 e 2.10 a 2.12 — é o
mesmo exercitado de ponta a ponta por `e2e/tests/business-journey.spec.ts`. Quando manual e teste
divergirem, o teste está certo: ele roda contra a API real a cada mudança.

As seções **2.8 (venda)** e **2.9 (retornáveis)** ganharam o mesmo contrapeso depois:
`e2e/tests/sales-journey.spec.ts` percorre do cliente ao pedido com o teto de crédito recusando e
autorizando **na tela**, e `e2e/tests/distribution-journey.spec.ts` vai do keg cheio à coleta e à
higienização, com a carga liberada por uma segunda pessoa em sessão própria. As três jornadas rodam a cada
mudança; o que cada linha do roteiro de homologação tem de prova automática está na seção 4.1.

---

## 1. Antes do primeiro lote

Esta ordem não é sugestão de organização — cada item é pré-requisito do seguinte, e pular gera recusa lá na
frente, longe da causa.

| # | Onde | O que | Por que antes |
|---|---|---|---|
| 1 | `/breweries` — **Cervejarias** | A cervejaria e seu fuso | Todo dado de negócio pendura em `brewery_id`, e o fuso decide a que dia pertence um apontamento das 23 h |
| 2 | `/security/users` — **Usuários** | Pessoas e grupos de permissão | Comando sem permissão é recusado; conceder no meio da operação obriga a refazer o passo |
| 3 | `/settings/parameters` — **Parâmetros** | Parâmetros da casa | Validade da liberação de limpeza, tolerâncias e alçadas saem daqui. O default existe, mas é o da casa que decide se um CIP de ontem ainda vale hoje |
| 4 | `/equipment` — **Equipamentos** | Panela, fermentadores, linha de envase | Receita, transferência e envase apontam para equipamento; sem ele não há onde a cerveja estar |
| 5 | `/catalog` — **Ingredientes** | Malte, lúpulo, levedura, embalagem | Com os atributos técnicos: sem potencial, cor, alfa-ácido e atenuação, a receita não calcula métrica nenhuma |
| 6 | `/suppliers` — **Fornecedores** | Quem fornece | O recebimento exige fornecedor: é o primeiro elo da rastreabilidade, e sem ele o recall não sai da fábrica |
| 7 | `/sanitation/procedures` — **POPs de limpeza** | O POP **publicado** | Ciclo de limpeza só executa procedimento publicado — rascunho não libera equipamento |

**Se a casa vai vender pelo sistema** — e não apenas produzir —, mais três, na ordem:

| # | Onde | O que | Por que antes |
|---|---|---|---|
| 8 | `/crm/customers` — **Clientes** | A organização compradora | Pedido pendura em cliente. O contato é a **pessoa**, e é ele que tem consentimento por finalidade e prazo de retenção — o cliente não se apaga, desativa-se, porque é o histórico de expedição que um recall percorre |
| 9 | `/sales/catalog` — **Catálogo** | Produto, canal e **preço vigente** | Pedido sem preço no canal é recusado: "ainda não precificado" e "de graça" são opostos, e o sistema se recusa a supor qual dos dois você quis |
| 10 | `/containers` — **Contêineres** | A frota, com etiqueta e **inspeção** | Só para quem opera keg, barril ou retornável. O vasilhame nasce **sem inspeção e não pode ser enchido** — tratar a ausência como aprovação deixaria a frota nova inteira fora de controle |

---

## 2. O ciclo, do insumo à expedição

### 2.1 Receber insumo — `/inventory` (**Estoque**)

Entra com fornecedor, lote do fornecedor, validade, custo unitário e resultado da inspeção. **É daqui que a
rastreabilidade parte:** o lote do fornecedor é o que um recall percorre de volta. Recebimento sem esse
código produz cerveja cuja origem termina na sua porta.

### 2.2 Publicar a receita — `/recipes` (**Receitas**)

Calcule as métricas e **publique**. Receita publicada é imutável: alteração gera versão nova, e é por isso
que o lote consegue dizer meses depois com qual receita foi feito. Ordem de produção só aceita receita
publicada.

### 2.3 Ordem de produção — `/brew-orders` (**Ordens de produção**)

Quatro passos, nesta ordem, e cada um recusa se o anterior não aconteceu:

1. **Criar** — receita publicada e volume.
2. **Liberar** — com **responsável nomeado**. "A equipe" não é responsável.
3. **Reservar estoque** — separa o insumo. Reservar antes de iniciar é o que impede dois lotes de contarem
   com o mesmo saco de malte.
4. **Iniciar** — **é aqui que o lote nasce**. Antes disso existe intenção; depois existe cerveja.

### 2.4 Acompanhar o lote — `/production/batches` (**Lotes de produção**)

Apontamentos de temperatura, densidade, pH e volume. A **transferência** para o fermentador registra volume
transferido, OG e perdas — e as perdas são declaradas, não deduzidas: volume que some sem alguém dizer por
onde vira cerveja sem origem.

Fermentação tem tela própria (`/fermentation/readings`, **Leituras de fermentação**); leitura de sensor, se
houver sensor cadastrado, entra pela mesma curva sem digitação.

### 2.5 Liberar a linha — `/sanitation/cycles` (**Ciclos de limpeza**)

**A linha de envase não recebe plano sem ciclo de limpeza liberado.** O ciclo é: abrir sobre o POP
publicado → registrar as medições de cada passo (concentração, temperatura, tempo) → concluir → registrar a
verificação (enxágue, visual, ATP, microbiológico) → liberar.

A liberação é **evidência, não um "ok" digitado**: cada passo carrega o que foi medido, e a validade vem do
parâmetro da casa. CIP feito semana passada não libera envase de hoje.

### 2.6 Plano de envase — `/packaging/plans` (**Planos de envase**)

Plano sobre o lote, com embalagem, linha limpa e janela. Depois: **checklist** (inspeção do vasilhame, teste
de recravação, suprimento de gás) → **reserva** → **execução** com volume de entrada, unidades produzidas e
unidades rejeitadas.

A execução **cria o lote de produto acabado** — é o objeto que sai da fábrica, e é ele que o recall procura.

### 2.7 Produto acabado e expedição — `/packaging/finished-lots` (**Produto acabado**)

A tela mostra quantas unidades ainda estão **sem destino**. Registre a expedição com destino, contato e
quantidade. O que não foi expedido está na fábrica e não é objeto de recall — a distinção é o que faz o
exercício de recall medir alguma coisa.

### 2.8 Vender — `/sales/orders` (**Pedidos**)

A expedição de 2.7 é a saída física. O **pedido** é o compromisso comercial, e ele reserva lote: é por isso
que um pedido consegue dizer, meses depois, qual cerveja foi prometida a quem — e é isso que um recall
percorre para saber a quem avisar.

O pedido recusa por motivos que **são regra, e não defeito**: produto sem preço no canal, lote sem saldo
livre, entrega prometida para depois da validade do lote reservado, e **limite de crédito**.

O teto de crédito vale nas **duas portas**, e elas se comportam de modo diferente de propósito. No portal do
cliente (`/portal`) a recusa é definitiva — não há vendedor por perto para explicar. Na porta interna, quem
tem `sales.order.credit_override` pode autorizar com **justificativa**, que fica registrada com nome e data
e aparece no pedido. Não é recusa dura porque a alternativa real é pior: o vendedor cadastraria um teto
maior "só por hoje" e esqueceria de voltar, e aí o limite deixaria de existir para sempre em vez de por um
pedido.

O **recebimento** entra no próprio pedido, e não numa tela de financeiro à parte: é o pagamento que libera o
limite de crédito de volta. Estorno é evento compensatório com motivo, e não edição — os dois lançamentos
ficam.

### 2.9 Retornáveis — `/containers` e `/distribution/loads`

**Só para quem opera keg, barril ou vasilhame retornável.** Quem envasa apenas em lata ou garrafa não
retornável pula esta seção inteira; ela não é pré-requisito de nada acima.

1. **Encher** — o vínculo com o lote é **evento, e não campo**: esvaziar fecha o período e não apaga. Um keg
   vive anos e passa por dezenas de lotes, e é assim que a genealogia sobrevive a isso. Encher exige as três
   coisas juntas: condição boa, estado vazio e inspeção válida.
2. **Montar a carga** (`/distribution/loads`) — vasilhames, paradas, sequência e janela.
3. **Liberar a carga** — **por outra pessoa**. `PLANNED` e `RELEASED` são estados diferentes porque entre um
   e outro há alguém que não é quem montou: a conferência existe para encontrar o erro de quem montou, e
   quem montou relê enxergando o que quis colocar. Reabrir uma carga liberada **derruba a conferência**.
4. **Entregar e coletar** — são fatos **separados**: o motorista recolhe vazios onde não deixou nada, e às
   vezes deixa sem recolher. A prova de entrega é append-only: corrige-se por evento compensatório que
   aponta para o original, porque uma prova reescrita *parece* original e diz outra coisa.
5. **Receber de volta** — o que voltou está **sujo até que alguém diga o contrário**. `RETURNED` não é
   `EMPTY`: derivar disponibilidade da chegada encheria com cerveja um vasilhame que ninguém lavou.

Assinatura e foto **não existem sem consentimento**, com finalidade escrita — e recusar assinar não trava a
entrega. A coordenada é gravada com três casas (~100 m) porque a operação precisa saber se a entrega foi no
lugar certo, e não montar o rastro diário de uma pessoa.

Quem opera na rua usa `/scan`, que funciona **offline** e sincroniza com idempotência por aparelho. Conflito
— o escritório já registrou aquela parada — **não se resolve sozinho**: fica numa fila que alguém olha, com
o motivo. Último-a-escrever-ganha descartaria em silêncio o registro de quem estava lá.

**Ler um código identifica, e não autoriza.** Um QR fotografado no bar não é credencial: quem escaneou
continua precisando de alçada para mover, encher ou dar baixa.

### 2.10 Provar a cadeia — `/traceability/genealogy` (**Genealogia**)

Do insumo à expedição, nos dois sentidos. É a tela que responde "de onde veio" e "para onde foi" sem que
ninguém precise montar planilha.

O contêiner é **nó da genealogia**, e não atributo do lote: ele atravessa lotes, e pendurá-lo no lote
responderia "onde este lote foi parar" sem nunca responder "o que este keg já teve dentro" — que é o
caminho de um recall que começa numa reclamação de bar.

### 2.11 Exercitar o recall — `/traceability/recall-drills` (**Simulados de recall**)

Abra o simulado sobre o lote, registre quantas unidades foram localizadas, o resumo e as ações corretivas.
O relatório devolve **percentual localizado** e o que fazer para melhorar.

**Simulado não recolhe nada** e não abre recall de verdade. É exercício — e é o único jeito de descobrir que
a rastreabilidade tem buraco antes do dia em que ela precisa funcionar.

### 2.12 Fechar a conta — **Custo do lote**, **Planejado × real**, **Relatório do lote**

`/costing/batches`, `/costing/variance` e `/reporting/batches`. O custo só fecha depois do envase, porque é
o envase que diz quantos litros viraram produto.

---

## 3. Quando o sistema recusa

A maioria das recusas **não é erro**: é uma regra que existe para impedir um estrago silencioso. As mais
frequentes no primeiro ciclo:

| A recusa | O que ela está dizendo | O caminho |
|---|---|---|
| Ordem não aceita a receita | A receita não está publicada | Publique — e note que publicar congela a versão |
| Não dá para iniciar a ordem | Falta liberar ou reservar estoque | Volte um passo; a ordem é a garantia de que o insumo existe |
| Plano de envase recusa a linha | A linha não tem limpeza liberada, ou a liberação venceu | Rode o ciclo de limpeza; a validade é parâmetro da casa |
| Execução de envase recusada | Falta item do checklist ou a reserva | Checklist inteiro antes da reserva |
| 403 em uma tela que existe | Permissão, não bug | `/security/users`; a permissão é do **tipo** de operação, não genérica |
| Conflito ao salvar | Alguém alterou o mesmo registro antes | Recarregue e refaça: a versão otimista está protegendo a edição da outra pessoa |
| Pedido recusado por preço | O produto não tem preço vigente naquele canal | `/sales/catalog`; "ainda não precificado" não é "de graça" |
| Pedido acima do limite de crédito | O cliente já deve mais do que a casa decidiu carregar | Receba o que está em aberto, ou autorize com justificativa se tiver a alçada — a recusa traz teto, dívida e valor deste pedido |
| Contêiner não pode ser enchido | Falta condição boa, estado vazio **ou** inspeção válida | A recusa diz qual das três; um keg que voltou está sujo até alguém higienizar e liberar |
| Carga não libera | Quem está liberando é quem montou | Outra pessoa confere. A regra é do agregado, da alçada **e** do banco — não há caminho por fora |
| Carga recusa um lote | O lote não foi liberado pela qualidade, ou entrou em quarentena | A saída da casa é quem cobra a assinatura; encher precede liberar, expedir não |
| Sincronização volta `DUPLICATE` | O aparelho já tinha enviado aquilo | Não é erro: é a resposta que permite fechar o item na tela |
| Sincronização volta em conflito | O escritório já registrou aquela parada | Alguém decide na fila de conflitos; o registro de quem estava na rua não é descartado sozinho |

Todo erro traz `traceId`. Ele é o que localiza a operação no log — leve-o junto ao relatar problema.

---

## 4. Roteiro de homologação

Executar o ciclo inteiro em homologação, com evidência de cada etapa. O roteiro é o mesmo da seção 2; o que
muda é que **cada linha exige evidência anexada** — sem ela, "funcionou" é memória, não registro.

- [ ] **Preparo** — cervejaria, usuários e permissões, parâmetros da casa, equipamentos, ingredientes com
      atributos técnicos, fornecedores, POP de limpeza publicado
- [ ] **Recebimento** — insumo com lote do fornecedor, validade e inspeção
- [ ] **Receita publicada** — com métricas calculadas
- [ ] **Ordem** — criada, liberada com responsável, estoque reservado, iniciada; **lote gerado**
- [ ] **Produção** — apontamentos e transferência com OG e perdas
- [ ] **Limpeza** — ciclo executado com medições e liberado
- [ ] **Envase** — plano, checklist completo, reserva, execução; **lote de produto acabado gerado**
- [ ] **Expedição** — com destino e quantidade; unidades sem destino conferem
- [ ] **Pedido** — cliente, canal e preço vigente; pedido registrado com lote reservado
- [ ] **Crédito** — um pedido acima do teto é **recusado no portal** e **autorizado com justificativa** na
      porta interna; a justificativa aparece no pedido e na auditoria
- [ ] **Recebimento** — pagamento registrado, e o limite de crédito volta a caber
- [ ] **Genealogia** — cadeia visível do insumo à expedição
- [ ] **Simulado de recall** — percentual localizado e ações corretivas registrados
- [ ] **Custo e relatório do lote** — fecham com o que foi produzido
- [ ] **Autorização** — uma operação tentada sem permissão retorna 403 com Problem Details
- [ ] **Isolamento** — um usuário de outra cervejaria não enxerga este lote
- [ ] **Bloqueadores** — nenhum aberto; o que sobrou tem identificador e critério de remoção

**Só se a casa opera retornável** — pule inteiro se não houver keg, barril ou vasilhame que volta:

- [ ] **Frota** — contêiner com etiqueta e inspeção válida; um vasilhame sem inspeção é recusado no
      enchimento
- [ ] **Enchimento** — vínculo ao lote registrado, com início do período
- [ ] **Carga** — montada por uma pessoa e **liberada por outra**; a tentativa de liberar a própria carga é
      recusada
- [ ] **Entrega** — prova registrada; uma correção aponta para a original e diz o que estava errado
- [ ] **Consentimento** — assinatura ou foto **recusada** não trava a entrega; a mídia não existe sem
      finalidade escrita
- [ ] **Coleta** — vasilhame recolhido volta como `RETURNED`, e só fica disponível depois de higienizado e
      liberado
- [ ] **Offline** — operação enviada duas vezes pelo aparelho volta `DUPLICATE` sem gravar de novo; parada
      já registrada pelo escritório vira **conflito na fila**, e não sobrescrita
- [ ] **Recall com contêiner** — o simulado localiza os **contêineres** do lote afetado, e não só as
      unidades expedidas

Evidência mínima por linha: identificador do objeto criado (lote, plano, simulado) e captura da tela que o
mostra. Para 403 e isolamento, o corpo da resposta — é ele que prova qual regra recusou.

## 4.1 O que já tem prova automática, e o que não tem

O roteiro acima nunca foi percorrido por gente. Mas boa parte das linhas dele **já é exercitada a cada
build**, e saber quais muda o tamanho da homologação: quem for operá-la não precisa gastar o dia
redescobrindo que a receita publica, e sim confirmando as linhas que nenhuma máquina alcança.

Três estados, e a diferença entre os dois primeiros importa:

- **Tela** — uma jornada E2E percorre a linha pela interface contra a stack real. O comportamento está
  provado; a homologação confirma o **ambiente**, e não a regra.
- **Só backend** — um teste de integração prova a regra pela API, e **ninguém nunca a viu numa tela**. É
  aqui que mora o defeito de interface que passa despercebido: a regra recusa, e a recusa não aparece.
- **Nenhuma** — não há prova automática. Estas são as linhas que a homologação existe para cobrir.

| Linha do roteiro | Estado | Prova |
|---|---|---|
| Preparo | Tela (parcial) | `business-journey`: equipamento, ingredientes com atributos técnicos, fornecedores e POP de limpeza publicado. Parâmetros da casa: `parameters.spec.ts` grava a validade do CIP pela tela, e `BreweryParametersIT` cobre o resto. **Usuários e permissões não têm prova** — o bootstrap local os cria prontos. |
| Recebimento | Tela | `business-journey` → `receive()`: lote do fornecedor, validade e inspeção `APPROVED`. |
| Receita publicada | Tela | `business-journey`: `/metrics` antes de `/publish` — a ordem é a regra. |
| Ordem | Tela | `business-journey`: criada, liberada com responsável, estoque reservado, iniciada, e a asserção de que **o lote nasceu** da ordem iniciada. |
| Produção | Tela (parcial) | `business-journey`: transferência com OG (1.052) e perdas (8 L). **Apontamentos de processo não são asseridos.** |
| Limpeza | Tela | `business-journey` → `releaseCleaning()`: ciclo com medições, verificação e liberação, na ordem que a linha cobra. |
| Envase | Tela | `business-journey`: plano, os três itens do checklist, reserva, execução, e a asserção de que saiu **um** lote de produto acabado. |
| Expedição | Tela | `business-journey`: expedição com destino e quantidade, e as **190 unidades sem destino** lidas na tela de produto acabado. |
| Pedido | Tela | `sales-journey`: cliente, canal, preço vigente e o pedido registrado **pelo formulário**. |
| Crédito | Tela + só backend | A porta interna está na tela (`sales-journey`): os três números da recusa, a justificativa, e a prova de que ela **não vaza para o pedido seguinte**. A recusa **no portal** só tem `CustomerPortalIT#oTetoDeCompromissoRecusaComOsTresNumeros`. |
| Recebimento (pagamento) | Tela | `sales-journey`: pagos os dois pedidos, um terceiro volta a caber sob o teto **sem justificativa nenhuma**. |
| Genealogia | Tela | `business-journey`: a cadeia do insumo à expedição, lida na tela de genealogia. |
| Simulado de recall | Tela | `business-journey`: escopo de 200 unidades (as 190 que ficaram não entram), e os **75% localizados** lidos na tela. |
| Custo e relatório do lote | Só backend | `BatchCostIT`, `BatchVarianceIT`, `BatchReportIT`. As telas (`costing.spec`, `reporting.spec`) provam que carregam — **não que os números fecham**. |
| Autorização (403) | Tela | `authority-and-isolation.spec.ts`: o operador **lê** o custo, a tela **não lhe oferece** o botão de fechar, o mesmo botão **aparece** para quem tem a alçada, e o POST direto responde `403` com `code: forbidden` — porque esconder o botão é cortesia, não autorização. Mais as **167 asserções de 403** dos ITs. |
| Isolamento | Tela | `authority-and-isolation.spec.ts`: a vizinha alcança **uma** cervejaria, não vê o lote desta casa na tela, recebe do endereço direto a **mesma resposta** que um id inexistente daria, e **vê o lote dela** — o contraponto sem o qual uma tela quebrada passaria por isolamento. Mais os 26 testes `outraCervejaria…` e o `TenantIsolationTest`. |
| Bloqueadores | — | Não é teste: é o `STATUS.md` das sprints. Hoje, **zero débitos registrados em aberto** (`DEB-PRD-002` fechou em 24/08). |
| **Frota** | Tela + só backend | `distribution-journey` → `kegCheio()`: etiqueta e inspeção válida. A **recusa** do vasilhame sem inspeção está em `ContainerIT#oConteinerNasceSemInspecaoENaoSeEnche` e `aInspecaoVencidaBloqueiaOEnchimento`. |
| **Enchimento** | Tela | `distribution-journey`: o histórico do keg ainda aponta para o lote **depois da volta inteira**. |
| **Carga** | Tela | `distribution-journey`: liberar a própria carga responde 409, e a segunda pessoa libera **em contexto de navegador próprio** — reaproveitar a sessão do primeiro não provaria nada. |
| **Entrega** | Tela + só backend | A prova e a recusa da segunda prova na mesma parada estão em `distribution-journey`. A **correção que aponta para a original** está em `DeliveryIT#aProvaNaoSeEditaESeCorrigePorEventoNovo`. |
| **Consentimento** | Só backend | `DeliveryIT`: `aEntregaAconteceSemAssinatura`, `aAssinaturaSoEntraComQuemConsentiuEParaQue`, `aAssinaturaSemFinalidadeNaoEntra`. |
| **Coleta** | Tela | `distribution-journey`: volta como `RETURNED` com `fillable: false`, e a higienização registra **o método**, que é o que se audita. |
| **Offline** | Só backend | `SyncIT#oReenvioDevolveOMesmoResultadoENaoCriaOutro` e `oConflitoNaoSeResolveSozinhoEEsperaGente`. |
| **Recall com contêiner** | Só backend | `RecallIT#oSimuladoAlcancaOsConteineresDoLote`, com o contraponto: um terceiro keg, de outro lote, **não sai no escopo**. |

**As duas linhas que o roteiro chama de "as que costumam faltar" faltavam por uma causa, e não por
esquecimento: o bootstrap local não sabia produzi-las.** O 403 na tela exigia alguém de pouca permissão
para logar, e as duas contas do perfil `local` estavam no mesmo grupo `ADMINISTRATORS`; o isolamento exigia
uma segunda cervejaria com gente dentro, e o bootstrap criava uma só. Não é coincidência que as linhas mais
esquecidas fossem as que o ambiente de teste não sabia montar — é causa.

**Desde 24/08 ele sabe.** O perfil `local` passou a semear mais duas contas e uma segunda cervejaria, e
cada conta de bootstrap varia **um eixo só** — que é o que as torna prova:

| Conta | O que ela varia | Para provar |
|---|---|---|
| `admin@brassia.local` | — (a linha de base) | o caminho feliz |
| `conferente@brassia.local` | a **pessoa** | separação de deveres: quem monta a carga não a libera |
| `operador@brassia.local` | as **permissões** | a recusa por alçada, e que a tela não oferece o que a API nega |
| `vizinha@brassia.local` | a **cervejaria** | o isolamento entre casas |

Uma conta que variasse dois eixos provaria menos: ao levar 403, não se saberia se foi por permissão ou por
casa errada — e são recusas diferentes, com correções diferentes. Nada disto existe fora do perfil `local`;
o grupo estreito é criado pelo initializer e não por migration, para não ir parar em produção.

**Na homologação as duas linhas continuam valendo a pena**, agora por outro motivo: não para descobrir se a
regra existe — isso o build confere —, mas para confirmar que o **ambiente** a aplica, com os grupos e as
cervejarias reais da casa, que não são os do bootstrap.

---

## 5. O que este manual não cobre, deliberadamente

- **Operação da infraestrutura** — deploy, migration e retorno estão em `infra/runbooks/deploy-rollback.md`.
- **Restauração de backup** — o ensaio está em `infra/runbooks/restore-drill.md`, com a execução mais
  recente registrada no fim. **O RTO está medido; o RPO não** — a janela de perda depende de política de
  backup, que ainda não existe. E o ensaio rodou em máquina de desenvolvimento com dados semeados, não em
  cópia de produção. Isso é informação operacional para quem for entrar em produção, não detalhe.
- **Módulos que não estão no caminho do primeiro lote** — água, sensorial, metrologia, gases, IA, sensores,
  webhooks, previsão de demanda e os módulos de inteligência têm tela própria e não são pré-requisito do
  ciclo. Entram quando a operação básica estiver de pé.
- **Comunidade e colaboração** — biblioteca pública, link compartilhado, fork e moderação (`/community/*`)
  ficam fora deliberadamente: eles colocam dado de cervejaria **fora** da cervejaria, e nada ali é
  pré-requisito de produzir ou vender. Quem for habilitá-los decide isso depois, e com outra cabeça.
