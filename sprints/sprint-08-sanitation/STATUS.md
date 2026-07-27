# Status — Sprint 08

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| CLN-001 | Concluída | IA | (local) | POP versionado (módulo novo `sanitation`): rascunho editável → publicar congela a versão (imutável; o ciclo referenciará a versão); editar publicado gera nova versão; só um rascunho por código. Etapas com campos tipados (método, produto, concentração/temperatura min≤max, tempo, vazão, EPIs, alternativa, proibição, evidência). `POST/GET/PUT /sanitation/procedures` + `/{id}/publish`. Migration V56; permissões `sanitation.procedure.read/manage`. UI: cadastro de POP com etapas + lista/publicar. Backend +8 testes; frontend +3. |
| CLN-002 | A fazer | — | — | — |
| CLN-003 | A fazer | — | — | — |
| CLN-004 | A fazer | — | — | — |
| CLN-005 | A fazer | — | — | — |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

### CLN-001 — decisões (confirmadas com o mantenedor)
- **Versionamento tipo receita**: POP nasce **DRAFT** (editável); **publicar** congela a versão (imutável — o ciclo/execução referenciará a versão). Editar publicado → **nova versão** (POST com o mesmo código, versão+1). Só **um rascunho por código** (segundo rascunho → 409).
- **Limites com campos tipados** por etapa: método, produto, concentração % (min/max), temperatura °C (min/max), tempo, vazão/ação mecânica, EPIs, alternativa, proibição, evidência exigida. Faixas com `min ≤ max` (domínio + CHECK).
- **"Parâmetro fora da ficha bloqueado"** será aplicado na **execução do ciclo (CLN-003)** contra a versão publicada — aqui a versão apenas armazena os limites. Módulo novo `sanitation` (sem ciclos). Permissões `sanitation.procedure.read/manage` (V56).

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
