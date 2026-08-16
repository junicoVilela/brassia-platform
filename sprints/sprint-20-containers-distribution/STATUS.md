# Status — Sprint 20

Estado: **ATIVA desde 2026-08-16** — CON-001 e CON-002 entregues.

| História | Estado | Evidência |
|---|---|---|
| CON-001 | Entregue | `V130` · identidade, etiqueta e ciclo · 18 de domínio e 12 de integração |
| CON-002 | Entregue | `V131` · conteúdo e posição append-only · 11 de domínio e 11 de integração |
| LOG-001 | A fazer | — |
| LOG-002 | A fazer | — |
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
