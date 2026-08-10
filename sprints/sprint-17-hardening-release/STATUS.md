# Status — Sprint 17

Estado: NÃO INICIADA

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| REL-001 | Artefato pronto, execução pendente | Claude | `infra/backup/restore-drill.sh`, `table-counts.sql` | Script executado ponta a ponta contra o banco local: dump, restauração isolada, conferência e relatório. Falta rodar contra **backup de produção** — só então RPO/RTO são reais. Ver DEC-REL-004. |
| REL-002 | Concluída | Claude | `infra/perf/*`, `ListBatchesUseCase`, `JdbcBatchRepository`, `PageResponse` | Gargalo medido **e corrigido**: com 3.000 lotes, p95 caiu de 319 ms para 9,8 ms. Ver DEC-REL-005 e DEC-REL-007. |
| REL-003 | Concluída | Claude | `InternalAddressGuard`, `SecurityConfiguration`, `.github/workflows/ci.yml`, `frontend/package-lock.json` | Um achado ALTO (SSRF no webhook) e dois médios resolvidos; varredura de CVE passou a barrar merge. Ver DEC-REL-001/002/003. |
| REL-004 | Runbook pronto, ensaio pendente | Claude | `infra/runbooks/deploy-rollback.md` | Árvore de decisão, forward-fix e o registro de ensaios. Fecha com pelo menos uma linha na tabela de ensaios. Ver DEC-REL-006. |
| REL-005 | A fazer | — | — | — |

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

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
