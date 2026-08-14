# Status — Sprint 17

Estado: EM EXECUÇÃO — REL-002, REL-003 e REL-004 concluídas; REL-001 fora de escopo por decisão do
mantenedor; REL-005 com o manual mínimo entregue e o ciclo em homologação pendente.

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| REL-001 | **Fora de escopo** (decisão do mantenedor) | — | — | O ensaio de restauração foi removido do repositório a pedido do mantenedor. A história **não fecha** como especificada — não há RPO/RTO medidos. Ver DEC-REL-008. |
| REL-002 | Concluída | Claude | `infra/perf/*`, `ListBatchesUseCase`, `JdbcBatchRepository`, `PageResponse` | Gargalo medido **e corrigido**: com 3.000 lotes, p95 caiu de 319 ms para 9,8 ms. Ver DEC-REL-007. |
| REL-003 | Concluída | Claude | `InternalAddressGuard`, `SecurityConfiguration`, `.github/workflows/ci.yml`, `frontend/package-lock.json` | Um achado ALTO (SSRF no webhook) e dois médios resolvidos; varredura de CVE passou a barrar merge. Ver DEC-REL-001/002/003. |
| REL-004 | Concluída | Claude | `infra/runbooks/deploy-rollback.md` | Ensaio executado em 2026-08-10: bloqueio de escrita medido migration a migration (`V100` = 143 ms) e retorno do artefato anterior contra o schema novo exercitado de verdade. Ambiente local, não cópia de produção — limitação registrada. Ver DEC-REL-006/009. |
| REL-005 | Parcial — manual entregue, ciclo pendente | Claude | `docs/44_MINIMUM_OPERATING_MANUAL.md` | O entregável que não depende de ambiente está pronto, com o roteiro de homologação e a evidência exigida por etapa. O ciclo em homologação continua aberto: depende do ambiente e de quem opera. Ver DEC-REL-010. |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

### DEC-REL-001 (REL-003) — ALTO: SSRF no destino do webhook, corrigido

**O achado.** `WebhookSubscription` exigia HTTPS mas não olhava para onde o endereço aponta. Quem tem
permissão de cadastrar webhook conseguia usar o servidor como sonda da rede interna: aponta para
`https://10.0.0.5/admin` ou para o serviço de metadados da nuvem, e lê o resultado pelo **status gravado na
entrega**. Não precisa ver o corpo — o código de resposta e a diferença entre "recusou conexão" e
"respondeu 401" já mapeiam a rede. O serviço de metadados é o alvo mais valioso porque costuma entregar
credencial sem autenticação.

**A correção** (`InternalAddressGuard`) recusa loopback, link-local, redes privadas, multicast, IPv6 de uso
local e a faixa 100.64.0.0/10, com três decisões que valem registro:

- **No envio, não no cadastro.** Validar só na criação seria contornável por DNS: o nome resolve para
  endereço público hoje e para `127.0.0.1` amanhã — *rebinding*. O custo é uma resolução que a requisição
  faria de qualquer jeito.
- **Recusa se QUALQUER endereço resolvido for interno.** Um nome que devolve um público e um privado
  passaria por uma checagem que olha só o primeiro, e quem escolhe qual usar é a pilha de rede.
- **A mensagem não repete o endereço resolvido.** Ela fica gravada na entrega e é legível por quem
  cadastrou — devolver o IP seria entregar exatamente a resposta que a checagem nega.

Nome que não resolve **não** é recusado: é destino inalcançável, e quem trata isso é o backoff. Confundir
os dois tornaria indistinguível "o DNS caiu" de "tentaram sondar a rede".

`followRedirects(NEVER)` já existia e continua sendo o que impede a segunda metade do ataque.

**Alerta para depois:** quando `DEB-SEC-001` (troca real de token com IdP) for implementado, ele fará uma
requisição de servidor para uma URL configurada por cervejaria — a mesma superfície, e precisará da mesma
guarda.

### DEC-REL-002 (REL-003) — `/actuator/prometheus` era público

Exposto e `permitAll` em todos os perfis, inclusive produção. O corpo não tem dado de negócio, mas tem o
inventário completo de rotas (label `uri`), volume de tráfego, taxa de erro por endpoint e pressão do pool
do banco — reconhecimento de graça para quem procura por onde entrar, e um oráculo de disponibilidade para
quem já entrou. Passou a exigir autenticação, junto com `/actuator/metrics/**`.

`/actuator/health/**` e `/actuator/info` seguem públicos: sondas de liveness precisam responder sem
credencial, e `show-details` está no padrão `never`.

**Consequência operacional, que entra no runbook de REL-004:** o coletor Prometheus passa a precisar de
credencial. Uma conta de serviço com API key atende.

### DEC-REL-003 (REL-003) — Dependências: uma alta corrigida, e o CI passou a barrar

`npm audit` apontava 5 vulnerabilidades, 1 alta (`nanoid`). **Todas as cinco estavam sob `@angular/build` e
`@angular/cli`** — ferramenta de build, nada que chegue ao navegador. A severidade real é bem menor que a
reportada, e registrar isso importa: tratar as duas coisas como equivalentes é o que faz um relatório de
vulnerabilidade virar ruído. Ainda assim foram corrigidas, porque o custo era um `npm audit fix` que mexeu
só no lock. Sobram 3 moderadas que exigiriam downgrade do Angular CLI.

A lacuna maior era de **processo**: o Dependabot abre PR de atualização, mas atualizar é sugestão — nada
impedia uma dependência com CVE conhecido de ser mergeada. Foi acrescentado o job `dependency-review`, que
falha o PR em severidade alta ou crítica. Roda só em `pull_request` porque compara duas árvores; num push
para `main` não há base de comparação, e um job que sempre passa por falta de entrada é pior que job nenhum.

### OBS-REL-001 (REL-003) — RESOLVIDO: o filtro por cervejaria saiu da disciplina e virou barreira

A varredura encontrou **dez escritas** tocando tabela com `brewery_id` filtrando só por id. Nenhuma era
vulnerabilidade — todas tinham guarda no handler. Mas a garantia morava na disciplina de cada handler
lembrar de carregar a entidade com escopo antes, e um handler novo que receba o id do path e chame o
repositório direto vaza entre cervejarias sem nada reclamar.

**Nove receberam `AND brewery_id = :brewery`**, com o id propagado do handler — que já o tinha, porque toda
operação nasce de um principal com cervejaria ativa. Duas nem exigiram mudar assinatura: o agregado já
carregava a cervejaria.

**A décima foi removida.** `federation_provider.update` não tinha chamador nenhum. Corrigir uma escrita sem
escopo que ninguém usa seria deixar o caminho errado pronto para quem precisar daquilo depois.

**A outra metade é o `TenantIsolationTest`.** Consertar os dez resolveria hoje e não amanhã. O teste varre
todo SQL do código contra as tabelas com `brewery_id` extraídas das migrations e falha se alguma escrita
não filtrar — transformando a checagem que fiz uma vez, na mão, em barreira de cada build.

Verifiquei que ele **pega**: removi o filtro de um repositório, o teste falhou apontando arquivo, tabela e
a instrução inteira, e restaurei. Um teste que nunca falhou não é barreira, é decoração.

Duas decisões nele:

- **Falha se extrair menos de 100 tabelas.** Se a regex quebrar numa migration futura, o teste passaria
  vazio — e teste que passa por não ter olhado nada é pior que teste nenhum, porque parece cobertura.
- **A mensagem de falha diz o que fazer**, inclusive no caso da tabela filha, onde a tentação é confiar que
  o pai foi carregado com escopo.

**O que ele NÃO garante**, escrito no próprio Javadoc: que o `brewery_id` usado seja o da sessão. Um
repositório que receba o id errado passa. Contra isso o que existe são os ITs de "outra cervejaria não
enxerga" que cada módulo já tem.

**Erro meu no caminho:** o script de edição usava `replace(..., 1)`, que aplica na primeira ocorrência do
arquivo — não na do método certo. Em dois repositórios o `WHERE` recebeu `:brewery` e o `.param` foi parar
noutro método; resultado, 500 no `publish` e três ITs vermelhos. Corrigido ancorando pelo bloco do método.

### O que foi verificado e estava correto

Não gerou achado, e vale registrar para a próxima revisão não refazer: **autorização em todos os endpoints**
(as exceções — login, recuperação, callback SSO, `ServiceProviderConfig` do SCIM — são `permitAll` por
desenho e documentadas); **XXE fechado** no único ponto que faz parse de XML (`disallow-doctype-decl` no
BeerXML); **cookies** com `HttpOnly`, `Secure` e `SameSite`; **limitação de tentativas de login** por e-mail
e IP; **segredos** vindos de variável de ambiente, sem valor padrão embutido; **redirecionamento de webhook**
recusado.


### DEB-REL-001 — RESOLVIDO: `dependency-review` voltou a barrar

O job acrescentado por `DEC-REL-003` falhou no próprio PR que o introduziu, e não por ter encontrado CVE:
a action exige o recurso *Dependency graph*, que estava desabilitado. Ficou `continue-on-error: true` para
não travar todo PR por um motivo que nenhuma mudança de código resolve.

**Critério de remoção cumprido.** Com autorização explícita, foram habilitados via API:

| Recurso | Estado |
|---|---|
| Dependency graph (via alertas do Dependabot) | habilitado |
| `secret_scanning` | habilitado |
| `secret_scanning_push_protection` | habilitado |
| `dependabot_security_updates` | habilitado |
| `secret_scanning_non_provider_patterns` | **indisponível** — exige Advanced Security |
| `secret_scanning_validity_checks` | **indisponível** — exige Advanced Security |

As duas últimas merecem nota: a API responde 200 e **ignora** o pedido em silêncio, mantendo `disabled`.
Quem só olhasse o código de resposta concluiria que habilitou. A conferência foi pelo estado devolvido,
não pelo sucesso da chamada.

O `continue-on-error` foi removido: o job volta a barrar PR com dependência de severidade alta, que era o
ponto dele. Este próprio PR é o teste — se a action ainda não funcionasse, ele não passaria.

**Push protection** é a adição de maior valor prático: o `gitleaks` no CI pega segredo que já foi enviado;
push protection recusa antes de o commit chegar ao servidor, que é o único momento em que ainda não houve
vazamento.

### DEC-REL-006 (REL-004) — Runbook escrito em torno de uma regra: o banco não volta

`docs/20_RELEASE_MIGRATION.md` define expand/contract, e a consequência operacional é que *rollback* é
sempre da **aplicação**. Um runbook que promete desfazer migration promete o que não vai cumprir no dia
em que precisar.

Contém árvore de decisão (rollback / forward-fix / restaurar), o que observar no ensaio de lock
(`ADD CONSTRAINT` sem `NOT VALID` varre a tabela inteira e bloqueia escrita; `CREATE INDEX` sem
`CONCURRENTLY` idem), e a regra que só dói no incidente: **migration publicada não é editada** — o
checksum diverge e o Flyway recusa subir, em produção, no meio do problema.

Registra também o pré-requisito criado por `DEC-REL-002`: o coletor de métricas precisa de credencial,
senão o painel fica cego exatamente durante o deploy.

**O que falta para REL-004 fechar:** uma linha na tabela de registro de ensaios. Tabela vazia é estado
honesto — significa que nenhum ensaio foi feito. **Preenchida em 2026-08-10 — ver DEC-REL-009.**

### DEC-QLT-001 (QLT-001-A) — O bloqueio estava desatualizado, e só uma das quatro cadências é julgável

Decisão do mantenedor em 2026-08-14: **alerta, não bloqueio**. Parar a produção por um controle atrasado
pararia a fábrica por causa de uma medição, e quem opera passaria a burlar a regra em vez de cumpri-la. O
aviso entra na central do lote, onde o desvio grave (QLT-001) e a etapa atrasada (FER-004) já aparecem —
uma segunda central seria um segundo lugar para ninguém olhar.

**O débito estava aberto por leitura antiga.** O critério pedia "existir agendador na plataforma", e ele
existe desde a Sprint 13. Vale como lembrete: débito descreve o mundo do dia em que foi escrito, e é a
terceira vez nesta leva que reler o código encurtou o trabalho.

**Só uma das quatro cadências é julgável por relógio, e isso ficou no tipo.** `PER_HOURS` é cobrada;
`PER_BATCH` só está atrasada quando o lote acaba sem a medição — não há instante *durante* o lote em que
ela esteja; `PER_SHIFT` exigiria um calendário de turnos que a plataforma não tem, e inventá-lo (8 h a
partir da meia-noite?) produziria atraso onde a cervejaria não vê atraso nenhum; `PER_PACKAGING_RUN` se
refere à corrida de envase, não ao lote em produção. As três continuam declaradas e não fiscalizadas — a
diferença é que agora isso está dito, com o porquê, e há teste afirmando que elas não alertam.

**Uma consulta publicada nova.** As consultas de produção respondiam por *um* lote de cada vez, o que
serve a quem já tem o identificador e não a quem procura o que está atrasado. `OpenBatchLookup` lista os
lotes abertos — e só os abertos: cadência não se cobra de lote encerrado, porque a medição que faltou já
não pode ser feita e o aviso viraria ruído permanente.

**O aviso não se repete, e a chave é a janela perdida.** Sem memória, a varredura de hora em hora
avisaria de novo a cada hora sobre o mesmo atraso — central que repete 24 vezes por dia é central que
ninguém lê. Enquanto ninguém mede, a janela perdida continua sendo a mesma, então o aviso é **um só**.
Depois que alguém mede, a janela passa a contar dali: atrasar outra vez é fato novo e avisa de novo. Os
dois casos têm teste, e o segundo só existe porque o primeiro teste que escrevi estava errado sobre a
semântica — o código estava certo.

**O ator é o sistema**, com identificador fixo: não há pessoa por trás de uma varredura, e emprestar o
nome de alguém faria a trilha dizer que um humano avisou.

### DEC-PKG-002 (PKG-004-B) — O medido vence o calculado, e ABV coube num tipo que já existia

Decisão do mantenedor em 2026-08-14: manter o "calculado, não medido" e acrescentar ABV medido, que
prevalece quando existe.

**Diferente do critério de remoção, e de propósito.** Ele propunha recalcular o ABV de OG medido e FG
estável. Isso produziria uma **terceira** estimativa — melhor que a da receita, ainda assim uma conta. O
que a legislação cobra no rótulo é o valor medido, e uma cervejaria que mede em laboratório não tinha onde
guardá-lo.

**Coube num tipo que já existia.** ABV é grandeza medida do lote, como cor e amargor, que são
`MeasurementKind` desde a V52. Entrando ali, herda quem pode registrar, o rastro de quem registrou, a
carta de controle e a série histórica. Um campo próprio criaria um segundo caminho para registrar medição,
com outra permissão, outra auditoria e outra tela, para a mesma coisa.

**O que a implementação revelou:** a medição de lote exigia status `IN_PROGRESS` — e ABV se mede na cerveja
**pronta**, depois de fermentar. A regra existe por bom motivo (acrescentar temperatura de mostura a um
lote encerrado descreveria um dia que já acabou), então virou regra por grandeza em vez de cair: brassa
exige lote em andamento, ABV não. Sem isso, a medição seria inexprimível justamente no momento em que ela
existe.

**Dois detalhes que só aparecem imprimindo:**

- A unidade é `%ABV`, não `%`. Porcentagem de quê separa álcool por volume de álcool por massa, e as duas
  circulam em rótulo pelo mundo. (A coluna `unit` é `VARCHAR(8)`, o que descartou `ABV_PERCENT` — e a
  notação curta, que é a impressa no rótulo, acabou sendo a melhor das duas.)
- O valor sai com `stripTrailingZeros`: a coluna guarda `5.4000` e a lata precisa dizer `5.4`.

**A remedição vale.** Mede-se ABV uma vez, e quando se mede de novo é porque a primeira estava errada — o
rótulo leva a última.

### DEC-PKG-001 (PKG-002-A) — A pressão que importa é a de equilíbrio, e ela existia calculada do lado errado

Decisão do mantenedor em 2026-08-11: cadastrar pressão máxima por embalagem e bloquear alvo acima dela.

**O que a plataforma já sabia, e onde não usava.** A pressão de equilíbrio só era calculada para a
carbonatação **forçada**, onde ela é o valor a aplicar no cilindro. Mas é a mesma conta para o priming: a
física não pergunta se o CO₂ veio de açúcar ou de gás — dada uma quantidade de volumes numa temperatura, a
pressão é aquela. Passou a ser calculada nos dois métodos, e é ela que se compara ao limite.

**E o caso perigoso é justamente o priming.** Garrafa com açúcar demais é a bomba clássica; carbonatação
forçada é limitada pelo regulador. O débito descrevia o sistema como bloqueando "o caso claro e deixando
passar o perigoso", e era literal.

**Recusa, não alerta.** Alerta é lido no dia em que a linha está atrasada, e a consequência de ignorá-lo é
alguém se machucando. É o único caso desta leva de decisões em que a consequência é física, e por isso é o
único que bloqueia em vez de avisar.

**A temperatura viaja com a recusa.** A conta vale na temperatura informada, e estocagem mais quente sobe a
pressão. Sem esse número na resposta, quem opera baixa o alvo até passar e guarda a caixa num galpão a
40 °C. Quando o alvo passa, o alerta diz a pressão contra o limite pelo mesmo motivo.

**Ausência declarada.** Sem `maxPressureBar` cadastrado, um alerta diz que **nada foi conferido** — é
melhor que quem opera saiba que a checagem não aconteceu do que suponha que aconteceu. O débito volta a
existir, na prática, para toda embalagem sem o dado; a diferença é que agora ele fala.

### DEC-FDS-002 (FDS-004-A) — A porta ficou na direção contrária à do critério, e o teste mandou

Decisão do mantenedor em 2026-08-11: a ação corretiva do simulado vira item de CAPA. O critério de
remoção dizia "o CAPA publicar porta de abertura de ação" — e foi exatamente isso que **não** deu certo.

**O ciclo que a primeira tentativa criou.** Publiquei `quality.CapaActionOpening` e chamei do
encerramento do simulado. O `ModularityTest` recusou: `quality` depende de `production` desde a QLT-001
(alerta de lote), `production` depende de `traceability` (implementa `LineageSource`), e a aresta nova
fechou o ciclo. A solução foi inverter — `traceability.CorrectiveActionSink` declarada na rastreabilidade
e implementada pela qualidade, exatamente como o `LineageSource` já fazia. Quem depende é quem implementa,
e a rastreabilidade continua sem saber que CAPA existe.

**Um efeito colateral necessário:** a checagem de existência do lote na abertura de NC (DEB-AIA-003, de
horas antes) saiu do handler e virou **chave estrangeira**. Ela era a outra ponta da dependência
`quality → production`. A troca melhora o que já estava lá: checagem prévia não é garantia — duas
requisições simultâneas passariam as duas, e um lote cancelado entre a checagem e o INSERT deixaria a NC
apontando para o nada.

**O simulado não abre a NC sozinho.** Isso exigiria o sistema decidir a severidade, e o quanto uma
cobertura de 75% é grave depende do produto e de quem audita. Quem encerra escolhe uma NC entre as que
estão prontas para receber ação.

**E não fura a ordem das fases do CAPA.** Descobri implementando: `planAction` recusa NC que ainda não foi
investigada. Está certo — planejar solução antes de conhecer a causa é o que o CAPA existe para impedir —
e o simulado se submete à regra em vez de contorná-la. A tela diz isso quando não há NC investigada, em
vez de mostrar uma lista vazia sem explicação.

**Texto e CAPA são excludentes**, garantido por `CHECK` e por teste: os dois juntos deixariam quem lê o
relatório sem saber qual é a ação de verdade — a escrita no texto, sem dono nem prazo, ou a que tem os
dois.

**Por que o texto não bastava**, que é a razão do débito existir: "revisar contatos dos distribuidores"
escrito no relatório de um simulado não tem dono, não tem prazo e não aparece em lista nenhuma. Seis meses
depois, o próximo simulado encontra a mesma lacuna e o relatório anterior está lá dizendo o que fazer —
que é a definição de um exercício que não melhora nada.

### DEC-FDS-001 (FDS-003-A) — Estorno, e só estorno: o resto continua sendo comercial

Decisão do mantenedor em 2026-08-11. O critério de remoção do débito apontava para as sprints 19/20
definirem movimentação comercial — **e fechá-lo não precisou disso**. Das três lacunas registradas
(devolução, cancelamento e transferência entre destinos), só uma é urgente e nenhuma parte dela é
comercial: a expedição digitada errada.

**Por que não podia esperar.** Um erro de digitação contamina o recall, que é onde o dado precisa estar
certo. 200 unidades registradas para o distribuidor errado fazem o simulado medir cobertura sobre um
destino que nunca recebeu nada, e fazem o saldo sem destino do lote mentir **para menos** — escondendo
cerveja que ninguém sabe onde está. Devolução e transferência continuam fora: dependem de cliente e
pedido, que é o que a Sprint 12 se recusou a inventar.

**Estorno não apaga, e é o `AGENTS.md` que manda.** A linha permanece marcada. Apagar tornaria
indistinguível "nunca houve expedição" de "houve e foi estornada" — e a segunda precisa ser demonstrável,
inclusive para quem recebeu a comunicação de um recall baseado nela. Na tela ela aparece riscada, com o
motivo ao lado.

**O efeito no recall é consequência, não passo.** As consultas que o alimentam passaram a olhar só
expedições vivas; nada é recalculado. Há teste de integração que monta o caso inteiro: com a expedição
errada valendo, o escopo soma 160 unidades e dois destinos; estornada, volta a 120 e um destino — e a
linha continua na listagem.

**O motivo é obrigatório e o domínio recusa evasiva curta.** Sem conteúdo, o histórico mostraria uma
expedição que deixou de valer sem dizer se foi digitação, destino trocado ou carga que não saiu — e as
três exigem reações diferentes de quem investiga.

**Alçada própria (`packaging.shipment.reverse`), não crítica.** Registrar é o trabalho do dia e muita
gente faz; estornar desfaz um destino que pode já ter sido comunicado. Não é crítica de propósito:
dificultar demais o estorno empurraria quem opera a conviver com o dado errado, que é o problema original.

**Erro meu no caminho:** a migration reusou um id de permissão já existente (`...131`) e derrubou o
contexto inteiro dos testes com violação de chave primária. O `ON CONFLICT (code)` não cobre isso — a
chave primária é o id. Corrigido para o próximo livre da sequência.

### DEC-AIA-001 (DEB-AIA-003) — O copiloto abre NC, e um terço do débito já tinha caído sozinho

Decisão do mantenedor em 2026-08-11: **a NC passa a referenciar lote, e os prazos vêm da severidade** pela
política da casa. Registro completo em `sprints/sprint-14-ai-rag/STATUS.md`.

**O achado que encurtou o trabalho.** O débito listava duas barreiras; a segunda — "os três prazos são
`NOT NULL` e não vêm na proposta" — **já não existia**. A PRM-001 criou `quality_capa_policy` depois do
débito ser escrito, e desde então a abertura já derivava os prazos da severidade. Débito antigo descreve o
mundo do dia em que foi escrito, e vale reler o código antes de aceitar o diagnóstico.

**O que não entrou na porta publicada é o registro que importa.** `NonConformityOpening` não recebe prazo,
nem código, nem status: prazo sai da política, código é numerado pelo sistema (`NC-AAAA-NNNN`, por ano,
porque é assim que se cita NC em auditoria), e NC nasce aberta. Qualquer um dos três entrando por ali
deixaria um chamador — inclusive a IA — decidir o que a cervejaria decidiu uma vez, na tela de parâmetros.

**Sem política, o aceite falha, e há teste afirmando isso.** A proposta continua `PENDING` e nenhuma NC é
criada. Um default de prazos embutido pareceria conveniência e viraria o prazo que ninguém escolheu.

### DEC-CLN-001 (CLN-004-A) — O evento tinha oito sprints sem consumidor, e o consumidor virou porta

Débito aberto na Sprint 08: `CleaningCycleReleased` era publicado e ninguém escutava. **A metade que
faltava não era o listener — era o estado que ele atualizaria.** Sem estado, a plataforma afirmava
condicionar o uso à sanitização e só cumpria isso no envase, que consultava a última liberação por conta
própria. Um fermentador recebia cerveja logo depois de esvaziar o lote anterior sem ninguém perguntar nada.

**A regra, em três movimentos:** usar suja o tanque; tanque sujo recusa a próxima cerveja (409
`equipment_not_clean`); ciclo verificado e liberado limpa. Vale tanto para a transferência do dia de
brassa quanto para o enchimento vindo de um blend — encher um tanque com cerveja é a mesma coisa, venha
ela de onde vier.

**Não existe "marcar limpo".** Existe "foi limpo por este ciclo", e o ciclo é obrigatório no contrato e no
tipo. A diferença não é de nome: um método sem ciclo seria o caminho usado no dia de correria, e "limpo"
passaria a significar "alguém clicou" em vez de "há evidência de sanitização, com concentração,
temperatura e ATP medidos". Um teste afirma que concluir o ciclo não limpa — só liberar limpa, porque
concluir é ter feito, e liberar é ter conferido que funcionou.

**O listener virou porta, e o `ModularityTest` foi quem mandou.** A primeira implementação foi um
`@TransactionalEventListener` em `equipment`, exatamente como o débito previa em 2026 — e criou **ciclo
entre módulos**: `sanitation` já dependia de `equipment` desde a CLN-003. Invertida a direção
(`EquipmentCleanlinessCommands`, chamada pela liberação), a dependência ficou numa direção só. O ganho não
foi só arquitetural: dentro da transação da liberação, deixou de existir a janela em que um ciclo aparece
liberado com o tanque ainda sujo — e o estado deixou de depender de alguém ter registrado um listener.

**Equipamento novo nasce limpo.** Exigir ciclo antes do primeiro uso obrigaria a registrar a limpeza de um
tanque recém-chegado, e é assim que se ensina alguém a burlar a regra.

**Sujar de novo não renova a data.** É a data antiga que denuncia o tanque parado sujo há três semanas —
que é um problema pior que o tanque esvaziado hoje de manhã, e ficaria escondido atrás de um uso recente.
Por isso a resposta do 409 traz `soiledSince`: sem ele, "tanque sujo" manda todo mundo fazer a mesma coisa
em dois casos que pedem reações diferentes.

**Limpeza não é perfil.** O estado mora na tabela de equipamento e tem repositório próprio: passar pelo
`EquipmentRepository.update` geraria uma revisão de perfil a cada tanque esvaziado, enchendo o histórico
de mudanças que não mudaram medida nenhuma. E a versão otimista do perfil não é tocada — limpar um tanque
não pode fazer a edição de capacidade de outra pessoa falhar por conflito.

**A listagem carrega o estado em bloco**, não item a item: é a lição da REL-002, onde o N+1 estava
invisível porque morava no mapeador.

### DEC-DEBT-001 — Quatro débitos fechados por decisão, sem código

Em 2026-08-11 o mantenedor decidiu catorze débitos de uma vez. **Dez viram trabalho**; estes quatro
fecham aqui, porque a resposta certa era decidir, não implementar. Débito sem decisão fica aberto para
sempre e vai apodrecendo na lista até ninguém mais lembrar do que se tratava.

**`FER-002` — as faixas de plausibilidade ficam como estão.** SG 0,980–1,180; −10 a 45 °C; 0–60 psi;
pH 2,5–7,5, fixas no domínio. Cobrem qualquer cerveja com folga larga, e a pergunta que a sprint 09
deixou em aberto ("estão certas para a operação real?") foi respondida: estão. Torná-las configuráveis
seria dar a alguém a chance de afrouxar a checagem até ela não recusar nada — que é o mesmo que não ter
checagem. Se um dia uma cervejaria precisar de faixa fora disso, vira história própria, com o caso
concreto na mão.

**`GAS-001-A` — custo e estoque do CO₂ ficam adiados, agora de propósito e por escrito.** Já tinham sido
adiados na sprint 13 por falta de tempo; a diferença é que agora é decisão. O CO₂ é fração pequena do
custo do litro e o modelo é caro de acertar — cilindro, comodato, retorno de vasilhame, perda por
vazamento. Entra quando alguém olhar o custo do lote e reclamar da ausência dele; até lá, o `UTL-001`
mede consumo, que é a metade que importa para quem opera.

**`MTR-001-B` — não vai ser feito, e o débito fecha.** "Aprovado com restrição" não vai estreitar a faixa
do instrumento automaticamente, porque a restrição é texto livre num certificado, e interpretá-la exigiria
inventar semântica em cima do que um metrologista escreveu em português. Um sistema que adivinha a
restrição errada é pior que um que não adivinha: ele produz uma faixa que parece conferida. O caminho
correto continua sendo humano — quem lê o certificado bloqueia o instrumento ou registra a faixa nova à
mão, e as duas ações já existem.

**`RPT-003-A` — a plataforma registra a entrega e não envia, e continua assim.** Enviar exige SMTP
configurado por cervejaria, tratamento de bounce, lista de destinatários e a conversa de LGPD que vem
junto com mandar dado de produção para fora por e-mail. O relatório agendado que fica disponível na
plataforma é honesto sobre o que faz. O débito fecha como decisão, não como pendência.

### DEC-REL-009 (REL-004) — O ensaio foi feito, e mediu o bloqueio em vez do tempo

Ensaio executado contra PostgreSQL 18.4 local isolado, baseline em `V97`, dataset de 1,5 milhão de medições
e 1 milhão de eventos de auditoria, migrations `V98`→`V109` aplicadas uma por vez com sonda de escrita
concorrente. Registro completo em `infra/runbooks/deploy-rollback.md`.

**A medida que o runbook não tinha e passou a ter.** Tempo de migration e tempo de escrita bloqueada são
grandezas diferentes, e a segunda é a que descreve indisponibilidade. Onze das doze migrations ficaram no
ruído; a `V100` parou a escrita em `production_measurement` por **143 ms**.

**O achado que muda uma crença.** O custo da `V100` não está no `ADD COLUMN`, está no índice único
**parcial** — que nasce vazio, porque o predicado exclui todas as linhas existentes, e mesmo assim varre a
tabela inteira para descobrir isso, com lock que bloqueia escrita durante toda a varredura. "Índice parcial
é barato" vale para o que ele grava, não para o que ele custa criar. Em 1,5 M de linhas são 143 ms; em 15 M
seriam ~1,4 s, e aí `CONCURRENTLY` deixa de ser luxo. Não é dívida desta release — é critério para a próxima
migration que indexar essa tabela.

**O retorno da aplicação foi exercido, não deduzido.** O artefato anterior (`bcdbd09`, 97 migrations) subiu
contra o schema em `V109` em 9,8 s, com `ddl-auto: validate` passando, autenticou e serviu
`GET /api/v1/production/batches` com 200. É a promessa central do expand/contract medida em vez de afirmada.
Dois detalhes que só aparecem fazendo: o Flyway emite **WARN e prossegue** diante de um schema à frente do
artefato (quem ler isso no meio do incidente vai achar que é a causa), e o rollback restaura o comportamento
antigo **inteiro** — inclusive a listagem sem paginação que a `REL-002` corrigiu.

**Os limites estão registrados junto com os números:** contêiner local e não cópia de produção, uma escrita
por vez em vez de concorrência real, e banco ocioso durante o rollback. A linha da tabela vale pelo que
mediu, não como carimbo.

### DEC-REL-010 (REL-005) — O manual descreve a sequência, não as telas; e o roteiro veio antes do ciclo

O critério pede três coisas — evidências anexadas, bloqueadores encerrados e manual mínimo entregue. Só a
terceira não depende de ambiente, e é a que este registro fecha.

**O manual não cataloga campos.** Rótulo e validação já estão na tela, e repeti-los aqui criaria uma segunda
fonte que envelhece na primeira mudança de layout — com o agravante de que a cópia desatualizada *parece*
autoridade. O que o manual traz é a **sequência e o porquê de cada porta**: que ordem só aceita receita
publicada, que o lote nasce no início da ordem e não na criação dela, que a linha de envase não recebe plano
sem limpeza liberada dentro da validade da casa.

**A fonte é o teste, e está dito no texto.** O ciclo descrito é o de `business-journey.spec.ts`, que roda
contra a API real a cada mudança. O manual declara que, se divergirem, o teste está certo — sem isso, um
manual escrito uma vez vira folclore em três releases.

**A seção que mais vai ser usada é a das recusas.** Quase toda recusa do primeiro ciclo é regra, não defeito:
receita não publicada, estoque não reservado, limpeza vencida, permissão do tipo errado, conflito de versão.
Quem não sabe disso abre chamado; quem sabe, volta um passo. Por isso a tabela diz *o que a recusa está
afirmando*, e não só como sair dela.

**O roteiro de homologação foi escrito antes do ciclo, de propósito.** Rodar primeiro e listar depois produz
exatamente a evidência que sobrou — a etapa que ninguém capturou some do relatório, e o aceite fica
parecendo completo. Cada linha nomeia a evidência mínima, incluindo as duas que costumam faltar por não
serem "o fluxo": um 403 com Problem Details e a tentativa de outra cervejaria.

**O que continua aberto:** o ciclo em homologação, que depende do ambiente e de quem opera. E o manual
registra na própria seção final que, sem ensaio de restauração (`DEC-REL-008`), **não há RPO nem RTO de
dados medidos** — isso é informação operacional para quem for entrar em produção, não nota de rodapé.

### DEC-REL-007 (REL-002) — O gargalo era N+1, não payload; corrigidos os dois

A listagem de lotes foi paginada, e a investigação mudou o diagnóstico pelo caminho.

**A causa não era o tamanho do JSON.** O mapeador do repositório resolvia os passos *dentro* do
mapeamento — uma consulta por lote. Listar 3.000 disparava **3.001 consultas**. O N+1 era invisível para
quem lesse a chamada: a consulta extra não aparecia ali, aparecia no mapeador.

Isso muda a correção. Paginar sozinho esconderia o problema numa página de 20, e ele voltaria em qualquer
lugar que lesse muitos lotes. Foram corrigidos os dois: a página limita o conjunto e os passos da página
passaram a vir numa consulta só.

| 3.000 lotes | p95 |
|---|---|
| Antes | 319 ms |
| Depois | **9,8 ms** |

32× mais rápido, e — o que importa mais — deixou de crescer com o histórico: três consultas por chamada,
haja 3 mil ou 300 mil lotes.

**Quebra de contrato deliberada.** A resposta deixou de ser array e virou envelope com `content`.
`docs/20_RELEASE_MIGRATION.md` exige versão nova e janela de transição para mudança incompatível — fiz a
quebra direta porque o projeto está em `0.1.0-SNAPSHOT` e esta sprint é a **primeira produção**. É o último
momento em que isso é barato; depois do primeiro cliente integrado, a mesma correção custa dez vezes mais.

Duas decisões de forma:

- **Teto de 100 no `size`, normalizado em vez de recusado.** Sem teto, `size=100000` reproduz exatamente o
  problema que a paginação fecha. Recusar com 400 transformaria um deslize de quem chama em incidente de
  suporte.
- **`listForSelection` para os cinco seletores.** Eles só populam um `<select>`, mas truncar em silêncio
  faria quem procura um lote concluir que ele não existe. O helper devolve `truncated` e `total` junto,
  centralizando a honestidade num lugar em vez de cinco.

**O mesmo erro de alcance, duas vezes — e é o que vale registrar.**

Primeiro no backend: detectei os consumidores procurando um padrão de iteração específico e achei 13
arquivos. A suíte acusou **180 erros** porque havia 33, com formatos diferentes.

Depois no frontend, e pior: corrigi o `BatchesApi` de produção e os componentes que o usam. **Build verde,
503 testes verdes** — e o E2E vermelho. Havia **sete clientes independentes** do mesmo endpoint
(`packaging`, `fermentation/readings`, `fermentation/yeast`, `costing`, `reporting`, `ai`, `production`),
cada um declarando o próprio tipo local. Tipagem forte não ajudou justamente porque cada um tinha o seu.

As duas vezes a correção foi a mesma: **procurar pelo endpoint, não pelo padrão nem pela classe**. Procurar
pelo formato que eu esperava encontrar achou exatamente o que eu esperava — e nada além.

O E2E foi a única barreira que pegou o segundo caso. Um teste que atravessa a stack real vale por isso:
ele não sabe quantos clientes existem, só sabe que a tela ficou vazia.

### DEC-REL-008 (REL-001) — O ensaio de restauração foi removido a pedido do mantenedor

`infra/backup/` (`restore-drill.sh` e `table-counts.sql`) saiu do repositório por decisão explícita do
mantenedor: não há interesse em manter isso agora. As três referências no runbook de deploy foram
substituídas pelo procedimento manual equivalente, para o runbook não apontar para arquivo inexistente —
instrução que manda rodar um script que não existe é pior que instrução nenhuma no meio de um incidente.

**A consequência, registrada e não suavizada:** REL-001 pede "RPO/RTO medidos; procedimento reproduzível e
auditado", e sem o ensaio ela não fecha como especificada. `docs/21_DATA_RETENTION_BACKUP.md` continua
afirmando que **backup sem teste de restauração não é controle válido** — o documento e o repositório
passam a discordar, e quem for auditar isso encontra a divergência aqui, não por surpresa.

**Vale preservar o que o ensaio ensinou**, porque foi aprendido rodando o script, não escrevendo-o, e
custaria o mesmo tempo de novo:

- **`n_live_tup` não serve para conferir integridade.** É estimativa do autovacuum e vem **zerada** num
  banco recém-restaurado — a conferência acusaria divergência em todas as tabelas, sempre. O correto é
  `COUNT(*)` exato, via `query_to_xml` quando se quer varrer todas as tabelas de uma vez.
- **Ferramentas do PostgreSQL devem rodar em container da mesma imagem do servidor.** Não estavam
  instaladas no host, e `pg_dump` de versão menor que a do servidor recusa rodar — detalhe que aparece
  justamente no dia da restauração de emergência.
- **Senha com caractere especial quebra a URL de conexão.** `brassia85!@#` faz o parser ler `#@localhost`
  como host, e o erro ("could not translate host name") não aponta para a senha.

**Como reverter:** o script está no histórico do git até o commit imediatamente anterior a este; um
`git show <commit>:infra/backup/restore-drill.sh` o traz de volta inteiro. A decisão é reversível a
custo baixo — o que não é reversível é descobrir que o backup não restaura no dia em que ele for preciso.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
