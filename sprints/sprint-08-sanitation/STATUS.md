# Status — Sprint 08

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| CLN-001 | Concluída | IA | (local) | POP versionado (módulo novo `sanitation`): rascunho editável → publicar congela a versão (imutável; o ciclo referenciará a versão); editar publicado gera nova versão; só um rascunho por código. Etapas com campos tipados (método, produto, concentração/temperatura min≤max, tempo, vazão, EPIs, alternativa, proibição, evidência). `POST/GET/PUT /sanitation/procedures` + `/{id}/publish`. Migration V56; permissões `sanitation.procedure.read/manage`. UI: cadastro de POP com etapas + lista/publicar. Backend +8 testes; frontend +3. |
| CLN-002 | Concluída | IA | (local) | Matriz de compatibilidade: regra por **material × sujidade × risco × produto anterior** (vocabulário fechado nos três primeiros; produto anterior texto opcional, normalizado minúsculo ou nulo=genérica). Regra referencia opcionalmente um **POP publicado** + método/alternativa/restrição em texto. Recomendação por **material exato (sem herança entre materiais)**: prefere a regra com produto anterior específico, senão a genérica; sem regra → 400. `GET/POST /sanitation/matrix` + `POST /sanitation/matrix/recommend`. Migration V57 (chave única + CHECK de vocabulário); permissões `sanitation.matrix.read/manage`. UI: recomendação + cadastro de regras + lista. Backend +4 (IT) +3 (domínio). |
| CLN-003 | A fazer | — | — | — |
| CLN-004 | A fazer | — | — | — |
| CLN-005 | A fazer | — | — | — |

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

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
