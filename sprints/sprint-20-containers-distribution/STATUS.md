# Status — Sprint 20

Estado: **ATIVA desde 2026-08-16** — as seis histórias entregues.

| História | Estado | Evidência |
|---|---|---|
| CON-001 | Entregue | `V130` · identidade, etiqueta e ciclo · 18 de domínio e 12 de integração |
| CON-002 | Entregue | `V131` · conteúdo e posição append-only · 11 de domínio e 11 de integração |
| LOG-001 | Entregue | `V132` · carga, roteiro e conferência por outra pessoa · 16 de domínio e 12 de integração |
| LOG-002 | Entregue | `V133` · prova append-only, consentimento e coordenada minimizada · 17 de domínio e 14 de integração |
| CON-003 | Entregue | `V134` · prazo, caução, atraso, perda e higienização · 14 de domínio e 11 de integração |
| MOB-001 | Entregue | `V135` · idempotência por aparelho e conflito explícito · 10 de domínio e 10 de integração |

## Decisões e bloqueios

### DEC-CON-001 (CON-001) — A identidade é do contêiner, e não da etiqueta

**A decisão.** O identificador (QR, código de barras, RFID) é tabela e objeto **separados** do contêiner.
A alternativa — o código lido como chave do vasilhame — faria trocar um adesivo descolado apagar cinco
anos de vida do keg: a inspeção, o histórico e a genealogia que a CON-002 vai pendurar aqui.

**Ler um código identifica, e não autoriza.** É o critério transversal da sprint escrito em código: o
agregado `ContainerIdentifier` não tem campo de permissão, cervejaria ou token, e um teste por reflexão
garante que continue assim. O endereço `GET /containers/by-identifier` exige `container.read` como
qualquer outra consulta, e quem escaneou continua precisando de alçada para mover, encher ou dar baixa —
um código fotografado no bar não é credencial em lugar nenhum.

**Um código ativo aponta para um contêiner só**, garantido por índice único **parcial**. Duas telas
colando o mesmo adesivo em kegs diferentes passariam por qualquer checagem prévia e deixariam a leitura
ambígua para sempre. O índice é parcial de propósito: o valor pode reaparecer na história — etiquetas
descolam e são refeitas — desde que nunca em dois vínculos vivos.

**Aposentar não apaga.** A etiqueta retirada continua explicando leituras antigas, mas deixa de resolver:
senão uma entrega de seis meses atrás passaria a apontar para outro keg depois de uma reetiquetagem.

### DEC-CON-002 (CON-001) — `RETURNED` não é `EMPTY`

**A decisão.** O que voltou do cliente está **sujo até que alguém diga o contrário**, num ato explícito —
o mesmo formato da liberação do lote pela qualidade (SAL-001-B). Derivar a disponibilidade da chegada
("voltou, logo está pronto") encheria com cerveja um vasilhame que ninguém lavou, e o problema apareceria
na boca do cliente.

**Encher exige três coisas juntas:** condição boa, estado vazio e **inspeção válida**. A recusa vem com
`reasonCode`, porque recusar sem motivo faria o operador tentar outro keg até um passar sem nunca saber o
que havia de errado com o primeiro. E `fillable` é composto no servidor: a tela não recalcula a regra, e
por isso não pode divergir dela.

**"Nunca inspecionado" é pior que "venceu".** Tratar a ausência de inspeção como aprovação deixaria a
frota nova inteira fora de qualquer controle — então o contêiner nasce sem inspeção e não pode ser
enchido.

**Baixa não é perda.** Não se dá baixa no que está com o cliente ou na rua: o vasilhame que não voltou é
outro fato, com outro dono (CON-003). O mesmo botão faria "sumiu" e "descartei" virarem a mesma linha no
inventário.

**Entregue:** `V130`, `Container`, `ContainerIdentifier`, `ContainerInspection` e exceções, porta, caso de
uso, oito endpoints, 8 caminhos e 3 schemas no OpenAPI, e a frota na tela. **18 testes de domínio, 12 de
integração e 6 de store.**

### DEC-CON-003 (CON-002) — O vínculo com o lote é evento, e não campo

**A decisão.** Um `lote_atual` na tabela do contêiner responderia "o que está dentro agora" e perderia "o
que estava dentro em 12 de março" — que é exatamente a pergunta de um recall. Um keg vive anos e passa por
dezenas de lotes; a única forma de a genealogia sobreviver a isso é o vínculo nascer histórico.
**Esvaziar fecha o período, e não apaga.**

O intervalo é **fechado no início e aberto no fim**. Sem isso, dois enchimentos seguidos responderiam
"sim" no mesmo instante da troca, e o recall recolheria dois lotes por causa de um keg. E a lacuna entre um
enchimento e o seguinte também é resposta: o vasilhame estava vazio.

**Um conteúdo vivo por contêiner**, garantido por índice único parcial — dois lotes no mesmo vasilhame
seria mistura sem registro. É o sexto caso da família: **a invariante que atravessa linhas mora no banco**,
porque a checagem prévia não sobrevive a duas telas enchendo o mesmo keg ao mesmo tempo.

**O contêiner virou nó da genealogia** (`NodeType.CONTAINER`), e não atributo do lote. Ele atravessa
lotes: pendurá-lo como propriedade do lote responderia "onde este lote foi parar" e nunca "o que este keg
já teve dentro" — e o segundo é o caminho de um recall que começa numa reclamação de bar. As direções são
`container → packaging` e `container → traceability`, sem ciclo: nenhum dos dois sabe que contêineres
existem.

**Encher precede liberar.** Kegs são enchidos na produção, antes de a qualidade assinar — exigir a
liberação aqui impediria a operação real de acontecer. Então lote **não liberado** passa, e lote
**vencido** ou **em quarentena** não: esses dois são fato consumado no momento do envase. Quem exige a
assinatura é a saída da casa.

**Duas famílias de recusa, e a distinção importa.** O *vasilhame* pode não estar apto
(`container_not_fillable`); o *líquido* pode não poder entrar (`fill_not_allowed`). Misturá-las daria ao
operador uma mensagem que não diz o que trocar.

**A posição acompanha o ciclo sem ninguém digitar** — rua, cliente e volta. Vazio e manutenção ficam de
fora de propósito: um keg vazio pode estar em qualquer depósito, e a oficina pode ser da casa ou de
terceiro. Inventar esses dois encheria o histórico de linhas que ninguém observou. E **sem posição não
significa "sumiu"**: significa que ninguém registrou.

**Entregue:** `V131`, `ContainerFill`, `ContainerLocation`, porta, caso de uso, `ContainerLineageSource`,
cinco endpoints, 3 caminhos e 2 schemas no OpenAPI, e o histórico na tela. **11 testes de domínio, 11 de
integração e 3 de store.**

### DEC-LOG-001 (LOG-001) — Quem montou a carga não é quem a libera

**A decisão.** `PLANNED` e `RELEASED` são estados diferentes de propósito: entre um e outro há uma pessoa
que não é a que planejou. A separação de deveres não é desconfiança do motorista — **a conferência existe
para encontrar o erro de quem montou**, e quem montou relê o próprio trabalho enxergando o que quis
colocar, e não o que colocou. Uma conferência feita pela mesma pessoa custa o mesmo tempo e não encontra
nada.

**Ela tem três camadas, e cada uma sozinha seria contornável.** O agregado recusa a mesma pessoa nos dois
papéis. A alçada `distribution.load.release` é separada de `plan` — só a regra do agregado seria burlada
dando as duas permissões a todo mundo, e só a alçada seria burlada por quem tem as duas. E um
`CHECK (released_by <> planned_by)` fecha o caminho para qualquer rota futura que esqueça de passar pelo
agregado: importação, correção manual, endpoint novo.

**A carga liberada congela, e reabrir derruba a conferência.** Acrescentar um keg numa carga já conferida
desfaz a conferência sem que ninguém perceba, e o papel que o motorista leva deixa de descrever o que está
no caminhão. Manter a conferência de pé depois da mudança seria pior que não ter conferência: o papel
diria que alguém olhou aquilo, e ninguém olhou.

**A saída cobra a qualidade** — a promessa que a CON-002 deixou em aberto. Encher precede liberar, e quem
exige a assinatura é a saída. A checagem roda na montagem **e** na liberação, por motivos opostos: na
montagem, para não jogar fora o trabalho de montar uma carga que não pode sair; na liberação, porque entre
uma e outra um lote pode ter entrado em quarentena — confiar na checagem da montagem seria confiar num
retrato de ontem. O `ContainerShippingLookup` compõe as condições em `container`, pelo mesmo desenho do
`SellableLotLookup`: quem tem o dado responde a pergunta.

**A capacidade é do veículo e conta as paradas juntas** (o caminhão é um só), e a recusa diz **quanto**
passou — "excedeu a capacidade" manda o operador tirar itens no chute. **A janela é compromisso, e não
previsão**: viaja com a parada em vez de ser derivada da sequência, senão a promessa mudaria toda vez que
alguém reordena o roteiro.

**Entregue:** `V132`, `Load`, `LoadStop`, `DeliveryWindow` e exceções, porta, caso de uso,
`ContainerShippingLookup` publicado por `container`, treze endpoints, 12 caminhos e 2 schemas no OpenAPI, e
as cargas na tela. **16 testes de domínio, 12 de integração e 5 de store.**

### DEC-LOG-002 (LOG-002) — A prova de entrega não se edita, e a mídia não existe sem consentimento

**Append-only, com correção por evento compensatório.** É o critério transversal da sprint, e aqui ele tem
uma razão específica: **uma prova de entrega reescrita é a pior espécie de registro** — ela *parece*
original e diz outra coisa, e ninguém consegue mais saber o que o entregador anotou às dez da manhã. A
correção aponta para a original, exige dizer o que estava errado, e não se corrige uma correção: encadear
versões tornaria "a última palavra" uma pergunta. Não há `PUT` nem `DELETE` nessa superfície, e a ausência
dos verbos é a regra.

**Uma prova por parada**, garantida por índice único parcial. A segunda tentativa é o duplo clique do
celular no meio da rua, e ela viraria duas entregas para o mesmo cliente.

**A mídia não pode existir sem consentimento** — não como checagem que alguém pode esquecer, mas porque o
objeto `ConsentedMedia` não é construtível sem quem consentiu, quando e **para quê**. Sem finalidade
escrita, o consentimento vira cheque em branco. E um `CHECK` no banco fecha o meio-termo: uma assinatura
guardada sem quem consentiu é dado pessoal sem base para estar ali. Recusar assinar **não trava a
operação**: o cliente que não quer assinar continua recebendo a cerveja.

**A geolocalização é minimizada no tipo, e não numa convenção.** `NUMERIC(6,3)` — três casas, ~100 m — não
consegue guardar mais casas nem que alguém tente. O motivo é concreto: a coordenada cheia do celular do
entregador, parada a parada, todo dia, é um rastro de movimentação de uma pessoa, e a operação só precisa
saber se a entrega foi no lugar certo. A coordenada cheia é arredondada na fronteira e não é guardada em
lugar nenhum — dado que não existe não vaza.

**"Não entregue" não é um motivo só.** Recusado, ausente e remarcado levam a ações diferentes amanhã;
juntá-los faria o roteirista tratar do mesmo jeito o bar que rejeitou a mercadoria e o que estava fechado
às sete. Por isso a não entrega exige motivo, e um `CHECK` cobra isso.

**Entregar e coletar são fatos separados**: o motorista recolhe vazios num bar onde não deixou nada, e às
vezes deixa sem recolher. Amarrá-los faria uma coleta exigir uma entrega inventada.

**A correção não remexe no vasilhame.** Um keg marcado como entregue que na verdade voltou precisa ser
movido por quem o tem na mão; adivinhar a transição a partir da correção produziria estados que ninguém
observou. A correção conserta o *registro*, e o vasilhame se conserta no ciclo dele.

**Entregue:** `V133`, `ProofOfDelivery`, `ConsentedMedia`, `CoarseLocation` e exceções, porta, caso de uso,
`ContainerMovementCommands` publicado por `container`, quatro endpoints, 4 caminhos e 3 schemas no OpenAPI,
e a entrega na tela. **17 testes de domínio, 14 de integração e 4 de store.**

### DEB-LOG-001 (LOG-001) — **RESOLVIDO na LOG-002**

O índice único garante que o vasilhame não se repita **dentro** de uma carga. A condição "não estar em
outra carga aberta" depende do estado da carga, que mora na outra tabela, e índice não faz junção — hoje
ela é checada no caso de uso, o que basta para o engano do dia a dia e **não** basta para duas telas
montando rotas ao mesmo tempo, que é exatamente o que acontece na véspera. A saída conhecida é o vasilhame
passar a `IN_TRANSIT` ao ser carregado, e aí o próprio ciclo do contêiner o torna indisponível: isso chega
com a LOG-002, que é quem move o estado na saída.

**Resolvido.** Foi exatamente essa saída: a partida da carga move o vasilhame para `IN_TRANSIT`, e o
`ContainerShippingLookup` só aceita quem está `FILLED` no depósito. O próprio ciclo do contêiner virou a
barreira. A checagem no caso de uso continua — agora para dar mensagem boa antes da saída, e não para
garantir.

### DEC-CON-004 (CON-003) — Perda não é baixa, e a caução registra a decisão em vez do dinheiro

**O escopo real da história.** Avaria, manutenção e baixa já vinham da CON-001; o que faltava era o
vasilhame que está **fora de casa**. A CON-002 já sabe que o keg está no cliente — faltava o compromisso:
até quando deveria voltar, quanto ficou retido, e o que fazer quando o prazo passa. Sem isso, "no cliente
há dois dias" e "no cliente há sete meses" são a mesma linha na tela.

**Atrasado é o que ainda não voltou depois do prazo** — e não o que voltou tarde. São duas listas que
servem a decisões diferentes: uma é dívida em aberto, a outra é histórico de quem devolve tarde. Misturar
faria a cobrança do dia ligar para quem já devolveu. E o atraso **nunca é negativo**: "faltam três dias" é
outra pergunta, e somá-la com atrasos daria zero sem nenhum keg no lugar.

**A caução registra a DECISÃO, e não o dinheiro.** `TO_REFUND` e `RETAINED` dizem o que a operação
decidiu; o estorno é lançamento financeiro e mora onde o dinheiro mora. Afirmá-lo aqui faria o sistema
dizer que houve um pagamento que ninguém fez. E ausência de caução é **nulo, não zero** — zero somaria no
relatório de valores retidos como se houvesse dinheiro parado.

**Perda não é baixa, e agora existe o caminho que faltava.** A CON-001 recusa dar baixa no que está com o
cliente, de propósito: "sumiu" e "descartei" não podem virar a mesma linha no inventário. A CON-003 abre a
exceção deliberada — `Container.declareLost` — que só é alcançável a partir de um empréstimo aberto, com
motivo obrigatório e alçada crítica, e que **carrega o motivo para dentro do registro da baixa**
(`"perdido: …"`). O inventário nunca precisa adivinhar qual dos dois aconteceu.

**Um empréstimo aberto por vasilhame**, por índice único parcial: o mesmo keg com dois clientes ao mesmo
tempo é impossível no mundo e contabilizaria duas cauções — e a checagem prévia não sobrevive a duas telas
registrando saídas na mesma manhã. É o sétimo caso da família.

**A higienização deu lastro ao ato explícito da CON-001.** Lá, liberar o keg que voltou era um ato sem
registro; aqui ele ganha nome, data e **método**. "Higienizado" sem dizer como é um carimbo, e um carimbo
não se audita — a pergunta real chega três meses depois, quando alguém quer saber se aquele keg foi lavado
antes da cerveja que o cliente reclamou.

**A devolução não move o vasilhame.** Quem move é a coleta (LOG-002) ou a operação manual; duplicar a
transição aqui produziria dois caminhos para o mesmo fato, e eles divergiriam na primeira regra nova.

**Entregue:** `V134`, `ContainerLoan`, `DepositAmount`, `SanitationRecord`, porta, caso de uso, seis
endpoints, 6 caminhos e 2 schemas no OpenAPI, e a fila de atrasados na tela. **14 testes de domínio, 11 de
integração e 5 de store.**

### DEC-MOB-001 (MOB-001) — O identificador é do aparelho, e o conflito não se resolve sozinho

**A idempotência tem de ser nomeável offline.** O identificador da operação vem do **aparelho**, porque
sem sinal não há como pedir um número ao servidor — e sem um id que o dispositivo gere sozinho, o
entregador que aperta "sincronizar" duas vezes num sinal ruim registra duas entregas para o mesmo cliente.
A garantia é um índice único em `(device_id, client_operation_id)`: o retry automático do aplicativo,
enquanto o sinal vai e volta, passaria por qualquer checagem prévia. **Por dispositivo, e não global** —
dois aparelhos podem sortear o mesmo UUID sem que isso signifique nada.

**O reenvio devolve o mesmo resultado, e não grava de novo.** `DUPLICATE` não é erro: é a resposta que
permite ao aparelho fechar o item na tela.

**Conflito é estado, e não exceção.** Quando a parada já foi registrada pelo escritório, a operação do
aparelho **não sobrescreve nem some**: fica marcada, com o motivo, numa fila que alguém olha.
Último-a-escrever-ganha descartaria em silêncio o registro de quem estava lá — ou o do escritório —, e nos
dois casos alguém descobre semanas depois sem saber o que perdeu. A distinção entre conflito e recusa é
feita pelo `reasonCode` da LOG-002: `already_recorded` vira conflito, o resto vira recusa com motivo.

**Duas horas, e as duas ficam.** `occurredAt` é do aparelho — quando a cerveja desceu; `receivedAt` é do
servidor. Usar a do servidor para o fato colocaria toda entrega offline no momento em que o caminhão
voltou ao depósito, e **ninguém entregou nada no pátio às seis da tarde**. Relógio adiantado é *marcado*
(`clockAhead`), e não recusado: o celular não se ajusta sozinho no subsolo do bar, e recusar perderia o
registro do que aconteceu de verdade.

**Cada operação entra na própria transação**, e a resposta é 200 com uma lista de desfechos — e não 201.
Uma parada em conflito não pode desfazer as outras cinco que já entraram: o entregador ficaria com o dia
inteiro por sincronizar por causa de uma que o escritório tocou. E "sincronizado" sozinho não distingue o
que entrou do que foi recusado, então cada item diz o seu status.

**A ordem aplicada é a do aparelho**, e não a dos pacotes que chegaram pela rede: aplicar fora dela
entregaria antes de despachar.

**A leitura de código já existia** (CON-001) e não foi refeita: `GET /containers/by-identifier` é o mesmo
endereço que o aplicativo usa, com a mesma regra — ler identifica, e não autoriza.

**Entregue:** `V135`, `OfflineOperation`, `SyncStatus`, porta, caso de uso, três endpoints, 3 caminhos e 2
schemas no OpenAPI, e a fila de conflitos na tela. **10 testes de domínio, 10 de integração e 1 de store.**

### DEB-CON-002 (CON-003) — `DepositAmount` duplica a regra do `Money` de vendas

O `Money` da SAL-001 vive dentro do domínio de `sales` — não é porta publicada —, e importá-lo de
`container` furaria a fronteira do módulo para economizar vinte linhas. Copiar a regra é o preço normal de
manter os módulos separados, mas a duplicação é real: **duas definições de "dinheiro com moeda explícita"
podem divergir**, e a segunda a mudar não avisa a primeira. Promover `Money` a tipo compartilhado é decisão
de arquitetura que merece história própria — e ela fica mais fácil de tomar agora que existem dois usos.

### DUV-CON-002 (CON-003) — O vasilhame dado como perdido que reaparece

**A pergunta.** Um keg declarado perdido volta seis meses depois — o bar reabriu, o cliente achou no
depósito. Hoje não há caminho: o empréstimo está encerrado e o contêiner, baixado.

**Por que não foi inventado.** A volta envolve **dinheiro que já mudou de mãos**: a caução foi retida, e
possivelmente cobrada. Desfazer isso é estorno, e decidir se ele acontece, quem autoriza e o que acontece
com o vasilhame (volta ao inventário? entra como novo?) é decisão de negócio com efeito financeiro. Inventar
aqui seria decidir sozinho sobre dinheiro do cliente.

**O que já está pronto para qualquer resposta.** A baixa carrega o motivo (`"perdido: …"`), então a
distinção entre keg descartado e keg perdido sobrevive; e o empréstimo guarda a caução e o desfecho, então
o que precisaria ser estornado está registrado.

### DEB-CON-001 (CON-002) — O dublê de lote acabado nos testes de contêiner

Os testes de integração do vasilhame usam um `SellableLotLookup` roteirizado. A composição real das três
condições de venda é exercida de ponta a ponta pelo `PackagingRunIT`; reproduzir mil linhas de cenário de
envase aqui deixaria o teste caro e o motivo da falha longe da causa. **O risco assumido:** se a assinatura
ou a semântica dos impedimentos mudar, estes testes continuam verdes com um contrato velho. Mitiga em
parte o fato de os códigos de impedimento serem os mesmos que o `PackagingRunIT` exercita de verdade.

### DUV-CON-001 (CON-001) — Qual é a periodicidade da inspeção?

**A pergunta.** A validade da inspeção é **informada por quem inspeciona**, e não calculada a partir de um
intervalo. Falta saber se a casa segue uma norma com prazo fixo, se ele varia por tipo de vasilhame, e se
o sistema deveria propor a data em vez de só aceitá-la.

**Por que não foi inventado.** Escrever aqui "cinco anos" faria o sistema **afirmar conformidade que
ninguém verificou** — e é a inspeção que libera um vaso de pressão para receber cerveja carbonatada. Um
prazo errado por excesso é risco físico; por falta, é frota parada sem motivo.

**O que fica pronto para qualquer resposta.** A validade já é campo com `CHECK` de ser posterior à
inspeção, e a regra de bloqueio já está no agregado. Se a periodicidade vier depois, ela vira sugestão de
data — e não muda o modelo.
