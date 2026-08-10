# Status — Sprint 15

Estado: CONCLUÍDA (7/7) — as duas histórias parciais fecharam: `INT-006` (MQTT contra broker real) e
`SEC-B07` (OIDC e SAML contra Keycloak real). Restam débitos declarados, nenhum bloqueando entrega.

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| INT-001 | Concluída | Claude | `backend/.../sensor`, `V98__sensor_ingestion.sql`, `frontend/.../features/sensors` | Módulo `sensor` novo. Idempotência pela restrição única do banco, qualidade e atraso sinalizados como eixos independentes. Ver DEC-INT-002/003 e DEB-INT-001. |
| INT-002 | Concluída | Claude | `backend/.../integration`, `V99__integration_webhooks.sql`, `frontend/.../features/webhooks` | Módulo `integration` novo. Outbox no mesmo commit do comando, HMAC com instante assinado, retry com backoff exponencial. Ver DEC-INT-005/006/007/008. |
| PWA-001 | Concluída | Claude | `frontend/.../core/offline`, `ngsw-config.json`, `manifest.webmanifest` | Service worker só para a aplicação; o roteiro é guardado por escolha explícita, carimbado com dono e cervejaria, vence em 12 h e é apagado no logout. Ver DEC-PWA-001/002 e DEB-PWA-001. |
| PWA-002 | Concluída | Claude | `frontend/.../core/offline/offline-queue.store.ts`, `V100__production_client_request_id.sql` | Fila com garantia "ao menos uma vez"; a chave gerada no registro (não no envio) a transforma em "exatamente uma" do lado do servidor. Conflito sai do ciclo automático e espera decisão. Ver DEC-PWA-003/004. |
| INT-003 | Concluída | Claude | `integration/domain/ScanReference`, `ScanController`, `frontend/.../features/scan` | O código carrega só o quê; a permissão do tipo apontado é verificada depois da leitura. Sem leitor de câmera: o QR é um link. Ver DEC-INT-009/010. |
| INT-006 | Concluída | Claude | `sensor/domain/PayloadFormat`, `SensorMqttSubscriber`, `V108__sensor_mqtt.sql` | Adapters de payload por HTTP **e MQTT**, exercitado contra broker real. DEB-INT-003 resolvido. |
| SEC-B07 | Concluída | Claude | `security/domain/SsoHandshake`, `AccountLinkDecision`, `SsoLoginHandler`, `V102__sso_handshake.sql` | Fluxo SP-initiated com state/nonce/PKCE, uso único no banco e vínculo que recusa sequestro. **OIDC e SAML exercitados contra Keycloak real** — DEB-SEC-001 resolvido por inteiro. |
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

### DEC-SEC-001 (SEC-B07) — Três amarras, cada uma contra um ataque diferente

Um login federado é uma conversa que sai da aplicação, passa por um terceiro e volta. **Entre a ida e a
volta não há nada ligando as duas pontas**: o navegador que volta pode ser outro, a resposta pode ter sido
fabricada, e a mesma resposta pode voltar duas vezes. `SsoHandshake` é o que amarra.

- **`state`** — contra CSRF de login. Sem ele, um atacante inicia um fluxo com a própria conta e induz a
  vítima a completá-lo: ela fica logada como ele e digita dados dele achando que são seus. Comparado em
  **tempo constante** — um `equals` sai no primeiro byte diferente, e essa diferença é medível pela rede.
- **`nonce`** — contra replay do token. Viaja ao provedor e volta dentro do token assinado.
- **PKCE (S256)** — contra interceptação do código. O verificador **nunca sai daqui**; só o desafio
  derivado vai ao provedor, e é essa assimetria que faz o mecanismo valer. `plain` não é aceito: com ele o
  desafio *é* o verificador.

**Uso único, decidido pelo banco.** O `UPDATE ... WHERE consumed_at IS NULL` é o ponto: duas voltas
simultâneas com a mesma resposta — duplo clique, retry do navegador, aba duplicada — passariam as duas por
uma checagem feita em memória. Vence exatamente uma.

**Dez minutos de validade**: cobre digitar senha e segundo fator do outro lado, e não cobre a aba esquecida
aberta ontem — que é onde um handshake vivo vira janela de ataque.

**SP-initiated apenas.** Um fluxo IdP-initiated chega sem ida, e por isso não tem como ser distinguido de
uma resposta fabricada.

**O destino pós-login é só caminho interno.** Aceitar URL absoluta faria do login um redirecionador aberto:
um link para o nosso domínio que, depois de autenticar, joga a pessoa num site de terceiro — com a barra de
endereço tendo mostrado o nosso domínio o tempo todo. `//evil.example.com` também é recusado: é URL
absoluta protocol-relative.

### DEC-SEC-002 (SEC-B07) — E-mail nunca vincula sozinho a uma conta que já existe

Esta é **a parte perigosa de todo SSO**. O caminho tentador — "o provedor disse que é ana@cervejaria.com,
então logue como a nossa ana@cervejaria.com" — entrega qualquer conta local a quem controlar ou enganar um
provedor configurado. É o *account takeover por asserção de e-mail*, e já derrubou sistemas grandes.

`AccountLinkDecision` tem três desfechos e nenhum outro:

| Situação | Desfecho |
|---|---|
| Já existe vínculo provedor+subject | **Entra.** É o segundo login de quem já provou os dois lados |
| Existe conta local de mesmo e-mail, sem vínculo | **RECUSA.** Nem com JIT ligado e e-mail verificado |
| Não existe nada, JIT ligado, e-mail verificado | **Cria.** Não há o que sequestrar |

O caminho legítimo para quem tem conta local é entrar por ela e vincular o provedor de dentro — provando os
dois lados. **A recusa é auditada** com o e-mail asserido e o motivo: se tivesse passado, seria um sequestro
de conta, e quem investiga precisa encontrá-la.

**E-mail não verificado não provisiona**, mesmo com JIT: sem verificação, quem consegue um cadastro no
provedor escolhe com qual e-mail aparece aqui.

**A ordem das verificações é a segurança.** Aperto de mão → verificação do provedor → decisão de vínculo.
Decidir o vínculo antes de verificar seria acreditar num e-mail que ninguém provou ter vindo do provedor.
Fixado em teste.

**A conta provisionada nasce sem senha local e sem cervejaria.** Sem senha porque quem entra por federação
entra pelo provedor — uma senha aleatória seria uma credencial que ninguém conhece mas que existe para ser
adivinhada. Sem cervejaria porque acesso é concedido por quem administra: provisionamento automático não
pode virar concessão automática.

### DEB-SEC-001 (SEC-B07) — RESOLVIDO: troca OIDC exercitada contra Keycloak real

O critério de remoção era explícito: *Keycloak em Testcontainers com realm de teste, e um IT cobrindo login
bem sucedido, nonce de outra conversa, código já usado e token com assinatura inválida.* **Os quatro passam.**

A troca agora é real: o código vai ao endpoint de token com o verificador PKCE, e o `id_token` tem a
**assinatura conferida contra o JWKS antes de qualquer coisa dele ser lida** — ler o `sub` ou o `email` de
um JWT antes da assinatura é ler o que o atacante escreveu.

Quatro barreiras independentes, e cada uma pega um ataque diferente:

- **Assinatura** (JWKS) — token forjado.
- **Emissor** — token de outro provedor.
- **Audiência** — token legítimo emitido para *outro cliente do mesmo provedor*; tem assinatura válida e
  emissor correto, e só a `aud` o distingue.
- **Nonce** — token legítimo de *outra conversa* do mesmo cliente. É o caso que a assinatura não pega.

**Keycloak de verdade, não dublê.** Um dublê devolve o que o código espera e prova apenas que o código lê o
que ele mesmo escreveu. Só um provedor real exercita JWKS publicado, assinatura, código de uso único e o
nonce viajando pelo protocolo.

**Duas tentações recusadas no caminho.** O `OidcTokenClaimsValidator` exige emissor `https://` e o container
servia HTTP: afrouxar o validador faria o teste passar e trocaria uma garantia real por um verde — um
emissor em HTTP entrega o `id_token` em texto claro a quem estiver no caminho. Liguei TLS no container. Em
seguida a aplicação recusou o certificado autoassinado, e a saída também não foi afrouxar a validação dela:
o teste troca o `SSLContext` **da própria JVM**, que é o que uma cervejaria faria com uma CA interna.

**Dois defeitos encontrados por acidente, e ambos valiosos:**

- `spring-boot-starter-oauth2-client` estava `optional`. Compilava e **não iria para o jar** — o mesmo erro
  que o comentário do Jackson, dez linhas acima no `pom.xml`, registra ter acontecido antes nesta base.
  Enquanto a troca era recusada nada em runtime tocava aquelas classes; a partir de agora, tocaria.
- O adaptador engolia a causa da falha de I/O. Passou a registrar o **tipo** (`SSLHandshakeException` vs
  `ConnectException`), nunca a mensagem — que carrega a URL inteira. Mesmo critério do `HttpWebhookSender`,
  e a diferença entre os dois tipos vale muito num incidente.

**SAML também fechou.** A justificativa que eu havia escrito — *"exige um IdP SAML de verdade"* — descrevia
um bloqueio que não existia: o Keycloak fala SAML nativamente, e bastou um cliente a mais no realm que já
estava no projeto. É a terceira vez nesta sequência em que tratei "precisa de coisa real" como impedimento
tendo a coisa real à mão.

O que o verificador protege, além da assinatura:

- **Assinatura na ASSERTION, não só no envelope.** É a diferença que o *XML Signature Wrapping* explora:
  uma Response assinada pode carregar assertion trocada, e quem valida só o envelope aceita o conteúdo
  adulterado. A assertion consumida é a mesma instância que teve a assinatura conferida.
- **DOCTYPE recusado** (XXE) — mesma decisão do BeerXML, e aqui vale mais porque a fonte é menos confiável.
- **Audiência, destino e validade.** Assinatura prova que o IdP emitiu; não prova que era para nós, nem
  para este endpoint, nem que ainda vale. Uma assertion legítima capturada de outro serviço do mesmo IdP
  passa na assinatura e falha na audiência.

**A defesa contra sequestro por e-mail funcionou antes de eu testá-la:** o primeiro login SAML foi recusado
porque o SAML não tem `emailVerified` padronizado e a ausência conta como não verificado — então o
provisionamento automático negou. Precisei fazer o IdP *afirmar* a verificação para o caso feliz existir.

`spring-security-saml2` estava `optional`: compilava e não iria para o jar. **Terceira vez** que este
projeto tropeça no mesmo defeito de empacotamento — o comentário do Jackson registra a primeira.

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

### DEC-INT-011 (INT-006) — O adapter traduz e delega; ele não grava

`AdapterIngestionHandler` converte o payload do fabricante para o formato canônico e chama o caso de uso de
INT-001. Reimplementar a gravação aqui criaria uma **segunda forma de gravar leitura** — e duas formas
divergem: uma ganharia uma regra que a outra não tem, e o caminho por onde a leitura entrou passaria a mudar
o que ela significa.

Três consequências:

- **As chaves das leituras derivam do identificador da mensagem** (`externalReadingId:GRANDEZA`), nunca
  sorteadas. É o que faz a idempotência de INT-001 valer aqui: reenviar a mensagem inteira reconhece as
  leituras como repetição. Uma chave sorteada por leitura faria do adapter **o furo por onde a idempotência
  vaza**. O sufixo com a grandeza existe porque uma mensagem vira várias leituras — sem ele, densidade e
  temperatura do mesmo envio disputariam a mesma chave e uma seria descartada como duplicata.
- **Só a grandeza cadastrada é gravada.** Um iSpindel manda densidade e temperatura juntas; um dispositivo
  cadastrado como termômetro não começa a gravar densidade porque o firmware passou a incluí-la — a série
  mudaria de assunto sozinha.
- **O dispositivo vem da URL, não do payload.** O `deviceId` de dentro da mensagem é informação do
  fabricante e serve para conferência; deixá-lo escolher permitiria a um gateway gravar na série de outro
  aparelho da mesma cervejaria.

### DEC-INT-012 (INT-006) — A conversão de unidade acontece na borda

Fahrenheit vira Celsius e kPa vira bar dentro do `PayloadFormat`, antes de qualquer coisa entrar no
domínio. Guardar a unidade do fabricante e converter na leitura espalharia a conversão por toda consulta que
tocasse a série — e uma delas acabaria esquecida.

Pelo mesmo motivo o **formato é atributo do cadastro, não da mensagem**: deixar o payload declarar o próprio
formato seria confiar num campo que o firmware preenche, e uma atualização que mudasse a declaração passaria
a ser interpretada de outro jeito sem que ninguém decidisse isso.

Duas escolhas de leitura defensiva, porque payload de dispositivo é entrada de terceiro:

- **Número como texto é aceito.** Muitos firmwares serializam tudo como string, e recusar isso rejeitaria
  aparelhos que funcionam perfeitamente. Texto que **não** é número é recusado — aí o campo está errado, e
  dizer isso é melhor que gravar zero.
- **O erro nomeia o campo.** "Payload inválido" mandaria quem configura o gateway adivinhar.

### DEB-INT-003 (INT-006) — RESOLVIDO: transporte MQTT contra broker real

O critério de remoção era: *cliente MQTT configurado por cervejaria, tópico por dispositivo, e
`SensorMqttIT` com broker real em Testcontainers cobrindo entrega, reentrega (QoS 1) e credencial
revogada.* **Entregue, com HiveMQ em container.**

**A previsão do débito se confirmou:** a tradução era mesmo independente do transporte. O assinante não
converte nada — recebe, extrai o código do tópico e chama `AdapterIngestionCommands.ingest`. Conversão,
idempotência, qualidade e atraso continuam onde estavam.

Decisões do transporte:

- **Configuração por cervejaria, não global.** Cada uma tem o próprio broker — o da fábrica, o do
  integrador, o da nuvem do fabricante. Um cliente global obrigaria todas a compartilhar broker e faria a
  credencial de uma alcançar os tópicos das outras.
- **O dispositivo vem do TÓPICO, nunca do corpo.** Mesma regra da ingestão HTTP, que tira o código da URL.
  Um gateway que escolhesse o aparelho pelo payload gravaria na série de outro da mesma cervejaria — e há
  um teste que publica um `deviceId` divergente para provar que o tópico vence.
- **QoS 1.** Com 0 uma leitura se perde no reconnect sem ninguém saber; com 2 o handshake não se paga para
  um dado que já é idempotente do nosso lado. Em 1 a mensagem pode chegar duas vezes, e chegar duas vezes
  é inofensivo: a idempotência está no banco, por `externalReadingId`.
- **`CHECK` exigindo TLS** no destino. `tcp://` entrega credencial e leitura em texto claro na rede da
  fábrica — justamente a rede com mais gente com acesso físico. Só `localhost` é tolerado.
- **Sessão limpa.** Mensagens acumuladas enquanto estivemos fora chegariam com horas de atraso e seriam
  marcadas como atrasadas — ruído que não descreve o processo.
- **Reassinatura no reconnect.** Com sessão limpa, o cliente volta conectado e **mudo** se ninguém
  reassinar — o pior estado possível, porque parece saudável.
- **Mensagem malformada não derruba o assinante.** Um assinante morto perde tudo em silêncio, e há teste
  para isso.

**`refresh()` nasceu do teste e resolve uma lacuna real:** sem ele, uma cervejaria que configura o broker
hoje só receberia leituras no próximo reinício da aplicação.

**O log do descarte inclui a mensagem da exceção**, não só o tipo — ao contrário do critério usado no
webhook e no SSO, e aqui é o certo: a mensagem diz *qual regra recusou* (unidade divergente, dispositivo
inativo, campo faltando), que é o que quem opera precisa. O que não entra é o corpo recebido, de fonte não
confiável.

**Três defeitos meus no caminho, todos do mesmo tipo — supor em vez de ler:** criei um `application.yml`
de teste que sombreava o principal (e a cópia obsoleta em `target/test-classes` seguiu sombreando depois
de apagá-lo); usei `created_at` numa tabela com `registered_at`; e **inventei o formato canônico do
payload** em vez de ler `PayloadFormat`. O que destravou foi passar a ler o log do assinante: ele apontou
a causa exata de cada iteração seguinte.

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

### DEB-PWA-001 (PWA-001) — RESOLVIDO: a fixture compartilhada existe, e o teste deixou de pular

`offline-runbook.spec.ts` tinha um caso que exigia um lote em produção para clicar em "Salvar offline" e era
pulado quando não havia nenhum — o que incluía a CI. O caso **crítico** (sair da conta esvazia o aparelho) já
rodava sempre, porque semeia o armazenamento direto: **um teste de segurança pulado é pior que ausente,
porque parece cobertura.**

**Critério de remoção cumprido.** `seedBatch(page)` em `e2e/tests/support.ts` (PR #193) monta a cadeia
completa — equipamento, insumos, receita, ordem, lote iniciado — e é compartilhada entre os E2E. O
`test.skip` saiu.

O que apareceu ao tirá-lo vale registro: o caso não estava só pulando por falta de lote, estava numa
**corrida**. `page.goto` volta com o HTML, não com os dados; o teste contava zero botões e desistia,
parecendo verde. Agora espera a resposta de `/api/v1/production/batches` antes de contar. O `skip`
escondia duas falhas diferentes com a mesma cor.

### DEB-INT-002 (INT-002) — RESOLVIDO: a jornada evento→entrega é exercida onde a OP já é montada

**Critério de remoção cumprido ao pé da letra.** A asserção foi para o `BrewOrderReleaseIT`, que já monta e
libera uma ordem por outro motivo — em vez de duplicar ~200 linhas de planejamento dentro do teste de
integração, que testaria o planejamento e não o elo.

O elo é o que nenhum teste cobria: que liberar publica o evento, que o ouvinte o traduz, e que a entrega
nasce **dentro da mesma transação** (`BEFORE_COMMIT`). O `WebhookIT` provava o outbox, a restrição única, o
isolamento e as alçadas — chamando o enfileirador direto, ou seja, pulando exatamente esse trecho.

**Dois casos, e o segundo é o que dá valor ao primeiro:** ordem liberada com assinatura gera uma entrega;
ordem liberada sem assinatura não gera nenhuma. Sem o negativo, o positivo passaria por qualquer entrega que
existisse por outro motivo.

**Dois erros meus ao escrevê-los, os dois no teste e nenhum no código:**

- **Filtrei `event_type` pelo nome da enum** (`BREW_ORDER_RELEASED`) quando a coluna guarda o **nome
  externo** (`brew_order.released`). O teste procurava o que não existe e falhava afirmando que a entrega não
  tinha nascido. Guardar o nome externo é o certo: é o que viaja para quem integra, e a tabela registra o que
  foi entregue, não a representação interna.
- **O caso negativo dependia da ordem de execução.** Assinatura é da *cervejaria*, não do pedido, então
  filtrar por ordem não isola nada: a assinatura criada no outro teste sobrevive no mesmo banco. Ele passaria
  ou falharia conforme a ordem, que ninguém controla. Passou a revogar as assinaturas antes de liberar.

### DEB-INT-001 (INT-001) — RESOLVIDO: a leitura do sensor virou ponto na curva

O bloqueio era estrutural e valia registrar: até aqui **nenhum módulo publicava porta de comando** no pacote
raiz, só consultas. Qualquer módulo podia *ler* o lote de outro; nenhum podia *pedir* nada a outro. O sensor
guardava a série dele e quem quisesse ver a curva de fermentação registrava a leitura à mão — com o dado já
dentro do sistema.

**Critério de remoção cumprido**, com três peças:

| Peça | O que resolve |
|---|---|
| `fermentation.FermentationCommands` | a primeira porta de comando publicada do projeto |
| `production.VesselOccupancyLookup` | o elo que faltava: um sensor conhece o tanque, não o lote |
| `sensor…BatchCurveFeed` + adapter | a tradução de vocabulário, no único lugar onde as duas linguagens se encontram |

A ingestão encaminha dentro da própria transação, como o critério pedia: a leitura de telemetria e o ponto na
curva caem juntos ou não caem.

**A porta é estreita de propósito.** Publica um comando, não o módulo. Quem chama não planeja agenda, não
colhe levedura e não avalia estabilidade de FG — essas nascem de decisão humana com ator, alçada e auditoria,
e expô-las a chamada entre módulos criaria caminhos onde alguém age sem que se saiba quem agiu. Registrar
telemetria é o oposto: não tem ator humano. Pelo mesmo motivo **não audita** — 2.880 leituras por dia
encheriam a trilha até esconder o que ela existe para guardar.

**Divergi do critério escrito num ponto, deliberadamente.** Ele dizia encaminhar "a leitura `GOOD`".
Encaminho também a `OUT_OF_RANGE`: a fermentação avalia plausibilidade por conta própria, com as mesmas
faixas, e grava o ponto sinalizado — pelo motivo que o próprio módulo de sensores já defende, de que um
buraco na curva é indistinguível de "não mediu". Filtrar esconderia justamente o sintoma de sensor sujo ou
fora d'água, que é quando olhar a curva mais importa. Já `FUTURE_CLOCK` **não** atravessa, e a assimetria é o
ponto: o instante da medição é parte da chave natural da leitura e é por ele que a curva se ordena. Um valor
absurdo fica visivelmente errado no gráfico; um instante inventado não — ele mente sobre a sequência dos
fatos, que é a única coisa que uma curva serve para contar.

`Measure` (sensor) e `ReadingKind` (fermentation) **seguem separadas**, e a ligação não as uniu: quem traduz
é o adapter. `FLOW` não tem correspondente porque um lote não tem vazão, e é exatamente por casos assim que
compartilhar as enums amarraria a evolução de um módulo à do outro.

### OBS-INT-001 (INT-001) — RESOLVIDO: equipamento inexistente devolve 400 apontando o campo

Achado ao escrever o IT da jornada de telemetria, semeando um `equipmentId` aleatório:
`sensor_device.equipment_id` tem chave estrangeira para `equipment`, e a violação subia crua como **500**.
Quem integra pelo API lia "erro do servidor" para um problema no dado que ele mesmo mandou.

Passou a verificar antes, pela consulta publicada do módulo de equipamentos, e a responder **400** com
Problem Details e `field: equipmentId`.

**400 e não 404, apesar de ser "não encontrado".** O recurso da requisição é o dispositivo sendo criado, e
ele não existe mesmo — o que está errado é um *campo do corpo*. Responder 404 faria quem integra concluir
que a rota está errada e procurar o problema onde ele não está.

**A verificação prévia não substitui a restrição do banco**, e não deve: duas requisições simultâneas
passariam as duas por uma checagem prévia. Ela traduz o erro; quem garante continua sendo a chave
estrangeira.

O `field` na resposta é a única informação acionável — sem ele, "equipamento inexistente" num corpo com meia
dúzia de campos ainda deixa a pessoa adivinhando qual conserta.

### DEB-INT-001 (INT-001) — RESOLVIDO: a leitura do sensor virou ponto na curva

O bloqueio era estrutural e valia registrar: até aqui **nenhum módulo publicava porta de comando** no pacote
raiz, só consultas. Qualquer módulo podia *ler* o lote de outro; nenhum podia *pedir* nada a outro. O sensor
guardava a série dele e quem quisesse ver a curva de fermentação registrava a leitura à mão — com o dado já
dentro do sistema.

**Critério de remoção cumprido**, com três peças:

| Peça | O que resolve |
|---|---|
| `fermentation.FermentationCommands` | a primeira porta de comando publicada do projeto |
| `production.VesselOccupancyLookup` | o elo que faltava: um sensor conhece o tanque, não o lote |
| `sensor…BatchCurveFeed` + adapter | a tradução de vocabulário, no único lugar onde as duas linguagens se encontram |

A ingestão encaminha dentro da própria transação, como o critério pedia: a leitura de telemetria e o ponto na
curva caem juntos ou não caem.

**A porta é estreita de propósito.** Publica um comando, não o módulo. Quem chama não planeja agenda, não
colhe levedura e não avalia estabilidade de FG — essas nascem de decisão humana com ator, alçada e auditoria,
e expô-las a chamada entre módulos criaria caminhos onde alguém age sem que se saiba quem agiu. Registrar
telemetria é o oposto: não tem ator humano. Pelo mesmo motivo **não audita** — 2.880 leituras por dia
encheriam a trilha até esconder o que ela existe para guardar.

**Divergi do critério escrito num ponto, deliberadamente.** Ele dizia encaminhar "a leitura `GOOD`".
Encaminho também a `OUT_OF_RANGE`: a fermentação avalia plausibilidade por conta própria, com as mesmas
faixas, e grava o ponto sinalizado — pelo motivo que o próprio módulo de sensores já defende, de que um
buraco na curva é indistinguível de "não mediu". Filtrar esconderia justamente o sintoma de sensor sujo ou
fora d'água, que é quando olhar a curva mais importa. Já `FUTURE_CLOCK` **não** atravessa, e a assimetria é o
ponto: o instante da medição é parte da chave natural da leitura e é por ele que a curva se ordena. Um valor
absurdo fica visivelmente errado no gráfico; um instante inventado não — ele mente sobre a sequência dos
fatos, que é a única coisa que uma curva serve para contar.

`Measure` (sensor) e `ReadingKind` (fermentation) **seguem separadas**, e a ligação não as uniu: quem traduz
é o adapter. `FLOW` não tem correspondente porque um lote não tem vazão, e é exatamente por casos assim que
compartilhar as enums amarraria a evolução de um módulo à do outro.

### OBS-INT-001 (INT-001) — Equipamento inexistente no cadastro de dispositivo devolve 500

Encontrado ao escrever o IT da jornada, semeando um `equipmentId` aleatório: `sensor_device.equipment_id` tem
chave estrangeira para `equipment`, e a violação sobe como **500**, não como 400. Quem integrar pelo API vai
ler "erro do servidor" onde o problema é o dado enviado.

Não corrigi junto porque é outra história — tratamento de erro do cadastro de dispositivo, não a ligação com
a curva —, e emendar aqui misturaria as duas no mesmo PR. **Critério de remoção:** validar a existência do
equipamento no caso de uso e responder 400 com Problem Details apontando o campo.

## Evidências de encerramento

- **Build/commit:** `mvnw verify` verde; `eslint` e `ng build` limpos; E2E contra API real
- **Testes executados (INT-001):** 40 unitários novos; `SensorIngestionIT` 23/23 com PostgreSQL 18 real,
  incluindo **oito requisições simultâneas** com a mesma identidade de mensagem (1×201 + 7×200, uma linha);
  frontend 12 novos; `sensors.spec.ts` 4/4
- **Testes executados (INT-002):** 28 unitários novos; `WebhookIT` 13/13 — **rollback do outbox provado**,
  restrição única, isolamento, alçadas assimétricas, auditoria sem caminho nem segredo; frontend 11 novos;
  `webhooks.spec.ts` 5/5
- **Testes executados (PWA-001):** 18 novos de frontend; `offline-runbook.spec.ts` com offline real via
  `context.setOffline` e logout esvaziando o armazenamento; um teste afirma que o gravado **não contém**
  custo, preço, fornecedor, CPF ou e-mail
- **Testes executados (PWA-002):** 23 novos de frontend; `MeasurementIT` 7/7 com 3 casos novos de
  idempotência do apontamento offline
- **Testes executados (INT-003):** 11 unitários; `ScanIT` 9/9 — sem a permissão do tipo é 403, e a
  permissão é *do tipo* e não genérica; `scan.spec.ts` 6/6
- **Testes executados (INT-006):** 12 unitários; 5 casos novos no `SensorIngestionIT`, incluindo que a
  idempotência sobrevive ao adapter
- **Testes executados (SEC-B07):** 29 unitários (`SsoHandshakeTest` 8, `AccountLinkDecisionTest` 9,
  `SsoLoginHandlerTest` 12); `SsoLoginIT` 7/7 — endpoints públicos, verificador PKCE que não viaja na URL,
  e **uso único decidido pelo banco**
- **Migrations aplicadas:** `V98__sensor_ingestion.sql`, `V99__integration_webhooks.sql`,
  `V100__production_client_request_id.sql`, `V101__sensor_payload_format.sql`, `V102__sso_handshake.sql`
- **Contratos atualizados:** `contracts/openapi.yaml` (`/sensors/**`, `/integration/webhooks*`,
  `/integration/scan`, `clientRequestId` em `RecordMeasurement`) e `contracts/security.openapi.yaml`
  (`/security/sso/**`)
- **Riscos remanescentes:** dois, ambos declarados e com critério de remoção —
  **`DEB-INT-003`** (transporte MQTT não entregue) e **`DEB-SEC-001`** (troca com IdP real não exercitada).
  Os demais débitos estão registrados acima.
- **Aceite:** **5 histórias completas** (INT-001, INT-002, PWA-001, PWA-002, INT-003) e **2 parciais com
  pendência declarada** (INT-006 sem MQTT, SEC-B07 sem a troca real). Nenhuma pendência foi escondida em
  TODO: as duas têm identificador, motivo e critério de remoção. **A decisão de aceitar a sprint com as duas
  parciais, ou de abrir histórias próprias para elas, é do mantenedor.**
