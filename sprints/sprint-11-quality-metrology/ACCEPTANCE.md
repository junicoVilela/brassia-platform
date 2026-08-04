# Aceite — Sprint 11

Marcado pela IA com base em verificação objetiva; o que depende de julgamento do mantenedor
está explicitamente aberto no fim.

- [x] Todas as histórias selecionadas atendem critérios específicos.
- [x] Nenhuma história posterior foi implementada parcialmente.
- [x] Testes de domínio, integração, autorização e tenant estão verdes.
- [x] OpenAPI, migrations, eventos e documentação estão consistentes.
- [x] Frontend trata loading, vazio, erro, conflito e acesso negado.
- [x] Observabilidade permite localizar a operação por traceId.
- [ ] `.ai/DEFINITION_OF_DONE.md` foi executado. *(12 de 12 — ver abaixo)*
- [x] Débitos e decisões restantes foram registrados, não escondidos em TODO.

## Escopo entregue: cinco histórias, não seis

O `AI_EXECUTION.md` — documento que rege a execução — lista **MTR-001, MTR-002, QLT-001, QLT-002
e SEN-001**. README, BACKLOG e STATUS listavam também SEN-002. A divergência foi levantada antes
de começar e resolvida pelo mantenedor: **SEN-002 fica para uma sprint futura**, porque um dos seus
critérios ("conteúdo licenciado respeita atribuição e nível de permissão") depende de decisão sobre
qual catálogo sensorial usar e sob que licença — decisão que não é de engenharia.

As cinco entregues formam um ciclo fechado, e é isso que sustenta o aceite: o instrumento é
cadastrado com aptidão derivada, a leitura é corrigida sem apagar o bruto, o plano de controle
julga a medição verificando o instrumento no ponto crítico, o desvio que nasce dali só se encerra
quando o CAPA prova eficácia, e a sessão sensorial avalia o produto às cegas.

## Evidência por item

**Critérios específicos** — cada história cobre os critérios do `BACKLOG.md`:
MTR-001 faixa/resolução/precisão com instrumento vencido bloqueando ponto crítico e certificado
preservado; MTR-002 correção por temperatura e curva mostrando fórmula e versão, com o original
imutável; QLT-001 parâmetro/faixa/frequência/ação por produto e etapa, com medição fora da faixa
abrindo desvio conforme a severidade; QLT-002 conter/investigar/agir/verificar com status e prazos
controlados e encerramento exigindo verificação; SEN-001 amostras cegas, ficha, resultado e
comparação, com resultado invisível antes do fechamento e vínculo ao lote preservado.

**Nenhuma história posterior parcial** — SEN-002 ficou inteiramente de fora (os descritores da
SEN-001 são texto livre, não uma biblioteca pela metade). O tratamento do desvio ficou todo em
QLT-002, e não foi antecipado em QLT-001.

**Testes** — 628 unitários + 441 de integração (Testcontainers com PostgreSQL 18), verdes em
`./mvnw verify`; 257 no frontend. As três suítes de integração novas têm teste negativo de
autorização (403) e de isolamento entre cervejarias (`naoEnxergaInstrumentoDeOutraCervejaria`,
`naoEnxergaPlanoDeOutraCervejaria`, `naoEnxergaNaoConformidadeDeOutraCervejaria`,
`naoEnxergaSessaoDeOutraCervejaria`).

**Contratos e migrations** — `contracts/openapi.yaml` de 129 para 165 paths (+36); migrations
V74→V78 em sequência contínua, todas com constraints e índices. Dez códigos de Problem Details
novos. *Eventos: esta sprint não emitiu evento de domínio* — a comunicação entre módulos usa
consulta publicada (`InstrumentStatusLookup`, `BatchLookup`) e porta de aplicação
(`BatchAlertPublisher`), conforme `AGENTS.md`.

**Frontend** — as três telas novas (instrumentos, planos de controle, sessões sensoriais) tratam
carregando, vazio, erro, conflito (mensagem específica por 409 em cada recusa: instrumento não
apto, fase fora de ordem, resultado indisponível) e acesso negado (`permissionGuard` na rota +
ações escondidas sem permissão).

**Observabilidade** — `RequestTraceIdFilter` + MDC preenchem o `traceId` em log estruturado,
auditoria e Problem Details; vale para os endpoints novos sem trabalho adicional.

## Definition of Done — 12 de 12

**Pela primeira vez o item de E2E é atendido.** O harness de Playwright entrou fora de sprint (PR
#130) e esta sprint acrescentou três jornadas — instrumentos, planos de controle e sessões
sensoriais. O job `E2E (Playwright, stack real)` roda na CI a cada PR, contra a aplicação
empacotada com PostgreSQL.

Deixo o item **desmarcado no topo mesmo assim**: o E2E cobre navegação, sessão e integração
frontend↔API, mas não percorre o fluxo de negócio ponta a ponta (montar plano, medir fora da
faixa, abrir NC, tratar e encerrar). Marcar "fluxo principal E2E aprovado" com o que existe hoje
seria generoso demais com a própria entrega. É julgamento, e por isso vai para a sua decisão.

Os demais 11 itens estão atendidos: domínio sem framework, unitários de invariante/limite/transição
inválida, integração com Testcontainers, autorização e tenant negativos, migrations com constraints
revisadas, OpenAPI e Problem Details, auditoria sem dado sensível — e aqui com cuidado extra, a
auditoria da SEN-001 não carrega nota nem o par código↔lote —, estados de UI, nenhum TODO/segredo/
código morto nos módulos novos, e decisões registradas.

## Aberto para o mantenedor

1. ~~**`PRM-001` — a tela de parametrização que você propôs.**~~ **Entregue** antes da sprint 12,
   em dois PRs: backend (#139, migration V79) e tela (#140, `/settings/parameters`). Fechou
   `PKG-001-A`, `GAS-001-B`, `QLT-002-A` e a periodicidade de calibração da MTR-001.
2. **`QLT-001-A` — frequência declarada, não fiscalizada.** Depende do agendador ausente desde
   FER-004; é o débito mais antigo ainda vivo na plataforma.
3. **`QLT-002-A` — prazos do CAPA informados, não derivados da severidade.** Cai na PRM-001.
4. **`MTR-001-B` — "aprovado com restrição" não estreita a faixa automaticamente.** Interpretar a
   restrição exigiria parsear texto livre e inventar semântica.
5. **`SEN-002` adiada**, pendente da decisão sobre catálogo licenciado.
6. ~~**Verificação visual das três telas novas não foi feita.**~~ **Feita** junto com a tela da
   PRM-001 — cinco telas (instrumentos, planos de controle, sessões sensoriais, parametrização e o
   hub de configurações), em tema claro e escuro, desktop (1440) e mobile (390). Desta vez contra a
   **aplicação real** com backend e banco, via Playwright, em vez do harness estático de HTML
   replicado: o que se inspeciona passa a ser a tela, não uma imitação dela. Quatro achados
   corrigidos, listados no `STATUS.md`.

## Débitos abertos ao fim da sprint

Três com identificador: **QLT-001-A** (frequência não fiscalizada), **QLT-002-A** (prazos do CAPA)
e **MTR-001-B** (restrição do certificado). **MTR-001-A foi aberto e fechado dentro da própria
sprint** — a designação de ponto crítico criada em MTR-001 virou regra executável em QLT-001, no
momento da medição.

Herdados e ainda abertos: `PKG-001-A`, `PKG-002-A`, `GAS-001-A`, `GAS-001-B`, `PKG-004-A` e
`PKG-004-B` da sprint 10, e `CLN-004-A` da sprint 08. Quatro deles entram na PRM-001.
