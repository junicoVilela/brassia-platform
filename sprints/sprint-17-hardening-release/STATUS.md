# Status — Sprint 17

Estado: **ENCERRADA em 2026-08-15** — 3 de 5 histórias concluídas, 1 parcial, 1 fora de escopo. A sprint
fecha **sem declarar o release pronto**, porque as duas coisas que provariam isso — restauração medida
(REL-001) e um ciclo em homologação (REL-005) — dependem de ambiente e de quem opera, não de código.
O que dependia de código está entregue.

Encerra também **catorze débitos herdados das sprints 08 a 16** — dez com código, quatro por decisão sem
código —, mais a `DEC-BLD-003` que a Sprint 16 deixou em aberto. Não estavam no escopo original; entraram
porque o escopo original travou nas duas histórias acima, e débito parado é o que envelhece pior. Ver
DEC-DEBT-001 e as decisões por módulo.

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| REL-001 | **Parcial** — RTO medido; RPO decidido, não medido | Claude | `infra/runbooks/restore-drill.md` (2026-08-19), `docs/21_DATA_RETENTION_BACKUP.md` (2026-08-26) | Reaberta em 2026-08-19 no formato reduzido da `REL-001-PROPOSTA`. O ensaio rodou de ponta a ponta: dump, restauração isolada, integridade conferida em 204 tabelas, aplicação de pé contra a cópia. **Ainda não fecha como especificada.** Até 2026-08-25 o bloqueio era a ausência de política de backup; em 2026-08-26 ela passou a existir e fixou **RPO de 5 minutos**. O bloqueio mudou de natureza, e não sumiu: a história pede RPO **medido**, e medi-lo exige o WAL archiving rodando. O RTO segue medido em máquina de desenvolvimento com dados semeados. Ver DEC-REL-008, DEC-REL-011 e DEC-REL-017. |
| REL-002 | Concluída | Claude | `infra/perf/*`, `ListBatchesUseCase`, `JdbcBatchRepository`, `PageResponse` | Gargalo medido **e corrigido**: com 3.000 lotes, p95 caiu de 319 ms para 9,8 ms. Ver DEC-REL-007. |
| REL-003 | Concluída | Claude | `InternalAddressGuard`, `SecurityConfiguration`, `.github/workflows/ci.yml`, `frontend/package-lock.json` | Um achado ALTO (SSRF no webhook) e dois médios resolvidos; varredura de CVE passou a barrar merge. Ver DEC-REL-001/002/003. |
| REL-004 | Concluída | Claude | `infra/runbooks/deploy-rollback.md` | Ensaio executado em 2026-08-10: bloqueio de escrita medido migration a migration (`V100` = 143 ms) e retorno do artefato anterior contra o schema novo exercitado de verdade. Ambiente local, não cópia de produção — limitação registrada. Ver DEC-REL-006/009. |
| REL-005 | **Parcial** — manual entregue, ciclo pendente | Claude | PR #202, `docs/44_MINIMUM_OPERATING_MANUAL.md` §4.1 (2026-08-23) | O entregável que não depende de ambiente está pronto, com o roteiro de homologação e a evidência exigida por etapa. O ciclo em homologação continua aberto: depende do ambiente e de quem opera. **Segue aberto no encerramento.** Em 2026-08-23 o roteiro passou a dizer, linha a linha, o que já tem prova automática; em 24 o bootstrap ganhou a conta de pouca alçada e a segunda cervejaria; em 25 custo e relatório ganharam jornada. **17 das 25 linhas são exercitadas inteiras pela tela a cada build**, 5 em parte, 2 só pelo backend, e 1 não é teste. Ver DEC-REL-010, DEC-REL-012, DEC-REL-013, DEC-REL-014 e DEC-REL-015. |
| Débitos 08–16 | Concluída (14 débitos) | Claude | PRs #212 a #224, ADR-0016 | Fora do escopo original, por decisão do mantenedor. Dez fechados com código, quatro por decisão sem código. Ver DEC-DEBT-001, DEC-CLN-001, DEC-AIA-001, DEC-FDS-001/002, DEC-PKG-001/002, DEC-QLT-001, DEC-CST-001/002, DEC-RPT-001. |

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

### DEC-RPT-001 (RPT-001-A) — Marca é acabamento, e esperá-la custava o documento inteiro

Decisão do mantenedor em 2026-08-15: PDF com layout simples, **sem identidade visual**. O critério de
remoção pedia "a casa decidir o layout do documento impresso e existir decisão sobre marca e assinatura"
— e foi exatamente essa espera que manteve o débito aberto por três sprints, com a cervejaria sem nada
para mandar a um auditor. Quando houver marca, ela entra sobre um documento que já funciona.

**O JSON continua sendo o padrão, e o formato é negociado pelo `Accept`.** Quem já integra com a
exportação recebe o mesmo corpo de antes: trocar o padrão quebraria integração por causa de acabamento. A
auditoria passou a registrar qual formato saiu — "exportou o relatório" e "levou o PDF para uma auditoria"
são a mesma permissão e histórias diferentes.

**As lacunas vão no topo do papel**, logo abaixo do cabeçalho. É a mesma regra da tela, e num documento
impresso ela pesa mais: o rodapé é onde a informação morre, e quem imprime precisa ver o que o relatório
*não* prova antes de mandá-lo a um cliente que vai lê-lo como se provasse tudo.

**PDFBox, e a licença foi critério.** Apache 2.0, sem dependência de servidor gráfico. A licença importa
aqui mais que o de costume porque o artefato gerado sai da plataforma para as mãos de terceiros.

**Fontes padrão do PDF.** Helvetica é uma das 14 que todo leitor tem: embutir fonte própria pesaria o
arquivo e exigiria licença de distribuição, para um documento que a cervejaria manda por e-mail.

**Dois detalhes que só aparecem gerando:**

- O escritor **vira a página sozinho**. Sem isso, um lote com muitas lacunas escreveria fora do papel — o
  PDFBox não recusa, ele desenha o texto onde ninguém vai ler.
- Caractere fora do WinAnsi **derruba a geração inteira** com exceção. Perder o relatório por causa de um
  símbolo num texto de lacuna seria perder o documento por causa do acabamento; ele vira interrogação e o
  resto se preserva.

**O teste abre o PDF.** Responder 200 com bytes não prova nada: um arquivo corrompido passa por qualquer
asserção de tamanho e falha na mão de quem for lê-lo. O `BatchReportIT` carrega o documento, extrai o
texto e confere que as seções e as lacunas estão lá.

### DEC-CST-002 (CST-002-A) — A perda esperada é da receita, e o esperado incide sobre o planejado

Decisão do mantenedor em 2026-08-14: perda esperada por etapa na receita — e **não** no equipamento ou na
política de envase, como o critério de remoção sugeria.

**A razão é física.** A perda característica é da cerveja tanto quanto do tanque: uma IPA muito lupulada
deixa mais líquido preso no trub que uma lager no mesmo fermentador. O dead space do equipamento já é
conhecido em outro lugar; o que faltava era o que *esta* cerveja perde. E a receita já carrega eficiência
de mostura e volumes — perda esperada é o mesmo tipo de dado.

**Versiona de graça.** Cada versão de receita é uma linha (V28), então a perda esperada acompanha a versão
publicada, e um lote é comparado contra o número que valia quando ele foi feito — não contra o que alguém
ajustou depois.

**Percentual, e não litros.** Perda de trub e absorção de lúpulo escalam com o tamanho da brassa. Um valor
absoluto ficaria errado no dia em que a cervejaria dobrasse o lote, e ficaria errado **em silêncio**.

**O esperado incide sobre o volume PLANEJADO da etapa**, e essa é a decisão que evita um erro sutil:
calculá-lo sobre o realizado faria o esperado seguir o desvio. Um lote que rendeu menos "esperaria" perder
menos, e o desvio sumiria por construção — a comparação passaria a se auto-justificar.

**O raciocínio original continua valendo para quem não cadastrou.** Sem percentual, a perda segue entrando
como fato, sem desvio: assumir esperado zero faria toda perda parecer desvio, e acusaria a fábrica com um
critério que ela nunca definiu. O que mudou na lacuna é o texto — ela deixou de citar o identificador do
débito e passou a dizer o que fazer.

**Zero não é nulo, e a tela respeita isso.** Uma cervejaria que não perde nada numa etapa tem esperado
zero, que é diferente de não ter medido. No formulário, `?? null` em vez de `|| null` — com `||`, um
esperado de 0% viraria "não mediu".

### DEC-CST-001 (CST-001-A) — A hora é da produção, o dinheiro é do custeio

Decisão do mantenedor em 2026-08-14: apontamento de hora no dia de brassa, com custo/hora vindo de
parâmetro. As duas metades do critério de remoção estão implementadas — o apontamento e o contribuinte.

**A separação é a decisão central.** `production_labor_entry` guarda quem trabalhou, quando e por quanto
tempo; `costing_labor_rate` guarda quanto a hora vale. Não é preciosismo de camada: é o que permite
ajustar a taxa sem reescrever apontamento, e o que evita que quem registra seis horas de brassa precise
conhecer moeda para fazê-lo. Por isso o contribuinte mora no **custeio**, e não na produção: a parcela é
`hora × taxa`, e a taxa é dele.

**Horas-homem, não horas.** Duas pessoas por três horas custam seis. Guardar "3 h" perderia exatamente a
metade que a cervejaria paga.

**Uma taxa por cervejaria, não uma por pessoa.** Custo de mão de obra por lote é custo médio da hora
produtiva — salário, encargos e ociosidade diluídos. Uma taxa por pessoa faria o mesmo lote sair mais caro
na semana em que o cervejeiro sênior trabalhou, o que descreve a escala e não o produto. Se um dia for
necessário, entra como taxa por atividade, que é a divisão que a cervejaria enxerga.

**Duas ausências diferentes, e a lacuna agora distingue.** Antes havia uma frase genérica no montador:
"não há hora trabalhada registrada na plataforma". Ela saiu de lá — a mão de obra passou a ter dono, e
quem declara a lacuna é o contribuinte, que sabe separar "sem taxa cadastrada" (defina a taxa) de
"ninguém apontou hora neste lote" (aponte as horas). São ações diferentes, e uma frase só as achatava.

**A atividade é texto, não enum.** A divisão de trabalho de uma cervejaria de três pessoas não é a de uma
de trinta, e um enum imporia a de quem escreveu o código.

**Lote cancelado não recebe apontamento; encerrado recebe.** Limpeza e fechamento acontecem depois de o
lote acabar, e recusar apontamento aí obrigaria a apontar antes de trabalhar.

**Continua faltando o `CST-001-B`** (utilidade por lote), e a lacuna estrutural do montador agora existe
só para ela.

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
registra na própria seção final o estado da restauração — desde a `DEC-REL-011`, **o RTO está medido e o
RPO não** — porque isso é informação operacional para quem for entrar em produção, não nota de rodapé.

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

### DEB-INT-004 — RESOLVIDO em 2026-08-26: o contrato publicava um caminho que ninguém servia

**O levantamento que a `DEB-INT-003` sugeriu, feito.** A pergunta era: *quantos caminhos publicados nenhum
teste executa?* — porque o defeito do outbox existia sob um teste verde contra dublê.

**A parte que vale mais que o resultado: os três primeiros números estavam errados, e eu não os mostrei.**
A varredura deu 198, depois 153, depois 20. As duas primeiras eram ficção da ferramenta e não do código:
os testes montam URL por constante (`WEBHOOKS + "/event-types"`) e por concatenação, e o casamento não
atravessava isso. Só houve número reportável depois de **calibrar contra oito endpoints sabidamente
testados** e ver os oito darem positivo. Um 198 apresentado teria mandado caçar 180 fantasmas.

**Dos 20 finais, 5 eram falso positivo** — URL montada por método auxiliar (`base(equipmentId)`,
`schedule(batchId)`), que nenhuma resolução de constante alcança. Os 20 foram conferidos **um a um, na
mão**. Sobraram 15.

**O achado que não era falta de teste.** `POST /api/v1/batches/{batchId}/measurements` estava no
`openapi.yaml` e **nenhum controlador o servia**: rascunho antigo do endpoint que hoje vive em
`/production/batches/{id}/measurements`, com um schema `Measurement` órfão junto. Quem integrasse pelo
contrato bateria num 404. **Contrato e implementação divergindo em silêncio é pior que contrato ausente:**
um contrato ausente manda perguntar; um contrato errado manda confiar. Caminho e schema removidos.

**A barreira.** `PublishedPathsTest` cruza os caminhos do contrato com os `@RequestMapping`/`@*Mapping` do
código e reprova o que ninguém serve. Verificada reintroduzindo o fantasma: ela falha nomeando o caminho.
Duas decisões nela:

- **Só a direção contrato → código.** A oposta pegaria endpoint interno legítimo e exigiria lista de
  exceção, que envelhece — e portão ruidoso é portão desligado.
- **Nome de parâmetro é normalizado.** O contrato diz `{batchId}` onde o controlador diz `{id}`; barrar
  divergência de vocabulário não é o que se quer.

**Os 14 endpoints restantes ganharam teste**, e o critério em cada um foi procurar a regra que o endpoint
guarda, não exercitar o caminho feliz:

| Endpoint | O que o teste passou a cobrar |
|---|---|
| `PUT /sales/orders/{id}/promise` | **A regra da validade vale na remarcação, não só na criação** — o furo clássico de regra que mora só no caminho de entrada |
| `PUT /sales/channels/{id}/active` | desativar some da lista padrão e **continua existindo**, e volta |
| `DELETE /distribution/loads/{id}/containers/{containerId}` | tirar **solta** o keg para outra carga, e não se tira de carga já conferida |
| `GET /distribution/loads/{loadId}/proofs` | carga de outra casa é `404`, e não lista vazia — vazia diria "existe e não teve entrega" |
| `GET /distribution/sync/loads/{loadId}` | a fila de **uma** carga, com o contraponto de a de outra não aparecer |
| `POST /reporting/saved-reports/runs/{runId}/link` | o link é token **novo** e **nunca vive mais que o artefato** |
| `POST /reporting/saved-reports/{reportId}/active` | desativar para a programação e **não apaga o histórico** |
| `GET /sensory/sessions/attributes` | o vocabulário da ficha vem do servidor, e ler exige alçada |
| `DELETE /sensory/sessions/{id}/samples/{sampleId}` | sai do rascunho, **não sai da sessão aberta** |
| `PUT /community/library/{id}/license` | troca daqui para a frente; publicação alheia é `404` |
| `POST /field-feedback/complaints/{id}/analysis` | tira da fila, e a segunda vez é `409` |
| `GET /portal/credit` | sem teto o valor é **nulo, não zero**, e o comprometido acompanha o pedido |
| `DELETE /quality/control-plans/{id}/points/{pointId}` | sai do rascunho, **não sai do plano publicado** |
| `PUT /water/profiles/{id}` | **bloqueio otimista**: o ajuste de quem leu a tela velha não vence em silêncio |

**Três expectativas minhas estavam erradas, e o código certo** — corrigi os testes, não o código: a
listagem de água é paginada, o status inicial de reclamação é `OPEN`, e abrir análise duas vezes responde
`409` em vez de ser idempotente. A terceira é decisão defensável do módulo, e o teste passou a dizer por
que ela é assim.

**Resultado:** das 447 operações publicadas, a varredura acusa 5 sem teste — e as 5 são os falso positivos
já conferidos na mão. **Nenhuma operação publicada ficou sem teste.**

### DEB-INT-003 — RESOLVIDO em 2026-08-25: ALTO — o outbox de webhooks nunca concluía uma entrega

**Achado por um aviso no log, não por um teste.** Ao subir a aplicação para outra tarefa, o
`WebhookDispatchRunner` registrava, a cada quinze segundos:
`rodada de webhooks falhou: No value supplied for the SQL parameter 'brewery'`.

**A causa.** O `UPDATE` do outbox filtrava por `brewery_id = :brewery` e **nunca ligava o parâmetro**.

**O efeito era o oposto exato do que o outbox promete.** A entrega era despachada, o destino recebia, o
desfecho não era gravado, a linha continuava `PENDING` e o mesmo evento saía de novo na rodada seguinte —
para sempre. `enqueueIfAbsent` e `FOR UPDATE SKIP LOCKED` existem precisamente para impedir duplicata, e
ela entrava pela porta seguinte. Pior: a exceção **escapava do `catch` por entrega** do despachante,
porque o próprio `catch` chama `update`. A rodada inteira abortava na primeira entrega, e **nenhum webhook
da instalação era concluído**.

**Por que nenhum teste pegou, e é a parte que vale carregar.** `DeliveryDispatcherTest` exercita o
despachante contra um repositório **dublê** — e dublê não tem SQL. O `WebhookIT` cobria assinatura,
outbox transacional, restrição única e alçadas, mas nunca o `UPDATE`. **SQL só falha quando roda**, e a
instrução que nenhum teste executa é exatamente onde o parâmetro esquecido se esconde.

**A barreira, que é a outra metade do trabalho.** `BoundParametersTest` varre os blocos
`.sql(""" … """)` do `src/main/java` e reprova todo parâmetro nomeado que a cadeia fluente não liga —
mesmo padrão da `TenantIsolationTest`. Ela falha com contagem mínima se a extração quebrar, e o `(?<!:)`
do padrão é obrigatório: sem ele todo `::text` do PostgreSQL viraria infração falsa, e a barreira seria
desligada na primeira semana.

**E ela encontrou mais três defeitos no mesmo build, todos em `JdbcSecurityAlertRepository`:**

| Defeito | Efeito |
|---|---|
| `findById` filtrava por `:brewery` e o método **nem recebia** a cervejaria | **Resolver um alerta de segurança nunca funcionou** — `PATCH /api/v1/security/alerts/{id}` sempre respondia 500 |
| `listByBrewery` usava `:status IS NULL` sem `CAST` | **Listar alertas sem filtro sempre respondeu 500** — que é justamente como a tela abre |
| `enqueueIfAbsent` tinha `.param("brewery", …)` duplicado | Inócuo, mas é a digital do copiar-e-colar de onde a ligação se perdeu |

O arquivo inteiro não tinha teste — nem de unidade, nem de integração. Os quatro defeitos são a mesma
causa: **caminho publicado que nenhum teste executa**.

**Como o `findById` foi corrigido importa.** O escopo passou para a **consulta**, e a conferência
posterior no handler (`if (alert.breweryId() != ...) throw Forbidden`) foi removida — é exatamente o que a
`OBS-REL-001` pede: a garantia deixa de depender de quem chama lembrar de comparar. De quebra, alerta de
outra cervejaria passou a responder como alerta que não existe, pela mesma razão da `DEB-PRD-002`.

**Verificado:** `WebhookIT#oDesfechoDaTentativaEGravado` reproduz o defeito contra o banco real (falhava
com `InvalidDataAccessApiUsageException` antes da correção) e afirma o efeito, não o código — a entrega
concluída **não volta** na rodada seguinte. `SecurityAlertIT` cobre os quatro caminhos que não tinham
teste. A `BoundParametersTest` não precisou ser verificada contra versão quebrada: **ela encontrou três
defeitos reais no primeiro build em que rodou.**

### DEC-REL-017 (REL-001) — **2026-08-26**: a política de backup existe, e o RPO é de 5 minutos

**O bloqueio que estava em pé desde 2026-08-15 mudou de natureza.** A `DEC-REL-011` registrou que o RPO
dependia de uma política que não existia, e que nenhum ensaio o produz a partir de uma ausência. A política
agora existe (`docs/21_DATA_RETENTION_BACKUP.md`), decidida pelo mantenedor.

| | | |
|---|---|---|
| **RPO** | 5 min | WAL contínuo com PITR |
| **RTO** | 4 h | a restauração leva 56 s; o resto é perceber, decidir e conferir |
| **Retenção** | 30 dias de janela PITR | o prazo em que se percebe corrupção lenta |

**O argumento que decidiu o RPO.** Apontamento, pedido e entrega se refazem com dor. **Auditoria e
genealogia não se refazem** — são a evidência, e são o que um recall precisa para dizer que este keg
carregou aquele lote. Uma casa com três barreiras de build guardando a rastreabilidade não pode ter backup
que a perde por atacado. Réplica em streaming foi descartada por comprar segundos onde o WAL compra
minutos, ao custo de outra máquina sempre ligada — e a indisponibilidade não é o gargalo, já que a volta
mede 56 segundos. Entra na conta um atenuante do próprio desenho: a fila offline da entrega vive **no
aparelho** até sincronizar (PWA-002), então perder o servidor não perde o que o entregador registrou.

**A distinção que muda o desenho, e que quase virou custo inútil:** *backup não é arquivo morto*. A
retenção legal plurianual da rastreabilidade se resolve **exportando dossiês** (RPT-001), não guardando
dumps velhos — restaurar um dump de três anos num schema duzentas migrations à frente não é plano, é
esperança. Backup responde "o sistema volta"; arquivo responde "provo o que aconteceu em março de 2024".
Guardar backup por anos paga caro por um artefato velho demais para restaurar e opaco demais para auditar.

**Duas decisões que existem para não serem tomadas às três da manhã:**

- **Banco e objetos: o banco manda.** Restaura-se o Postgres ao ponto escolhido e os objetos ficam como
  estão. É seguro porque o armazenamento é *append-only* na prática, e o efeito é objeto órfão — lixo, não
  corrupção. O oposto seria dossiê apontando para arquivo inexistente, que é documento que mente.
- **Quem decide restaurar tem nome**, e não cargo: restaurar descarta tudo depois do ponto escolhido. Quem
  decide deveria ser outra pessoa que quem executa, pela mesma razão da LOG-001 — a carga não é liberada
  por quem a montou. **Os três nomes estão em branco na política, aguardando o mantenedor.**

**A REL-001 NÃO fecha com isto, e a distinção importa.** A história pede "RPO/RTO **medidos**". O RPO agora
está **decidido**; medi-lo exige o WAL archiving configurado e rodando, para observar o atraso real do
arquivamento. O que mudou é a natureza do bloqueio: era "falta uma decisão que só o negócio toma", e passou
a ser "falta ambiente" — a mesma dependência que segura a REL-005.

### DEC-REL-015 (REL-005) — **2026-08-26**: o consentimento da entrega sai do "só backend"

**A regra é de dado pessoal, e o lugar onde ela se perde é a tela.** `DeliveryIT` prova as três coisas
pela API: a entrega acontece sem assinatura, a mídia não existe sem finalidade, e a chave do arquivo não
sai na listagem. Nenhuma tinha sido vista numa tela — e uma tela que exigisse o nome de quem assinou para
o botão funcionar transformaria o dado pessoal em **obrigatório na prática**, com a API impecável e a
regra revogada na ponta. É na ponta que o entregador está, com o cliente esperando na porta.

**A jornada exercita as duas metades pelo formulário:** o campo de assinatura fica em branco e a entrega
acontece; com nome, a tela mostra de quem é a assinatura.

**A finalidade é a metade menos óbvia, e é a que a tela resolve bem.** Quem opera não digita para que
serve a assinatura — e não deveria: pedir isso a cada parada produziria "entrega" mil vezes e um cheque em
branco na milésima. A tela a fornece, e o teste cobra que ela chegue gravada. O operador **não tem como**
produzir mídia sem finalidade, porque esse caminho não existe na interface.

**E a chave do arquivo não chega ao navegador.** A listagem diz que existe assinatura e de quem; onde ela
está guardada é outra conversa. Sem essa asserção, "a mídia é protegida" seria promessa de servidor.

**Verificado contra a versão quebrada:** tornando `consentedByName` obrigatório no formulário, a entrega
sem assinatura não acontece e a tela nunca mostra "Entregue" — a jornada falha. É exatamente o defeito que
ela existe para barrar, e é um defeito que nenhum teste de backend pegaria.

**Efeito no roteiro:** de 25 linhas, **17 percorridas inteiras pela tela** (eram 16), 5 em parte, 2 só pelo
backend (eram 3), 1 não é teste.

### DEC-REL-014 (REL-005) — **2026-08-25**: custo e relatório saem do "só backend"

**A linha do roteiro é "custo e relatório do lote fecham com o que foi produzido", e o que existia provava
outra coisa.** `BatchCostIT`, `BatchVarianceIT` e `BatchReportIT` provam a apuração pela API; as telas
tinham teste, e o que ele afirmava era que elas **carregam**. Entre as duas coisas cabe o defeito que
importa: um total certo na API que chega torto à tela é indistinguível, para quem lê, de um total errado —
e foi assim que o `LOCALE_ID` ficou meses mostrando `200.00` onde a casa lê `200,00`, com todo o backend
verde.

**A jornada persegue três acordos, e nenhum deles é "a tela abriu":**

1. **A tela concorda com a API.** O total exibido é o total apurado, formatado como a casa lê dinheiro.
2. **As duas telas concordam entre si.** Custo e dossiê derivam da mesma apuração; se divergirem, um dos
   dois sai da casa com o número errado e não há como saber qual olhando só um deles.
3. **O número se move quando a produção muda.** Sem isto, um total gravado uma vez e nunca recalculado
   passaria nos dois primeiros.

**A conta fecha de verdade, e não é "um número apareceu":** o teste confirma o consumo da brassagem pela
proposta de reserva, calcula o esperado do preço de entrada semeado (1,50 por unidade de compra) e confere
parcela a parcela — 90,00 de lúpulo, 30,00 de malte, 1,50 de levedura, 592,50 de embalagem, **714,00** de
total, com o custo por litro caindo sobre os 390 L **transferidos** e não sobre o volume planejado.

**A lacuna é o contraponto do teste inteiro.** Um custeio que somasse zero de mão de obra mostraria um
número menor com cara de completo, e alguém precificaria em cima dele. A jornada exige a ressalva na tela,
depois **fecha** a lacuna (taxa de hora + apontamento) e exige que o total suba — o que prova que o número
é derivado, e não decorativo.

**Descoberto ao escrever:** o `seedSellableLot` nunca confirmava o consumo da brassagem, então o custo de
insumo ficava zerado e declarado como lacuna. A primeira versão da jornada teria passado afirmando um
"total" que era só embalagem. Confirmar o consumo é o que faz a linha do roteiro ser sobre *o que foi
produzido*.

**Verificado contra a versão quebrada:** trocando o `number: '1.2-2'` do total para `'en-US'`, a tela passa
a mostrar `714.00` e a jornada falha. A asserção da vírgula não é preciosismo — é o que segura o
`LOCALE_ID` no lugar.

**Efeito no roteiro:** de 25 linhas, **16 percorridas inteiras pela tela** (eram 15), 5 em parte, 3 só pelo
backend (eram 4), 1 não é teste.

### DEC-REL-013 (REL-005) — **2026-08-24**: o bootstrap ganhou os dois eixos que faltavam

**O que a DEC-REL-012 encontrou, esta fecha.** As duas linhas que o roteiro chama de "as que costumam
faltar" não faltavam por esquecimento: **faltava alguém para logar**. As duas contas do perfil `local`
estavam no mesmo grupo `ADMINISTRATORS` e o bootstrap criava uma cervejaria só, então nem a recusa por
permissão nem a tentativa de outra casa tinham como ser encenadas em ambiente nenhum. O backend já provava
as duas regras — 167 asserções de 403 e 26 testes de isolamento —, e nenhuma delas passava por uma tela.

**Cada conta de bootstrap varia um eixo só, e é isso que as torna prova.** Admin e conferente: mesmas
permissões, *pessoas* diferentes. Operador: mesma casa, *permissões* diferentes. Vizinha: mesma alçada,
*cervejaria* diferente. Uma conta que variasse dois eixos provaria menos — ao levar 403, não se saberia se
foi por permissão ou por casa errada, e são recusas diferentes com correções diferentes.

**As duas jornadas novas** (`e2e/tests/authority-and-isolation.spec.ts`), e o que cada uma cobra além do
óbvio:

- **Alçada.** O operador *lê* o custo (sem esse contraponto, tela em branco não distingue "não tenho
  alçada" de "a tela quebrou"); a tela **não lhe oferece** o botão de fechar; o **mesmo botão aparece**
  para quem tem a alçada (sem isso, um botão renomeado deixaria a asserção verde para sempre); e o POST
  direto responde `403` com `code: forbidden` — porque esconder o botão é cortesia com quem opera, e não
  autorização. A última asserção é a que separa uma interface arrumada de um sistema seguro.
- **Isolamento.** A vizinha alcança **uma** cervejaria; não vê o lote desta casa na tela; e **vê o lote
  dela** — sem esse contraponto, uma tela quebrada ou uma sessão sem cervejaria ativa passariam por
  isolamento.

**As duas foram verificadas contra a versão quebrada**, e não só vistas passar: com `costing.cost.close`
concedido ao grupo estreito, o botão aparece e a asserção falha; com a associação da vizinha trocada para
global, ela passa a enxergar as duas casas — e, afrouxando a asserção de sessão para deixar o teste chegar
mais longe, o lote alheio aparece na tela dela. Ambas as alterações foram desfeitas.

**O que o teste de isolamento afirma mudou depois de rodar, e para melhor.** Eu esperava `404` no acesso
direto ao lote alheio; a API responde `400`. A isolação está íntegra — o repositório filtra por cervejaria
e a vizinha não recebe nada —, mas o status vem de um `IllegalArgumentException` genérico em
`GetBatchHandler`. Em vez de cravar um código, o teste passou a afirmar o que de fato importa:
**indistinguibilidade** — o lote da outra casa responde exatamente como um id sorteado que não existe em
lugar nenhum, mesmo status, mesmo `code`, mesmo `detail`, e sem o código do lote no corpo. Essa é a
propriedade que fecha o vazamento, e ela não depende de qual código o backend escolheu. Ver `DEB-PRD-002`.

**O quarto initializer foi o que forçou a extração.** Admin e conferente já eram o mesmo procedimento
escrito duas vezes; com operador e vizinha seriam quatro lugares para lembrar de mudar. O
`BootstrapAccountSeeder` recolheu a mecânica, e os quatro passaram a declarar só o que os distingue.

**Duas armadilhas de ordem, ambas encontradas antes de custar caro:**

- **O código da vizinha precisa ordenar depois do da padrão.** A cervejaria ativa de uma sessão é a
  *primeira por código* entre as acessíveis, e o admin tem associação global — ele alcança as duas. Um
  código antes de `MATRIZ` trocaria a casa ativa de toda a suíte E2E, que passaria a semear numa e a ler
  na outra. `VIZINHA` vem depois; está escrito no javadoc e no YAML.
- **O guarda do `BreweryBootstrapInitializer` é "não existe cervejaria nenhuma".** Se a vizinha nascesse
  primeiro num banco vazio, a `MATRIZ` nunca seria criada. Os `ApplicationRunner` passaram a ter ordem
  declarada, e o caminho de banco vazio foi **executado** num banco novo para conferir: `MATRIZ`,
  `VIZINHA`, e as quatro contas com o escopo certo.

**O que isto NÃO promete.** Nada disto existe fora do perfil `local`: o grupo estreito é criado pelo
initializer e não por migration, de propósito — uma migration o levaria para produção, e nenhuma casa
pediu um grupo chamado `OPERADORES_LOCAIS`.

**Efeito no roteiro:** de 25 linhas, **15 passam a ser percorridas inteiras pela tela** (eram 13), 5 em
parte, 4 só pelo backend (eram 6), e 1 não é teste. O ciclo em homologação continua aberto — ele depende
de ambiente e de quem opera —, mas as duas linhas mais esquecidas dele saíram da lista de "alguém precisa
lembrar" e entraram na de "o build confere".

### DEB-PRD-002 — RESOLVIDO em 2026-08-24: lote inexistente responde 400 onde a casa toda responde 404

**O efeito.** `GET /production/batches/{id}` para um lote que não existe — ou que é de outra cervejaria —
responde `400 bad_request` com "Requisição inválida.", porque `GetBatchHandler` lança
`IllegalArgumentException` e o advice global o traduz assim. O pedido não era inválido: estava bem-formado
e o recurso é que não existe para quem pediu. Quem integra recebe uma resposta que manda conferir o
próprio pedido quando o que houve foi outra coisa, e o resto da plataforma responde `404` com código
próprio em situação igual (`identifier_not_found`, `unknown_batch`, `unknown_node`).

**Não é vazamento**, e por isso é débito e não achado: a resposta é idêntica para "não existe em lugar
nenhum" e "existe noutra casa", e o E2E de isolamento passou a afirmar justamente essa igualdade.

**Por que não foi corrigido junto.** A história autorizada era a do bootstrap. Trocar o status é mudança de
contrato — mexe no `openapi.yaml` e no que o frontend trata —, e entraria de carona numa entrega sobre
outro assunto.

**Critério de remoção:** `GetBatchHandler` passa a lançar uma exceção própria traduzida para `404` com
código estável, o `openapi.yaml` documenta, e o E2E de isolamento continua verde **sem alteração** — ele
não depende do código escolhido, só da igualdade entre as duas respostas.

**Critério cumprido em 2026-08-24, e o escopo foi maior do que a linha do débito dizia.** O efeito estava
escrito sobre o `GET`, mas a causa era do módulo: **treze** lugares na produção lançavam o mesmo
`IllegalArgumentException("lote inexistente")`. Corrigir só o `GET` deixaria doze respostas erradas e a
aparência de resolvido.

- **`UnknownBatchException` → `404 unknown_batch`**, nos treze. É o código que custo, relatório e IA já
  usavam — não inventei vocabulário novo.
- **`UnknownStepException` e `UnknownAlertException` → `404`** vieram junto por necessidade, não por
  capricho: deixá-las em `400` criaria, *no mesmo controlador*, a inconsistência que o débito veio remover.
- **As duas cobrem "de outro lote" com a mesma resposta de "não existe"**, pela razão que o E2E de
  isolamento já usava um nível acima: o endereço é um par, e responder diferente confirmaria a existência
  do recurso para quem só tem o identificador. `ConfirmAlertHandler` tinha um "alerta não pertence ao lote"
  distinguível; não tem mais.

**O que NÃO mudou, e é a metade que importa da decisão.** Continuam `400` os casos em que o problema é um
**campo do corpo**: etapa informada numa medição, medição de origem numa correção, calculadora que não é
correção de brassa, fermentador de destino inexistente. Ali o pedido *é* inválido e quem opera precisa
saber qual campo consertar — 404 apagaria essa informação. A regra que separa os dois é "o recurso do
endereço" contra "a referência no corpo".

**O defeito que a correção quase introduziu, achado revisando o frontend.** `OfflineQueueFacade#isConflict`
tratava 400/409/422 como conflito e o resto como falha transitória. O lote inexistente respondia 400 e caía
como conflito; ao virar 404 ele passaria a ser **retentado para sempre** por um aparelho de chão de fábrica,
contra um lote que não vai voltar a existir. O 404 entrou na lista, e o teste que guarda a passagem foi
verificado contra a versão sem ele — falha.

**Verificado:** `StepProgressIT#loteDeOutraCasaRespondeExatamenteComoLoteQueNaoExiste` compara as duas
respostas inteiras (status, `code`, `detail`, `title`) em vez de cravar um código;
`etapaDeOutroLoteRespondeComoEtapaQueNaoExiste` e
`AlertCenterIT#alertaDeOutroLoteRespondeComoAlertaQueNaoExiste` fazem o mesmo um nível abaixo. O E2E de
isolamento seguiu verde **sem uma linha alterada**, como o critério pedia — e é isso que prova que ele
afirmava a propriedade certa desde o começo.

### DEC-REL-012 (REL-005) — **2026-08-23**: o roteiro passou a dizer o que já está provado

**O que mudou.** A `DEC-REL-010` deixou o roteiro de homologação escrito e o ciclo aberto, e assim ficou
por oito dias. O ciclo continua aberto — ele depende de ambiente e de quem opera, e isso nenhuma linha de
código resolve. O que foi feito é outra coisa: **cada uma das 25 linhas do roteiro passou a citar a prova
automática que já existe para ela**, conferida contra o repositório e não de memória.

**O resultado, e ele é melhor do que eu esperava:** 13 linhas são percorridas **inteiras pela tela** a cada
build, 5 em parte, 6 só têm prova de backend, e 1 (bloqueadores) não é teste — é o `STATUS.md`. As três
jornadas E2E que apareceram entre as sprints 18 e 20 cobriram, sem que ninguém tivesse cruzado as duas
listas, a maior parte de um roteiro escrito antes delas.

**Por que isso importa para quem for homologar.** A distinção que a tabela faz e que uma contagem de
cobertura não faria é entre *"provado pela tela"* e *"provado só pelo backend"*. A segunda é onde mora o
defeito que atravessa tudo: a regra recusa corretamente pela API, e a recusa **não aparece para quem
opera**. Foi exatamente esse o defeito que a `SAL-004` custou a achar, e a jornada comercial só o pegou
porque olhou a tela. Quem homologar deve gastar o tempo nessas seis linhas, e não redescobrindo que a
receita publica.

**O achado que só apareceu ao cruzar as listas.** As duas linhas que o próprio roteiro chama de "as que
costumam faltar por não serem o fluxo" — o 403 com Problem Details e a tentativa de outra cervejaria — são
justamente as duas que **o bootstrap local não consegue produzir**:

- As duas contas do perfil `local` estão no mesmo grupo `ADMINISTRATORS`, por decisão registrada no
  javadoc do `BootstrapCheckerInitializer`: a segunda conta existe para exercitar a regra de *pessoas
  diferentes*, e não a de permissões diferentes. Não há ninguém de pouca permissão para logar.
- O bootstrap cria **uma** cervejaria, e não há porta pública para criar conta com senha noutra: o convite
  manda token por notificação, e o SCIM exige credencial de serviço e não define senha.

Não é coincidência que as linhas mais esquecidas sejam as que o ambiente de teste não sabe montar — é
causa. **Fechá-las é trabalho de bootstrap, não de teste**, e é história nova: uma terceira conta com
alçada estreita e uma segunda cervejaria semeada. Enquanto não existir, as duas são **obrigatórias** na
homologação, com o corpo da resposta anexado.

**Correção de rumo no próprio manual.** O cabeçalho ainda avisava que venda e retornáveis não tinham
jornada E2E. Elas têm desde as sprints 19 e 20 — `sales-journey.spec.ts` e `distribution-journey.spec.ts`.
Um aviso desatualizado num manual é pior que aviso nenhum: ele manda desconfiar exatamente do que passou a
ser a parte mais bem provada.

### DEC-REL-011 (REL-001) — **2026-08-19**: o ensaio voltou, no formato reduzido

**O que mudou.** A `DEC-REL-008` removeu o ensaio e deixou registrado que o documento de retenção e o
repositório passavam a discordar. Em 2026-08-19 o ensaio foi reexecutado no **tamanho B** da
`REL-001-PROPOSTA` — execução única, manual, com o roteiro versionado — e essa divergência fechou.

**O que o ensaio provou.** Banco de 1.586 MB com 2M eventos de auditoria e 5,5M medições: `pg_dump` em
54,6 s produzindo 611 MB, `pg_restore` numa cópia isolada em 44,6 s, **204 de 204 tabelas conferindo
linha a linha**, e — a prova que as anteriores não dão — a **aplicação subindo contra a cópia** em 11,3 s
e servindo 5.000 lotes por uma listagem paginada. Linhas restauradas não são banco utilizável; só o
arranque do Flyway e uma leitura de verdade separam "o dump abriu" de "o sistema voltaria".

**As duas lições da DEC-REL-008 foram usadas, não reaprendidas:** `COUNT(*)` exato via `query_to_xml` em
vez de `n_live_tup` (que volta zerado num banco recém-restaurado e acusaria divergência em tudo), e
ferramentas do PostgreSQL na mesma imagem do servidor.

**Por que a história ainda não fecha.** A especificação pede "RPO/RTO medidos". O **RTO está medido**, sob
condições declaradas por escrito no runbook. O **RPO não** — ele depende de frequência de backup,
retenção e cópia fora do ambiente, e essa política não existe. Nenhum ensaio produz um RPO a partir de
uma política ausente, e escrever um número ali seria inventar o controle em vez de tê-lo.

**A limitação que não deve ser suavizada:** máquina de desenvolvimento, dados semeados pelo
`seed-representative-dataset.sql`, não cópia de produção — porque produção não existe. Os tempos são
ordem de grandeza e prova de procedimento, e o runbook diz isso na própria seção de resultados, não em
nota de rodapé.

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

- **Build/commit:** as histórias de release em #188 (REL-002), #189, #193, #194 (REL-001, remoção),
  #201 (REL-004) e #202 (REL-005); os débitos herdados em #212 a #222, um PR por débito; o ADR-0016 em
  #223; a varredura de âncoras de data em #224. Um PR por assunto, mergeados em série na `main`.
- **Testes executados:** `mvnw verify` verde na `main` em 2026-08-15 — **1.263 unitários e 802 de
  integração** contra PostgreSQL real via Testcontainers, zero falhas. Frontend: **503 testes em 82
  arquivos**, verdes. `ModularityTest` verde, inclusive nos dois pontos em que ele recusou um ciclo
  (ver DEC-CLN-001 e DEC-FDS-002). A sprint começou com 786 testes de integração e terminou com 802.
- **Migration aplicada:** `V110` a `V118` — 118 migrations no total. Nenhuma destrutiva; a de maior
  risco (`V117`, mão de obra) cria tabela nova e não toca em coluna existente.
- **Contratos atualizados:** `contracts/openapi.yaml` — **256 caminhos**, três a mais que no encerramento
  da Sprint 16.
- **Riscos remanescentes:**
  - **REL-001 não fecha.** O RTO está medido e o ensaio voltou ao repositório (DEC-REL-011); a política
    de backup existe e fixou o RPO em 5 minutos (DEC-REL-017). **Falta medir o RPO**, o que exige o WAL
    archiving rodando — e ambiente. O risco deixou de ser "ninguém sabe se o backup restaura" e passou a
    ser "ninguém exercitou a recuperação a ponto no tempo, que é a que a política promete".
  - **REL-005 não fecha.** O manual existe e o roteiro existe; o ciclo em homologação, não. Um manual
    que nunca foi seguido de ponta a ponta é hipótese escrita com capricho. Ver DEC-REL-010.
    **Reduzido em 2026-08-23 a 26, não fechado:** 17 das 25 linhas do roteiro são percorridas inteiras
    pela tela a cada build e outras 5 em parte, o que encolhe o ciclo manual — mas as 2 linhas provadas
    só pelo backend continuam dependendo de gente, e o ciclo em si continua dependendo de ambiente e de
    quem opera. Ver DEC-REL-012 a DEC-REL-015.
  - **O ensaio da REL-004 rodou em ambiente local**, não em cópia de produção. As medidas de bloqueio de
    escrita são reais, mas o volume de dados não é o de produção. Ver DEC-REL-009.
  - **Oito atualizações do Dependabot** (#204 a #211) ficaram armadas com auto-merge no encerramento, não
    mergeadas. As duas de maior alcance foram verificadas localmente antes: `anthropic-java` 2.34→2.53
    (96 testes do módulo `ai`, sem dependência nova na árvore) e `testcontainers-keycloak` 3.7→4.3
    (8 testes de SSO; a imagem embutida vai de Keycloak 26.2 a 26.7, mesmo major).
  - **O portão de CVE passou a ser verificado, e não só configurado.** O PR #225 é um canário
    descartável com `commons-text:1.9` (CVE-2022-42889) que existe para provar que o job **falha** —
    porque um PR limpo passando não distingue um portão que funciona de um portão aberto.
- **Aceite:** pendente de validação manual. Junto com o aceite das Sprints 09 e 16, também pendentes.

### O que esta sprint ensinou, e que vale carregar

**Débito antigo descreve o mundo do dia em que foi escrito.** Foi o padrão que mais apareceu, e três
vezes o critério de remoção prescrevia a solução errada:

- `CLN-004-A` pedia um *listener*; implementado assim, ele criava um ciclo entre módulos que não existia
  quando o débito foi redigido.
- `DEB-AIA-003` já estava um terço resolvido pela PRM-001, feita depois dele.
- `QLT-001-A` esperava um agendador que existia desde a Sprint 13.

A consequência prática: **ler o débito é o primeiro passo, conferir se o mundo dele ainda existe é o
segundo** — e o segundo não é opcional. Nos dois casos de ciclo, quem corrigiu o rumo foi o
`ModularityTest`, não o julgamento de quem escrevia o código. Está registrado no ADR-0016.

**Teste com data fixa não é determinístico, é datado.** Dois apareceram: um quebrou o build em
2026-08-14, e a varredura que ele motivou encontrou outros onze com prazo para 2026-08-20. A regra está
em `docs/12_TESTING_STRATEGY.md`.
