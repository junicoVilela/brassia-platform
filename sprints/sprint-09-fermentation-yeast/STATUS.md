# Status — Sprint 09

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| FER-001 | Concluída | IA | (local) | Perfil de fermentação versionado (módulo novo `fermentation`): DRAFT editável → publicar congela a versão (imutável; histórico não reescrito); editar publicado → nova versão; só um rascunho por código. Estágios ordenados com setpoint de temperatura, rampa (h), pressão e **critério de avanço tipado** (TIME=dias / GRAVITY=FG-alvo / MANUAL) + **exige confirmação**. `POST/GET/PUT /fermentation/profiles` + `/{id}/publish`. Migration V61 (+ domínio de permissão `fermentation`); permissões `fermentation.profile.read/manage`. UI: cadastro multi-estágio + lista/publicar. Backend +7 (domínio) +4 (IT). |
| FER-002 | A fazer | — | — | — |
| FER-003 | A fazer | — | — | — |
| FER-004 | A fazer | — | — | — |
| YST-001 | A fazer | — | — | — |
| YST-002 | A fazer | — | — | — |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

### FER-001 — decisões (confirmadas com o mantenedor)
- **Escopo = só o perfil versionado (config)**: FER-001 entrega o template de estágios (temperatura/rampa/pressão + critério de avanço + exige confirmação). O avanço em si (aplicar a um lote, mover estágios) fica para FER-004. "Histórico não reescrito" = versão publicada imutável.
- **Critério de avanço tipado**: `TIME` (dias positivos), `GRAVITY` (FG-alvo positivo) ou `MANUAL`; validação cruzada no domínio (TIME não usa densidade; GRAVITY não usa dias; MANUAL não usa nenhum). Todos com flag `requiresConfirmation`.
- **Versionamento igual CLN-001**: DRAFT editável, publicar congela; editar publicado → nova versão (v+1); um rascunho por código (segundo → 409). Módulo novo `fermentation` (permission_domain `...014`).

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
