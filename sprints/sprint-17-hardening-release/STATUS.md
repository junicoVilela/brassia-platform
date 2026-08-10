# Status — Sprint 17

Estado: NÃO INICIADA

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| REL-001 | Artefato pronto, execução pendente | Claude | `infra/backup/restore-drill.sh`, `table-counts.sql` | Script executado ponta a ponta contra o banco local: dump, restauração isolada, conferência e relatório. Falta rodar contra **backup de produção** — só então RPO/RTO são reais. Ver DEC-REL-004. |
| REL-002 | Artefato pronto; **um gargalo real medido** | Claude | `infra/perf/seed-representative-dataset.sql`, `measure-journeys.sh` | `/api/v1/production/batches` não pagina e cruza a meta de 500 ms por volta de 4.700 lotes — medido, não extrapolado. Ver DEC-REL-005. |
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

### OBS-REL-001 (REL-003) — Isolamento multi-tenant depende da aplicação, não do banco

Varri os 157 esquemas (125 com `brewery_id`) contra todo SQL do código: **10 escritas** tocam tabela de
tenant filtrando só por id. Conferi as 10 uma a uma — **todas** têm guarda no handler, que carrega a
entidade com escopo antes (`findVisible(breweryId, id)`, `requireProvider`, `assertEditable`). **Nenhuma
vulnerabilidade.**

Mas a garantia mora inteira na disciplina de cada handler lembrar de carregar antes. Um handler novo que
receba o id do path e chame o repositório direto vaza entre cervejarias, e nada — nem tipo, nem teste, nem
restrição de banco — impede isso de ser escrito. É o mesmo tipo de fragilidade que as histórias da Sprint 16
trataram tornando estrutural.

**Não corrigi**: mudar isso é acrescentar `brewery_id` a dez `WHERE` (barato) ou adotar RLS no PostgreSQL
(caro, e decisão arquitetural que merece ADR). Fica registrado com critério de remoção: ou os dez `WHERE`
recebem o filtro redundante, ou um ADR decide por RLS.

### O que foi verificado e estava correto

Não gerou achado, e vale registrar para a próxima revisão não refazer: **autorização em todos os endpoints**
(as exceções — login, recuperação, callback SSO, `ServiceProviderConfig` do SCIM — são `permitAll` por
desenho e documentadas); **XXE fechado** no único ponto que faz parse de XML (`disallow-doctype-decl` no
BeerXML); **cookies** com `HttpOnly`, `Secure` e `SameSite`; **limitação de tentativas de login** por e-mail
e IP; **segredos** vindos de variável de ambiente, sem valor padrão embutido; **redirecionamento de webhook**
recusado.


### DEB-REL-001 — `dependency-review` não-bloqueante até habilitar o Dependency graph

O job acrescentado por `DEC-REL-003` **falhou no próprio PR que o introduziu** — e não por ter encontrado
CVE: a action exige o recurso *Dependency graph* do GitHub, que está desabilitado neste repositório.

Deixá-lo bloqueante travaria todo PR por um motivo que nada no código resolve, e a reação previsível seria
remover o job — trocando um controle imperfeito por controle nenhum. Ficou `continue-on-error: true`.

**Não habilitei o recurso**: é configuração da conta do GitHub, fora do que me foi autorizado (git, não
administração do repositório).

**Critério de remoção:** habilitar *Settings > Code security and analysis > Dependency graph* e tirar o
`continue-on-error`. Só então o job barra de fato.

**Enquanto isso, o repositório também está com** `secret_scanning`, `secret_scanning_push_protection` e
`dependabot_security_updates` **desabilitados** — os três são gratuitos em repositório público. O
`gitleaks` no CI cobre parte do primeiro, mas só no que passa pelo pipeline; *push protection* age antes,
que é onde um segredo vazado ainda dá para conter.

### DEC-REL-004 (REL-001) — O ensaio existe e roda; o número ainda não é de produção

`infra/backup/restore-drill.sh` faz dump, sobe um PostgreSQL isolado, restaura cronometrando, confere
integridade e escreve relatório. **Executado ponta a ponta** contra o banco local: schema idêntico, zero
divergências.

Três coisas que só apareceram por rodar o script em vez de escrevê-lo:

- **`n_live_tup` não serve para conferir integridade.** É estimativa do autovacuum, e vem **zerada** num
  banco recém-restaurado — o ensaio acusaria divergência em todas as tabelas, sempre. Alarme que sempre
  dispara é alarme que se aprende a ignorar. Trocado por `COUNT(*)` exato via `query_to_xml`.
- **Exigir cliente PostgreSQL no host é barreira real** — não estava instalado. As ferramentas passaram a
  rodar em container da mesma imagem do servidor, o que ainda garante que a versão do cliente casa com a
  do servidor: `pg_dump` de versão menor recusa rodar, e é o tipo de detalhe que aparece no dia da
  restauração de emergência.
- **Senha com caractere especial quebra a URL.** `brassia85!@#` faz o parser ler `#@localhost` como host,
  e o erro ("could not translate host name") não aponta para a senha. Documentado no cabeçalho.

**O que falta para REL-001 fechar:** rodar contra um **backup de produção**. RPO e RTO medidos sobre um
banco praticamente vazio não são RPO e RTO — o relatório diz isso explicitamente em vez de apresentar
"1 segundo" como se fosse a resposta.

### DEC-REL-005 (REL-002) — `/api/v1/production/batches` não pagina, e a meta cai perto de 4.700 lotes

Medido, não estimado:

| Lotes | p95 |
|---|---|
| 300 | 40 ms |
| 3.000 | **319 ms** |

Linear, ~0,106 ms por lote: a meta de 500 ms (`docs/15_NONFUNCTIONAL_REQUIREMENTS.md`) cai por volta de
**4.700 lotes**. Uma cervejaria com três brassagens por dia chega lá em poucos anos; uma cervejaria cigana
chega antes. Para comparar: `audit_event` com **121 mil linhas** responde em 5 ms — porque pagina.

**Não corrigi**, e a razão não é esforço: mudar a resposta de `Batch[]` para página é **quebra de
contrato**, e `docs/20_RELEASE_MIGRATION.md` exige versão nova e janela de transição. É uma história, não
um ajuste de hardening — e cabe a você decidir se entra antes do release ou vira dívida com prazo.

O medidor marca `VAZIO` quando a tabela tem menos de mil linhas. Sem isso, o relatório mostraria sete
linhas verdes das quais seis mediram tabela vazia — o "número bonito e inútil" que o próprio gerador de
dataset existe para evitar. Verde sobre tabela vazia não é evidência, e o relatório passou a dizer isso.

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
honesto — significa que nenhum ensaio foi feito.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
