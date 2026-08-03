# Aceite — Sprint 10

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
PKG-001 disponibilidade e limpeza verificadas antes da reserva, com a embalagem reservada no
estoque; GAS-001 cilindro vencido ou bloqueado não é alocado e o teste de vazamento é registrado;
PKG-002 temperatura e CO₂ residual entram no cálculo e a confirmação é obrigatória; PKG-003 perda
derivada com balanço que fecha por construção e consumo de embalagem virando movimento de estoque;
FSL-001 oxigênio medido com TPO ≥ DO e validade recomendada a partir da política da cervejaria;
GAS-002 resistência e comprimento calculados a partir da pressão de equilíbrio, sem ajuste
automático; PKG-004 rótulo montado só de fontes rastreáveis, com campo obrigatório ausente
barrando a impressão.

**Nenhuma história posterior parcial** — o que dependia de decisão ou de sprint futura ficou de
fora e está nomeado em "Decisões e bloqueios" do `STATUS.md`: custo e estoque de gás (sprint 13),
pressão máxima por embalagem, prazo de validade do CIP, alergênicos no catálogo e ABV medido.

**Testes** — 513 unitários + 376 de integração (Testcontainers com PostgreSQL 18), todos verdes
em `./mvnw verify` na `main` depois dos sete merges; 229 no frontend. As sete suítes de integração
novas têm teste negativo de autorização (403) e de isolamento entre cervejarias.

**Contratos e migrations** — `contracts/openapi.yaml` foi de 94 para 129 paths, cobrindo os
endpoints das sete histórias; migrations V67→V73 em sequência contínua, todas com constraints e
índices. Sete códigos de Problem Details novos (`packaging_blocked`, `over_carbonation`,
`volume_balance`, `batch_volume_exceeded`, `label_not_printable`, `insufficient_packaging_stock`,
`above_network_limit`). *Eventos: esta sprint não emitiu evento de domínio* — a comunicação entre
módulos usa consulta publicada (`BatchLookup`, `CleaningReleaseLookup`,
`EquipmentAvailabilityLookup`) e porta de aplicação (`PackagingStockGateway`), conforme
`AGENTS.md`. Onze handlers registram auditoria.

**Frontend** — as duas telas novas (planos de envase, gases e CO₂) tratam carregando, vazio, erro,
conflito (mensagem específica por 409, incluindo os bloqueios agregados de `packaging_blocked`) e
acesso negado (`permissionGuard` na rota + ações escondidas sem permissão).

**Observabilidade** — `RequestTraceIdFilter` + MDC preenchem o `traceId` em log estruturado,
auditoria e Problem Details; vale para os endpoints novos sem trabalho adicional.

## Definition of Done — 11 de 12

Falta **"Fluxo principal E2E aprovado"**: o projeto continua sem harness de e2e (`e2e/` só contém
`README.md`), a mesma lacuna registrada na sprint 09. Não é lacuna introduzida aqui, mas também
não foi resolvida aqui — montar o harness é trabalho próprio.

Os demais 11 itens estão atendidos: domínio sem framework, unitários de invariante/limite/
transição inválida, integração com Testcontainers, autorização e tenant negativos, migrations com
constraints e índices revisados, OpenAPI e Problem Details, auditoria sem dado sensível, estados
de UI, nenhum TODO/segredo/código morto nos módulos novos, e decisões registradas.

## Aberto para o mantenedor

1. **Os dois débitos de PKG-004 afetam rótulo impresso**, não só código. `PKG-004-A`: alergênico
   não tem fonte no catálogo, então configurar a regra da casa exigindo esse campo **barra a
   impressão** até o dado existir. `PKG-004-B`: o ABV vem da receita publicada e sai marcado como
   "calculado, não medido" — é honesto, mas convém confirmar se é o que a cervejaria quer no
   rótulo. Recomendo decidir os dois antes de usar rótulo em produção.
2. **Validade do CIP (`PKG-001-A`)** — a regra hoje exige liberação anterior ao início planejado e
   posterior ao último uso da linha, mas não expira por tempo. Definir quantas horas uma liberação
   cobre sem novo uso é decisão do POP.
3. **Pressão máxima por embalagem (`PKG-002-A`)** — o sistema bloqueia o caso claro (priming sem
   espaço para o alvo), mas não sabe o limite de lata, long neck e garrafa de champanhe. Enquanto
   o catálogo não tiver o dado, um alvo alto em embalagem frágil passa.
4. **Modelo de custo do gás (`GAS-001-A`)** e **periodicidade da requalificação (`GAS-001-B`)** —
   ambos dependem de definição de negócio e de norma, e estão fora do que a sprint 10 se propôs.
5. **Verificação visual feita, com três achados cosméticos que não são desta sprint.** As duas
   telas foram conferidas em tema claro e escuro, a 1440px e a 390px, com dados povoados. Layout,
   paleta e estados batem com o tema em todas as combinações. O que apareceu:
   - `btn-outline-secondary` no escuro tem contraste de **2,1:1** (texto `#475569` sobre card
     `#1b232d`), abaixo do mínimo AA de 4,5:1. Atinge "Cancelar" e "Desbloquear" aqui, mas está
     em 15 templates do app e não tem override em `styles.scss` — é lacuna do tema.
   - `datetime-local` em `col-sm-2` trunca o valor no desktop (`05/08/2026, 09:(`), colidindo com
     o ícone do seletor; no mobile aparece inteiro. Cabe subir para `col-sm-3`.
   - `<code>` sai em `#d63384` (padrão Bootstrap) ao lado de um resultado de cálculo e parece
     erro; também aparece em `calculators`, `service-accounts` e `federation`.

   Nenhum bloqueia uso e nenhum é regressão da sprint 10, então não foram corrigidos aqui —
   o contraste do botão toca 15 telas e merece trabalho próprio.
