# Status — Sprint 09

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| FER-001 | Concluída | IA | (local) | Perfil de fermentação versionado (módulo novo `fermentation`): DRAFT editável → publicar congela a versão (imutável; histórico não reescrito); editar publicado → nova versão; só um rascunho por código. Estágios ordenados com setpoint de temperatura, rampa (h), pressão e **critério de avanço tipado** (TIME=dias / GRAVITY=FG-alvo / MANUAL) + **exige confirmação**. `POST/GET/PUT /fermentation/profiles` + `/{id}/publish`. Migration V61 (+ domínio de permissão `fermentation`); permissões `fermentation.profile.read/manage`. UI: cadastro multi-estágio + lista/publicar. Backend +7 (domínio) +4 (IT). |
| FER-002 | Concluída | IA | (local) | Leituras e curvas: densidade/temperatura/pressão/pH por lote, origem MANUAL ou SENSOR. Faixa de plausibilidade por grandeza+unidade → fora da faixa é **gravada e sinalizada** (`valid=false` + motivo), nunca recusada; unidade incompatível com a grandeza → 400. Ingestão **idempotente** pela chave natural (lote, grandeza, origem, instante): reenvio de sensor devolve 200 e não duplica a série. Lote validado por consulta publicada `production.BatchLookup` (sem acessar tabela alheia). `POST/GET /fermentation/readings`. Migration V62; permissões `fermentation.reading.read/record`. UI: curva SVG que diferencia manual/sensor por **cor e forma** + anel de status nas sinalizadas, com tabela equivalente. Backend +7 (domínio) +7 (IT); frontend +6 (store). |
| FER-003 | A fazer | — | — | — |
| FER-004 | A fazer | — | — | — |
| YST-001 | A fazer | — | — | — |
| YST-002 | A fazer | — | — | — |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

### FER-002 — decisões (pendentes de confirmação do mantenedor)
- **"Leitura inválida é sinalizada, não rejeitada"** foi lido como plausibilidade física por grandeza+unidade (SG 0,980–1,180; °C −10–45; psi 0–60; pH 2,5–7,5). Faixa fixa no domínio nesta fatia; se a cervejaria precisar de faixa configurável, vira história própria.
- **Erro de contrato continua sendo 400**: unidade incompatível com a grandeza (ex.: densidade em °C) e lote inexistente são recusados; só o valor fora da faixa é aceito e sinalizado.
- **Idempotência pela chave natural** (lote, grandeza, origem, instante), first-wins: reenvio de sensor não duplica a curva e devolve 200 com o mesmo id. Não há atualização de leitura — medição não é reescrita nem apagada.
- **Vínculo com o lote via `production.BatchLookup`** (consulta publicada nova no módulo production), não por acesso à tabela de produção.

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
