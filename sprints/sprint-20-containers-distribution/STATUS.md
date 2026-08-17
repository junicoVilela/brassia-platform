# Status — Sprint 20

Estado: **ATIVA desde 2026-08-16** — CON-001, CON-002, LOG-001 e LOG-002 entregues.

| História | Estado | Evidência |
|---|---|---|
| CON-001 | Entregue | `V130` · identidade, etiqueta e ciclo · 18 de domínio e 12 de integração |
| CON-002 | Entregue | `V131` · conteúdo e posição append-only · 11 de domínio e 11 de integração |
| LOG-001 | Entregue | `V132` · carga, roteiro e conferência por outra pessoa · 16 de domínio e 12 de integração |
| LOG-002 | Entregue | `V133` · prova append-only, consentimento e coordenada minimizada · 17 de domínio e 14 de integração |
| CON-003 | A fazer | — |
| MOB-001 | A fazer | — |

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
