# Status — Sprint 08

Estado: CONCLUÍDA

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| CLN-001 | Concluída | IA | (local) | POP versionado (módulo novo `sanitation`): rascunho editável → publicar congela a versão (imutável; o ciclo referenciará a versão); editar publicado gera nova versão; só um rascunho por código. Etapas com campos tipados (método, produto, concentração/temperatura min≤max, tempo, vazão, EPIs, alternativa, proibição, evidência). `POST/GET/PUT /sanitation/procedures` + `/{id}/publish`. Migration V56; permissões `sanitation.procedure.read/manage`. UI: cadastro de POP com etapas + lista/publicar. Backend +8 testes; frontend +3. |
| CLN-002 | Concluída | IA | (local) | Matriz de compatibilidade: regra por **material × sujidade × risco × produto anterior** (vocabulário fechado nos três primeiros; produto anterior texto opcional, normalizado minúsculo ou nulo=genérica). Regra referencia opcionalmente um **POP publicado** + método/alternativa/restrição em texto. Recomendação por **material exato (sem herança entre materiais)**: prefere a regra com produto anterior específico, senão a genérica; sem regra → 400. `GET/POST /sanitation/matrix` + `POST /sanitation/matrix/recommend`. Migration V57 (chave única + CHECK de vocabulário); permissões `sanitation.matrix.read/manage`. UI: recomendação + cadastro de regras + lista. Backend +4 (IT) +3 (domínio). |
| CLN-003 | Concluída | IA | (local) | Execução de ciclo: referencia **POP publicado** + **equipamento existente** (validado via `EquipmentProfileLookup`) e **congela um snapshot** das etapas/faixas ao iniciar. State machine IN_PROGRESS→INTERRUPTED→IN_PROGRESS→COMPLETED. **Parâmetro fora da ficha é bloqueado** (invariante adiado da CLN-001): concentração/temperatura fora do intervalo e tempo abaixo do dwell mínimo → 400; **override com alçada** (`sanitation.cycle.override`) + justificativa registra o desvio (auditado). Etapa **fora de ordem exige motivo**; **interrupção preservada** e retomável; conclusão exige todas as etapas. `POST /cycles` `/steps` `/interrupt` `/resume` `/complete` + `GET`. Migration V58; permissões `sanitation.cycle.read/execute/override`. UI: lista + início + tela de execução (registrar etapa/override/interromper/retomar/concluir). Backend +11 (domínio) +5 (IT). |
| CLN-004 | Concluída | IA | (local) | Verificar e liberar: um ciclo **COMPLETED** recebe checagens tipadas (enxágue/visual/**ATP** RLU≤limite/micro); **liberação exige verificação aprovada** — não passa com limpeza reprovada (409); reprovado vai a **REJECTED**. Dois passos: `POST /verification` (calcula aprovado/reprovado) → `POST /release` (→RELEASED) ou `POST /reject` (→REJECTED). Liberação publica evento **`CleaningCycleReleased`** (listener no equipment = débito **CLN-004-A**). Migration V59 (colunas + CHECK ampliado); permissão crítica `sanitation.cycle.release`. UI: bloco verificação/liberação na tela de execução. Backend +4 (domínio, total 15) +4 (IT). |
| CLN-005 | Concluída | IA | (local) | Consumo e otimização: mede **água (L)/energia (kWh)/produto (kg)** por ciclo com **execução encerrada** (COMPLETED/RELEASED/REJECTED); IN_PROGRESS/INTERRUPTED → 409; um registro por ciclo, **upsert**. **Comparação read-only** por POP (`GET /consumption/summary` — média/mín/máx) — **não reduz parâmetro do POP** (reduzir limite exige nova versão publicada, CLN-001). `POST /cycles/{id}/consumption`. Migration V60 (colunas agregadas); permissões `sanitation.consumption.read/manage`. UI: bloco consumo/comparação na tela de execução. Backend +3 (domínio, total 18) +4 (IT). |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

### CLN-001 — decisões (confirmadas com o mantenedor)
- **Versionamento tipo receita**: POP nasce **DRAFT** (editável); **publicar** congela a versão (imutável — o ciclo/execução referenciará a versão). Editar publicado → **nova versão** (POST com o mesmo código, versão+1). Só **um rascunho por código** (segundo rascunho → 409).
- **Limites com campos tipados** por etapa: método, produto, concentração % (min/max), temperatura °C (min/max), tempo, vazão/ação mecânica, EPIs, alternativa, proibição, evidência exigida. Faixas com `min ≤ max` (domínio + CHECK).
- **"Parâmetro fora da ficha bloqueado"** será aplicado na **execução do ciclo (CLN-003)** contra a versão publicada — aqui a versão apenas armazena os limites. Módulo novo `sanitation` (sem ciclos). Permissões `sanitation.procedure.read/manage` (V56).

### CLN-002 — decisões (confirmadas com o mantenedor)
- **Chave da matriz**: material + sujidade + risco + **produto anterior**. Material/sujidade/risco são enums fechados (INOX/ALUMINIO/PLASTICO/MADEIRA/VIDRO/BORRACHA; LEVE/MODERADA/PESADA; BAIXO/MEDIO/ALTO). Produto anterior é texto livre **opcional**, normalizado (trim+minúsculo) ou nulo = regra **genérica**. Unicidade por essa chave (V57 `uq_sanitation_rule_key`, COALESCE do produto anterior no `existsKey`).
- **Recomendação = referência opcional a POP publicado + método/alternativa/restrição em texto.** Se `procedureCode` informado, o POP precisa estar **PUBLISHED** (senão 400).
- **Sem herança entre materiais**: a recomendação casa o **material exato** do contexto; entre as regras daquele material/sujidade/risco, prefere a de **produto anterior específico**, senão a **genérica**; nenhuma regra → 400 (madeira/plástico não herdam inox).

### CLN-003 — decisões (confirmadas com o mantenedor)
- **Parâmetro fora da ficha bloqueia, com override de alçada**: sem override, concentração/temperatura fora do intervalo congelado e tempo abaixo do dwell mínimo → 400. Com `override=true`, exige justificativa e a permissão **`sanitation.cycle.override`** (crítica) — sem ela, 403; o desvio fica auditado (`overridden`/`overrideReason`). Cobre o risco "override sem alçada".
- **Alvo do ciclo = equipamento existente**: o ciclo referencia um `equipmentId` real, validado via `EquipmentProfileLookup` (existência + tenant); inexistente → 400. Base para "bloqueio de equipamento" (CLN-004).
- **Snapshot imutável no início**: ao iniciar, copia etapas/faixas da versão **publicada** para o ciclo; a validação usa o snapshot, não o POP vivo (fidelidade mesmo que outra versão seja publicada depois).
- Demais invariantes vêm do aceite: **fora de ordem exige motivo**; **interrupção preservada** (INTERRUPTED retomável); conclusão exige todas as etapas; `FOR UPDATE` para concorrência; auditoria por comando (evento fica para CLN-004, quando aplicável).
- **Nota de UI**: o `override` no request é `Boolean` (não primitivo) — Jackson falha ao desserializar um `boolean` ausente em record; `overrideRequested()` normaliza null→false.

### CLN-004 — decisões (confirmadas com o mantenedor)
- **Checagens tipadas**: enxágue (ok), inspeção visual (ok), **ATP** (leitura RLU + limite → aprovado se RLU ≤ limite) e micro (ok). A verificação só "passa" com as quatro aprovadas. As flags booleanas no request são `Boolean` fail-closed (ausente = reprovado).
- **Dois passos**: `POST /verification` registra as checagens e calcula aprovado/reprovado (re-registrável enquanto COMPLETED); `POST /release` só com verificação **aprovada** (senão 409 — não passa com limpeza reprovada); `POST /reject` (verificação registrada) → REJECTED. Estado exigido: **COMPLETED**.
- **Evento agora, integração como débito**: a liberação publica `CleaningCycleReleased` (auditada). Permissão crítica `sanitation.cycle.release` (release + reject); verificação sob `sanitation.cycle.execute`.

### CLN-005 — decisões (confirmadas com o mantenedor)
- **Consumo agregado**: água (L), energia (kWh) e produto/químico (kg) — três números por ciclo. Consumo detalhado por item de produto fica como débito **CLN-005-A**.
- **Comparação read-only**: `GET /sanitation/consumption/summary?procedureCode=` agrega média/mín/máx por POP. **Nenhum endpoint altera limites do POP** — reduzir parâmetro exige criar+publicar nova versão (CLN-001), satisfazendo "comparação não reduz parâmetro sem nova versão aprovada" estruturalmente.
- **Registro**: permitido em ciclo com execução encerrada (COMPLETED/RELEASED/REJECTED), **upsert** (re-registrável para correção); IN_PROGRESS/INTERRUPTED → 409. Permissões `sanitation.consumption.read/manage`.

### Débitos técnicos
- **CLN-004-A** — Reação do módulo `equipment` ao evento `CleaningCycleReleased` (desbloquear/marcar equipamento como limpo). Critério de remoção: quando existir estado de bloqueio de equipamento, um listener em `equipment` consome o evento e atualiza o equipamento; hoje o evento é publicado sem consumidor.
- **CLN-005-A** — Consumo detalhado por item de produto/químico (produto+quantidade+unidade) em tabela filha, além do agregado `product_kg`. Critério de remoção: quando o relatório de otimização precisar discriminar por químico.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
