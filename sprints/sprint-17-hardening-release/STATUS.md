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

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
