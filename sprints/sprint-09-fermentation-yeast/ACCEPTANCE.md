# Aceite — Sprint 09

Marcado pela IA com base em verificação objetiva; o que depende de julgamento do mantenedor
está explicitamente aberto no fim.

- [x] Todas as histórias selecionadas atendem critérios específicos.
- [x] Nenhuma história posterior foi implementada parcialmente.
- [x] Testes de domínio, integração, autorização e tenant estão verdes.
- [x] OpenAPI, migrations, eventos e documentação estão consistentes.
- [x] Frontend trata loading, vazio, erro, conflito e acesso negado.
- [x] Observabilidade permite localizar a operação por traceId.
- [ ] `.ai/DEFINITION_OF_DONE.md` foi executado. *(11 de 12 itens; ver abaixo)*
- [x] Débitos e decisões restantes foram registrados, não escondidos em TODO.

## Evidência por item

**Critérios específicos** — cada história cobre os critérios do `BACKLOG.md`:
FER-001 versionamento congelado; FER-002 sinalização em vez de recusa + idempotência;
FER-003 parecer explicável que não encerra + FG falso estável; FER-004 condição/janela/ação/
tolerância/responsável, prévia antes do recálculo, alerta sem efeito colateral, planejado ×
executado no histórico; YST-001 genealogia completa e reprovada indisponível; YST-002
recomendação explicável e uso com confirmação e lote vinculado.

**Nenhuma história posterior parcial** — o que dependia de sprint futura ficou de fora e está
nomeado em "Decisões e bloqueios" do `STATUS.md` (atenuação real do lote de origem, consumo
parcial de levedura, conversão PLATO→SG).

**Testes** — 397 unitários + 280 de integração (Testcontainers com PostgreSQL 18), todos
verdes em `./mvnw verify`; 186 no frontend. Toda história tem teste negativo de autorização
(403) e de isolamento entre cervejarias.

**Contratos e migrations** — `contracts/openapi.yaml` cobre os endpoints das seis histórias;
migrations V61→V66 em sequência contínua, com checks e índices. *Eventos: esta sprint não
emitiu evento de domínio* — a comunicação entre módulos usa consulta publicada
(`BatchLookup`) e porta de aplicação (`BatchAlertPublisher`), conforme `AGENTS.md`.

**Frontend** — as quatro telas novas (perfis, leituras, coletas, linha do tempo) tratam
carregando, vazio, erro, conflito (mensagem específica por 409) e acesso negado
(`permissionGuard` na rota + ações escondidas sem permissão).

**Observabilidade** — `RequestTraceIdFilter` + MDC preenchem o `traceId` em log estruturado,
auditoria e Problem Details; vale para os endpoints novos sem trabalho adicional.

## Definition of Done — 11 de 12

Falta **"Fluxo principal E2E aprovado"**: o projeto não tem harness de e2e (`e2e/` só contém
`README.md`), então nenhuma sprint poderia marcar este item hoje. Não é lacuna introduzida
aqui, mas também não foi resolvida aqui — montar o harness é trabalho próprio.

Os demais 11 itens estão atendidos: domínio sem framework, unitários de invariante/limite/
transição inválida, integração com Testcontainers, autorização e tenant negativos, migrations
com constraints revisadas, OpenAPI e Problem Details, auditoria sem dado sensível, estados de
UI, nenhum TODO/segredo/código morto, e decisões registradas.

## Aberto para o mantenedor

1. **Decisões da FER-002 seguem pendentes de confirmação** no `STATUS.md` — principalmente as
   faixas de plausibilidade fixas no domínio (SG 0,980–1,180; °C −10–45; psi 0–60; pH 2,5–7,5).
   Se alguma faixa estiver errada para a operação real, é ajuste de uma linha por grandeza.
2. **Agendador da varredura de atrasos (FER-004)** — hoje é disparada por endpoint. Definir se
   vira cron, e onde roda, é decisão de infraestrutura.
3. ~~**Candidato a ADR**: `BatchAlertPublisher` é a primeira porta de *escrita* publicada entre
   módulos (as anteriores eram consulta).~~ **Virou a `ADR-0016` em 2026-08-15.** O precedente merecia
   registro mesmo: até a sprint 16 ele era o único, e ao fechar os débitos das sprints 08 a 16
   apareceram mais cinco portas de escrita em duas semanas — uma delas na direção oposta às outras,
   porque a direção natural fechava ciclo. O ADR registra as duas formas e quando cada uma vale.
4. **Acabamento de UI pendente**: responsável, cepa e afins ainda são ids digitados à mão em
   três telas. Vale uma história própria.

## Evidências de encerramento

- Build/commit: `main` em `bc0d0c6`, PRs #108, #112, #113, #114, #115 e #116 mergeados.
- Testes executados: `./mvnw verify` (397 + 280) e `ng build` + `ng test` (186).
- Migration aplicada: V61→V66 em banco limpo, via Testcontainers em cada IT.
- Contratos atualizados: `contracts/openapi.yaml`.
- Riscos remanescentes: os quatro itens abertos acima.
- Aceite: **pendente do mantenedor.**
