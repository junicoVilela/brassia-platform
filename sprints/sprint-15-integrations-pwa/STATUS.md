# Status — Sprint 15

Estado: NÃO INICIADA

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| INT-001 | Concluída | Claude | `backend/.../sensor`, `V98__sensor_ingestion.sql`, `frontend/.../features/sensors` | Módulo `sensor` novo. Idempotência pela restrição única do banco, qualidade e atraso sinalizados como eixos independentes. Ver DEC-INT-002/003 e DEB-INT-001. |
| INT-002 | Concluída | Claude | `backend/.../integration`, `V99__integration_webhooks.sql`, `frontend/.../features/webhooks` | Módulo `integration` novo. Outbox no mesmo commit do comando, HMAC com instante assinado, retry com backoff exponencial. Ver DEC-INT-005/006/007/008. |
| PWA-001 | Concluída | Claude | `frontend/.../core/offline`, `ngsw-config.json`, `manifest.webmanifest` | Service worker só para a aplicação; o roteiro é guardado por escolha explícita, carimbado com dono e cervejaria, vence em 12 h e é apagado no logout. Ver DEC-PWA-001/002 e DEB-PWA-001. |
| PWA-002 | Concluída | Claude | `frontend/.../core/offline/offline-queue.store.ts`, `V100__production_client_request_id.sql` | Fila com garantia "ao menos uma vez"; a chave gerada no registro (não no envio) a transforma em "exatamente uma" do lado do servidor. Conflito sai do ciclo automático e espera decisão. Ver DEC-PWA-003/004. |
| INT-003 | Concluída | Claude | `integration/domain/ScanReference`, `ScanController`, `frontend/.../features/scan` | O código carrega só o quê; a permissão do tipo apontado é verificada depois da leitura. Sem leitor de câmera: o QR é um link. Ver DEC-INT-009/010. |
| INT-006 | A fazer | — | — | — |
| SEC-B07 | A fazer | — | — | — |
| ~~INT-004~~ | Movida | — | — | Sprint 21 — ver DEC-INT-001 |
| ~~INT-005~~ | Movida | — | — | Sprint 21 — ver DEC-INT-001 |
| ~~INT-007~~ | Movida | — | — | Sprint 21 — ver DEC-INT-001 |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

### DEC-INT-001 — Conectores de terceiro saem para a Sprint 21

INT-004 (Brewfather), INT-005 (Brewer's Friend) e INT-007 (central de sincronização) foram movidas para
`sprints/sprint-21-external-connectors/`.

O motivo é verificabilidade, não prioridade. Os critérios de aceite dessas histórias são sobre comportamento
de fronteira — paginação, rate limit, backoff, timeout e revogação — e o projeto não tem credencial de teste
dos provedores. Implementá-las agora significaria exercitar o nosso código contra o contrato que **supomos**
que o provedor tem, e é exatamente a suposição que falha em integração com terceiro. A história seria marcada
como concluída sem que o critério tivesse sido cumprido, que é a forma mais cara de dívida: a que não parece
dívida.

INT-007 acompanha porque existe para exibir execuções, cursores, falhas, rate limit e conflitos **desses**
conectores. Sem eles seria uma tela sem conteúdo.

**Fica na Sprint 15** o que não depende de terceiro: INT-001 e INT-006 (ingestão de sensor e adapters de
dispositivo — o dispositivo é do próprio usuário e um broker MQTT roda em Testcontainers), INT-002 (webhook
de saída, cujo destino é configurado pelo usuário e testável contra servidor local), INT-003 (QR, que é
inteiramente nosso), PWA-001/002 e SEC-B07 (federação contra provedor que já temos validado desde a
Sprint 01-D).

**Consequência para a Sprint 21:** ela herda a dependência de inbox/idempotency e outbox construídos aqui, e
não pode começar sem credencial de teste — registrado como `BLQ-INT-001` no `STATUS.md` dela.

### DEC-INT-002 (INT-001) — A idempotência é da restrição única, não de uma consulta prévia

O caminho ingênuo — "procura se já existe, senão insere" — tem uma janela entre a pergunta e a escrita, e é
dentro dela que cai o reenvio de um gateway que despachou a mesma mensagem duas vezes em milissegundos. Ou
seja: a janela existe exatamente no cenário para o qual a idempotência existe. Quem decide é
`uq_sensor_reading_message (device_id, message_id)` com `ON CONFLICT DO NOTHING`; o caso de uso só lê o
resultado. `SensorIngestionIT.reenvioConcorrenteGravaUmaSo` dispara **oito requisições simultâneas** com a
mesma identidade contra PostgreSQL real e afirma 1×201 + 7×200, uma linha gravada.

Três consequências deliberadas:

- **A chave vem do dispositivo, não do conteúdo.** Um hash do payload trataria duas medições legitimamente
  idênticas — sensor parado, mesmo segundo, mesmo valor — como repetição, e descartaria uma leitura
  verdadeira. A repetição a reconhecer é a de *transporte*, e só quem enviou sabe que é o mesmo envio.
- **Repetida responde 200, não erro.** O dispositivo que não recebeu o ACK e reenviou fez a coisa certa;
  erro o ensinaria a continuar tentando. E `duplicate` viaja até a tela, porque distingue "o gateway está
  reenviando demais" de "estou recebendo o dobro de leituras" — mesmo sintoma no gráfico, soluções opostas.
- **A resposta devolve a leitura gravada, não a recém-montada.** Elas diferem no id e no `receivedAt`, e
  responder a segunda apagaria o atraso real do primeiro envio.

### DEC-INT-003 (INT-001) — Qualidade e atraso são sinalizados, e são eixos independentes

Recusar uma leitura ruim não deixa "nada": deixa um buraco na curva, e um buraco é indistinguível de "o
sensor não mediu" e de "não aconteceu nada". Gravada e marcada, a leitura conta duas coisas verdadeiras — o
dispositivo reportou naquele instante, e não se deve acreditar no número.

Atraso e qualidade ficam em colunas separadas porque descrevem problemas diferentes com providências
diferentes: uma leitura pode ter valor perfeito e ter chegado três horas tarde (rede), outra pode chegar na
hora com o sensor fora d'água (sensor). Um "status" único obrigaria a perder uma das duas informações.

Duas escolhas dentro disso:

- **`FUTURE_CLOCK` tem precedência sobre `OUT_OF_RANGE`.** Um dispositivo que volta de reset com o relógio
  de fábrica manda valores perfeitos com instante impossível; dizer "fora da faixa" responderia a pergunta
  errada — o problema não é o número, é que a leitura não pode ser posicionada na série.
- **O atraso é medido contra a régua do dispositivo**, não contra um limiar fixo: 30 s não significam nada
  num sensor horário e significam uma janela perdida num de 15 s. Sem `expectedInterval` cadastrado o
  atraso é medido e informado mas **não julgado** — afirmar atraso sem régua seria inventar um limiar.

**O que é recusado**, e por quê: grandeza ou unidade divergentes do cadastro (400) e dispositivo pausado ou
revogado (409). Erro de configuração e erro de estado não descrevem medição nenhuma — guardá-los
sinalizados contaminaria a série com linhas que ninguém sabe ler. O caso concreto é o firmware atualizado
que passa a mandar Fahrenheit sem avisar: converter em silêncio trocaria a escala da série histórica
inteira.

### DEC-INT-004 (INT-001) — Leitura de sensor não é auditada; o dispositivo é

O AGENTS.md pede auditoria para comando crítico, e telemetria não é comando. Um dispositivo de 30 segundos
gera 2.880 linhas por dia; auditar cada uma encheria a trilha de ruído até que ninguém mais encontrasse nela
a alteração de custo ou a liberação de lote que ela existe para guardar. O que **é** auditado é o que muda a
confiança na série: cadastrar, pausar e revogar. Uma lacuna de seis horas numa curva é uma pergunta sem
resposta até alguém descobrir que o dispositivo estava pausado, e quem o pausou.

A leitura em si é o próprio registro — imutável, com os dois relógios e a qualidade gravados. O repositório
não expõe `UPDATE` nem `DELETE`.

### DEC-INT-005 (INT-002) — O outbox grava na mesma transação do comando

`DomainEventOutboxListener` escuta em `BEFORE_COMMIT`, e essa escolha é a história inteira. A entrega é
gravada **dentro** da transação do comando que originou o evento: se a liberação da OP reverter, a entrega
reverte junto — e o webhook "ordem liberada" não sai para uma ordem que não existe. **Um webhook não se
desmanda; a única defesa é ele nunca ter saído.** `WebhookIT.outboxReverteComOComando` prova com um
rollback explícito contra PostgreSQL real.

`AFTER_COMMIT` pareceria mais seguro e seria pior de duas formas: a gravação ficaria fora da transação,
criando a janela em que o fato está commitado e a intenção de entregar não — evento perdido se o processo
cair no meio —, e um erro ali deixaria de ser reversível junto com o comando.

O que **não** acontece no listener é o envio. Enfileirar é barato e local; entregar depende de um servidor
de terceiro e roda depois, noutro processo. É essa separação que faz **"falha não bloqueia domínio" ser
estrutural em vez de intenção**: nada que aconteça com o destino — 500, timeout, DNS, certificado vencido —
tem caminho de volta até a liberação da OP, que já foi confirmada e respondida muito antes.

Duas consequências:

- **O payload é congelado no enfileiramento**, montado a partir do evento e não relido do banco.
  Recalculá-lo no reenvio entregaria o estado de agora sob o nome de um fato de antes — o retry de "ordem
  liberada" descreveria a ordem como ela está hoje, possivelmente já cancelada.
- **A identidade do fato inclui o que o distingue**: `recipeId:version`, porque publicar a v2 é outro fato
  e a restrição única trataria a segunda publicação como repetição da primeira.

### DEC-INT-006 (INT-002) — O instante entra dentro da assinatura, não ao lado dela

A assinatura HMAC prova que a mensagem saiu de quem conhece o segredo e que o corpo não mudou. Não prova
que ela é recente — por isso o formato assinado é `<timestamp>.<corpo>` e não só o corpo. Um instante que
viajasse apenas num cabeçalho não assinado seria reescrito por quem intercepta, e um replay de ontem
passaria por atual.

O separador não é enfeite: sem ele, `timestamp=1` + corpo `"23…"` e `timestamp=12` + corpo `"3…"`
alimentariam o HMAC com a mesma sequência de bytes — duas mensagens diferentes com a mesma assinatura
válida. O ponto não aparece em dígitos de época, então a divisão é sempre inequívoca. Fixado em
`WebhookSignatureTest.separadorDesfazAmbiguidade`.

Três decisões que acompanham:

- **Só `https`**, verificado no domínio e com `CHECK` no banco. A assinatura protege *integridade*, não
  sigilo: em HTTP puro, o que acontece na cervejaria trafega em texto claro. Aceitar `http` "só para teste"
  é exatamente como uma URL de teste vai para produção.
- **Redirecionamento é recusado** (`Redirect.NEVER`). Seguir um 302 mandaria o corpo assinado e os
  cabeçalhos para um endereço que ninguém cadastrou; um destino comprometido redirecionaria os eventos da
  cervejaria para onde quisesse.
- **O segredo é gerado pelo servidor**, nunca recebido. Aceitá-lo do cliente seria aceitar "senha123" com
  aparência de configuração legítima, e a assinatura só prova algo enquanto o segredo é imprevisível.

### DEC-INT-007 (INT-002) — Alçadas assimétricas: parar de mandar não pode ser difícil

Criar e **reativar** exigem `integration.webhook.manage`; **pausar e revogar exigem apenas
`integration.webhook.read`**. Parece invertido e não é: criar aponta um fluxo de dados da cervejaria para
um endereço de fora, e é esse ato que precisa de alçada e de trilha. Pausar e revogar *interrompem* o
fluxo — e uma permissão difícil para "parar de mandar" produz o incentivo errado exatamente no momento em
que se descobre que o destino foi comprometido.

Consequências deliberadas:

- **Pausar não descarta a fila.** Diz "pare de me mandar coisa nova", não "esqueça o que já aconteceu":
  descartar pendentes faria uma pausa de cinco minutos para manutenção perder eventos em silêncio.
- **O segredo é devolvido uma única vez**, na criação. Mesmo raciocínio de uma API key — ele precisa
  chegar a quem configura o outro lado, e depois disso não há motivo legítimo para lê-lo de volta. Manter
  um caminho de leitura seria manter uma porta que só serve para vazar. O acessor do domínio chama-se
  `secretForPersistence()` com nome desconfortável de propósito: um `secret()` curto seria chamado por
  engano na montagem de um DTO, que é como um segredo vaza para uma resposta HTTP.
- **A auditoria guarda o host, não a URL.** O caminho de um webhook às vezes carrega token.

### DEC-INT-008 (INT-002) — Retry com backoff exponencial, e o que desiste não some

Cinco tentativas (30 s, 1 min, 2 min, 4 min) cobrem cerca de meia hora — a ordem de grandeza de um deploy
ou de uma queda curta do outro lado. Backoff fixo martelaria um destino caído a cada 30 s, atrapalhando
justamente a recuperação dele; com muitas cervejarias apontando para o mesmo destino, viraria uma negação
de serviço acidental.

`EXHAUSTED` é terminal e **a linha não é apagada**. Uma entrega que desiste em silêncio é a pior forma de
falha de integração: o outro lado nunca soube do evento, e nós também não sabemos que ele não soube.

Mais três escolhas:

- **`FOR UPDATE SKIP LOCKED`** no `claimDue`, com a rodada inteira dentro de uma transação. É o que torna
  o despachante seguro com mais de uma instância — sem ele, duas instâncias pegariam as mesmas entregas e
  o destino receberia o evento em duplicidade, que ele não distingue de dois fatos iguais.
- **Destino inalcançável não tem status.** "Respondeu 500" e "não respondeu" apontam lados diferentes do
  problema, e só o *tipo* do erro é registrado — a mensagem pode conter a URL inteira.
- **Só o desfecho é auditado**, não cada tentativa: retry é comportamento esperado, não fato a guardar, e
  auditar todas encheria a trilha com o ruído de um destino instável.

### DEC-INT-009 (INT-003) — O código carrega o quê, nunca a credencial

Um QR colado num tanque é legível por qualquer pessoa que entre na sala, fotografável de longe e copiável
para outra etiqueta. **Qualquer segredo impresso nele é um segredo público.** Por isso `ScanReference`
contém apenas tipo e identificador — nada de token, assinatura ou credencial.

A consequência é a ordem das verificações: a sessão é exigida antes de tudo pelo filtro de segurança, e a
permissão é verificada **depois** de interpretar o código, porque só aí se sabe qual permissão é. Ler o
código não é ganhar acesso; é fazer uma pergunta que ainda precisa ser autorizada.

A alçada exigida é a **do tipo apontado**, declarada em `ScanTarget` junto do próprio tipo — não num mapa na
borda HTTP, que protegeria só o caminho que passa por ela. Quem pode ver equipamento não passa a ver lote
por ter lido um QR de lote.

**403 e não 404.** A pessoa apontou a câmera para uma etiqueta real; a resposta honesta é que ela não pode
ver aquilo, não que aquilo não existe. Já o código *ilegível* responde **422 com mensagem uniforme** para
todos os motivos — distinguir "formato inválido" de "tipo inexistente" ensinaria quais tipos existem a quem
sonda.

Duas escolhas de robustez:

- **O identificador tem alfabeto fechado** (letras, dígitos, hífen, sublinhado, ponto). A etiqueta é entrada
  de terceiro tanto quanto um formulário: qualquer um imprime um QR e cola no tanque. `../../admin`,
  `1?admin=true` e `<script>` são recusados.
- **Segmentos extras são ignorados**, porque PKG-004 já imprime `brassia://lote/<código>/envase/<plano>`.
  Recusar o sufixo invalidaria toda etiqueta já colada numa caixa.

### DEC-INT-010 (INT-003) — Não há leitor de câmera, e o endpoint é um roteador

**Sem biblioteca de leitura.** O QR contém um link para `/scan?code=…`, e quem lê é o aplicativo de câmera
que já vem no telefone — o mesmo que qualquer pessoa usa para ler um QR de restaurante. Embutir um leitor
significaria uma dependência a mais, permissão de câmera a pedir e uma experiência pior que a nativa em
troca de nada. A tela também aceita o código digitado, que cobre etiqueta rasgada e uso no computador.

É por isso que o endpoint é `GET`: ler não altera nada, e só um `GET` pode ser aberto por um link.

**O endpoint não carrega o recurso.** Quem responde pelo equipamento, pelo lote, pela OP e pela embalagem
são os módulos donos deles, e é lá que a cervejaria e o estado são verificados. Duplicar a verificação aqui
criaria uma segunda autoridade sobre a mesma pergunta — e duas autoridades divergem com o tempo. O scan
resolve *para onde ir*; a tela de destino faz o resto, como sempre fez.

Consequência: a rota `/scan` do frontend **não tem `permissionGuard`**. A alçada depende do tipo apontado,
que só se conhece depois de interpretar o código — e um guard fixo teria de escolher uma permissão antes de
saber qual.

### DEC-PWA-001 (PWA-001) — O service worker não cacheia a API, e a ausência é a decisão

`ngsw-config.json` tem `assetGroups` e **nenhum `dataGroup`**. Cachear respostas de API por padrão de URL
resolveria "ler offline" em três linhas de configuração — e junto guardaria **tudo** que casasse com o
padrão, de forma invisível, num armazenamento que sobrevive ao logout e é legível por quem usar o aparelho
depois.

O cenário não é hipotético: um tablet de chão de fábrica é compartilhado por turno, e um cache de "o que
passou pela API" acaba guardando o custo do lote e a trilha de auditoria porque alguém abriu essas telas uma
vez. O que o service worker cacheia aqui é só a aplicação — código e assets, que são públicos.

O dado do roteiro fica no `OfflineRunbookStore`, e a diferença é que ali ele é **escolhido, nomeado e
datado**: só o roteiro, só quando alguém pede, sempre com a identidade de quem pediu. A conversão de
`Batch` para `OfflineRunbook` é explícita campo a campo — é o que garante que um campo novo na API (um
custo, um responsável) não passe a ser gravado no aparelho só porque começou a ser devolvido. `orderId` e
`recipeId` existem em `Batch` e deliberadamente **não** são gravados: não servem para executar a etapa.

### DEC-PWA-002 (PWA-001) — Três defesas, e em todas a resposta é apagar

O critério "dados sensíveis seguem protegidos" é resolvido por três verificações em toda leitura:

- **Dono.** Roteiro salvo por outro usuário é recusado. É o tablet que troca de turno.
- **Cervejaria.** Mesmo usuário, cervejaria diferente, mesma recusa.
- **Validade (12 h).** Um roteiro velho descreve um lote que já mudou. Um roteiro desatualizado apresentado
  como atual é **pior que nenhum**: leva alguém a executar a etapa errada com confiança.

Nas três a resposta é **apagar o registro**, não apenas escondê-lo — esconder deixaria o dado no disco. E o
logout chama `clearAll()` num `finalize`, que cobre erro e sucesso: ficar sem rede na hora de sair é
justamente quando alguém entrega o aparelho para o próximo turno. Trocar de cervejaria também limpa.

Duas consequências para a interface:

- **Salvar é ação explícita**, não cache automático. Um cache que decide sozinho o que guardar acaba
  guardando o que ninguém pediu.
- **A tela diz que está mostrando um retrato e de quando ele é.** Sem essa frase, um roteiro de seis horas
  atrás parece o estado de agora.

`navigator.onLine` é usado só como **dica de interface**: ele diz que a máquina tem interface ativa, não que
o servidor está alcançável — o wi-fi da cervejaria pode estar conectado e sem saída. Quem decide se a
requisição funcionou é a requisição.

### DEC-PWA-003 (PWA-002) — A chave é gerada no registro, não no envio

A fila do aparelho tem garantia **"ao menos uma vez"** por natureza: ela reenvia até receber confirmação,
porque a alternativa — desistir na primeira falha — perderia o apontamento de quem estava sem rede, que é a
única razão de ela existir. "Exatamente uma vez" acontece do outro lado, e depende de onde a chave nasce.

O cenário decide: a medição é registrada às 9 h sem rede, a fila tenta às 11 h, a resposta se perde, e a
fila tenta de novo às 11 h 05. Uma chave gerada **no envio** seria diferente nas duas tentativas e criaria
duas medições da mesma leitura; gerada **no registro**, ela identifica o fato, e as duas tentativas são a
mesma coisa.

No servidor, quem decide é o índice único parcial `(brewery_id, client_request_id)` com `ON CONFLICT DO
NOTHING` — não uma consulta prévia, que deixaria a janela em que caem duas tentativas simultâneas da mesma
fila. O reenvio responde **200 com `duplicate: true`** e devolve a medição **gravada**, não a recém-montada:
responder um id novo faria a fila do aparelho guardar um id que não existe no servidor.

Duas escolhas dentro disso:

- **A repetição é reconhecida depois das validações de estado, não antes.** Se o lote foi encerrado
  enquanto a fila esperava rede, o reenvio é recusado como qualquer registro tardio — devolver "já
  registrado" para um lote encerrado inventaria uma medição que nunca entrou.
- **Único por cervejaria, não global.** A chave vem de um aparelho e não há autoridade central garantindo
  unicidade entre cervejarias. Colisão é improvável com UUID, mas fazer a corretude depender dessa
  improbabilidade seria fazer a corretude depender de sorte.

### DEC-PWA-004 (PWA-002) — Conflito não é falha de rede, e o tratamento é oposto

Este é o critério da história: **conflito não sobrescreve silenciosamente**.

- **Falha de rede ou 5xx** é transitória: conta a tentativa e mantém na fila. Desistir perderia o
  apontamento.
- **409/400/422** é o servidor dizendo que o estado mudou ou que o conteúdo não serve. Insistir produziria
  o mesmo "não" mais quatro vezes. O item **sai do ciclo automático e espera decisão humana** — nem
  descartado (perderia o apontamento), nem aplicado à força (sobrescreveria o que outra pessoa fez).
- **5xx fica deliberadamente fora** da classificação de conflito: erro de servidor costuma passar, e
  tratá-lo como conflito jogaria na mão de quem opera uma decisão que era só esperar.

Três consequências:

- **O envio é sequencial**, não paralelo: apontamentos do mesmo lote têm ordem, e disparar tudo de uma vez
  entregaria numa ordem que ninguém escolheu. Se a rede cai no meio, a drenagem para — não adianta gastar
  tentativa dos demais contra uma rede que já se sabe indisponível.
- **O conteúdo é congelado no registro.** O apontamento é o que foi medido, não o que vale agora.
- **A fila é apagada no logout e na troca de cervejaria**, como o roteiro. O que ainda não subiu se perde, e
  é o certo: enviá-lo depois, sob a sessão de outra pessoa, atribuiria a medição a quem não a fez.

### DEB-PWA-001 (PWA-001) — O teste de "salvar pela tela" é pulado em banco limpo

`offline-runbook.spec.ts` tem um caso que exige um lote em produção para clicar em "Salvar offline", e ele é
pulado quando não há nenhum — o que inclui a CI. O caso **crítico** (sair da conta esvazia o aparelho) foi
reescrito para semear o armazenamento direto e por isso roda sempre: **um teste de segurança pulado é pior
que ausente, porque parece cobertura.**
**Critério de remoção:** quando houver fixture de lote em produção compartilhada entre os E2E, apontar o
caso para ela.

### DEB-INT-002 (INT-002) — O caminho "evento real → webhook" não é exercido por IT

`WebhookIT` prova o outbox, a restrição única, o isolamento e as alçadas chamando o `EventEnqueuer` direto.
O caminho completo — liberar uma OP de verdade e ver a entrega aparecer — exigiria montar uma ordem
completa (~200 linhas já escritas em `BrewOrderIT`), e duplicá-las aqui testaria o planejamento, não a
integração. A tradução de evento em entrega é coberta por unidade.
**Critério de remoção:** quando houver um IT de jornada que já monte uma OP liberada, acrescentar a ele uma
asserção sobre a fila de webhooks em vez de montar a OP de novo aqui.

### DEB-INT-001 (INT-001) — A leitura de sensor não alimenta a curva de fermentação

`fermentation` já tem `FermentationReading` com `ReadingSource.SENSOR`, mas não publica porta de comando no
pacote raiz — o mesmo obstáculo registrado em DEB-AIA-001/002 da Sprint 14. Ligar a ingestão ao lote exigiria
criar `fermentation.FermentationCommands`, o que é mudança na história daquele módulo, não nesta.

Consequência prática: o sensor guarda a série dele, e quem quer ver a curva de fermentação continua
registrando a leitura à mão. **Critério de remoção:** quando `fermentation` publicar porta de comando, a
ingestão passa a encaminhar a leitura `GOOD` para o lote ativo do equipamento vinculado, na mesma transação.

Registro relacionado: `Measure` (sensor) e `ReadingKind` (fermentation) são enums separadas de propósito —
uma descreve o que um *dispositivo* reporta (tem `FLOW`), a outra o que se mede *de um lote* (tem `PH`).
Compartilhá-las criaria dependência entre módulos para economizar quatro constantes e amarraria a evolução
de um à do outro.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
