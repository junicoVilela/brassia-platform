# Status — Sprint 09

Estado: CONCLUÍDA (aceite pendente do mantenedor)

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| FER-001 | Concluída | IA | (local) | Perfil de fermentação versionado (módulo novo `fermentation`): DRAFT editável → publicar congela a versão (imutável; histórico não reescrito); editar publicado → nova versão; só um rascunho por código. Estágios ordenados com setpoint de temperatura, rampa (h), pressão e **critério de avanço tipado** (TIME=dias / GRAVITY=FG-alvo / MANUAL) + **exige confirmação**. `POST/GET/PUT /fermentation/profiles` + `/{id}/publish`. Migration V61 (+ domínio de permissão `fermentation`); permissões `fermentation.profile.read/manage`. UI: cadastro multi-estágio + lista/publicar. Backend +7 (domínio) +4 (IT). |
| FER-002 | Concluída | IA | (local) | Leituras e curvas: densidade/temperatura/pressão/pH por lote, origem MANUAL ou SENSOR. Faixa de plausibilidade por grandeza+unidade → fora da faixa é **gravada e sinalizada** (`valid=false` + motivo), nunca recusada; unidade incompatível com a grandeza → 400. Ingestão **idempotente** pela chave natural (lote, grandeza, origem, instante): reenvio de sensor devolve 200 e não duplica a série. Lote validado por consulta publicada `production.BatchLookup` (sem acessar tabela alheia). `POST/GET /fermentation/readings`. Migration V62; permissões `fermentation.reading.read/record`. UI: curva SVG que diferencia manual/sensor por **cor e forma** + anel de status nas sinalizadas, com tabela equivalente. Backend +7 (domínio) +7 (IT); frontend +6 (store). |
| FER-003 | Concluída | IA | (local) | Estabilidade de FG: parecer **explicável** sobre a série de densidade — devolve veredito, critério aplicado e as leituras que o sustentam, e **não encerra** a fermentação. Janela/leituras mínimas/tolerância ficam no **perfil de fermentação**, congeladas pela versão publicada (rascunho não rege parecer → 409). Rejeita **FG falso estável** (`WINDOW_NOT_COVERED`): leituras aglomeradas num intervalo curto não provam nada. Só entram leituras de densidade em SG válidas — sensor ruidoso da FER-002 é descartado. `GET /fermentation/batches/{id}/fg-stability?profileId=`. Migration V63 (colunas no perfil, com padrão 48h/3/0,0020 SG). UI: painel de parecer na tela de leituras + campos no cadastro de perfil. Backend +11 (domínio) +8 (IT); frontend +4 (store). |
| FER-004 | Concluída | IA | (local) | Linha do tempo do lote: nasce de um **perfil publicado** (vínculo lote↔perfil) e admite etapas do lote (dry hop, cold crash, transferência). Cada etapa tem **ação, janela, condição, tolerância e responsável**. Mover uma data devolve **prévia** (antes/depois + onde a propagação parou) e só grava ao confirmar; propaga apenas pela cadeia de etapas **encadeadas e pendentes** — executada e âncora não se movem. Executar preserva o planejado e registra **desvio + justificativa** (obrigatória fora da tolerância); execução é única. Etapa vencida abre alerta na **central do production** (porta publicada `BatchAlertPublisher`), sem tocar em setpoint, equipamento ou estado. **Encerra o débito FER-003-1**: a estabilidade de FG passa a derivar o perfil do lote e o `profileId` sai do endpoint. Migration V66; permissões `fermentation.schedule.read/manage`. UI: planejar, tabela de etapas, prévia confirmável e registro de execução. Backend +20 (domínio) +9 (IT); frontend +8 (store). |
| YST-001 | Concluída | IA | (local) | Coleta de levedura: origem (lote via `BatchLookup` + coleta-mãe), geração, condição, viabilidade e armazenamento. **Geração derivada** da mãe (comprada = 1), então genealogia e geração nunca divergem; mãe indisponível não propaga linhagem (409). Nasce em **quarentena**: aprovar/reprovar é decisão humana, auditada e **terminal** — reprovada (contaminação) nunca volta a ficar disponível, e reprovar exige motivo. Genealogia completa via CTE recursiva. `POST/GET /fermentation/yeast/harvests`, `/{id}/review`, `/{id}/genealogy`. Migration V64; permissões `fermentation.yeast.read/manage`. UI: cadastro, revisão e linhagem expansível. Backend +10 (domínio) +9 (IT); frontend +8 (store). |
| YST-002 | Concluída | IA | (local) | Recomendação de reutilização: combina **geração, idade e viabilidade** contra a política da cervejaria e devolve, por coleta, veredito + **explicação de cada fator** (recomendação explicável, não uma nota opaca); acumula todos os bloqueios em vez de parar no primeiro. Ordena recomendadas primeiro, depois maior viabilidade / menor geração / mais nova. Só coleta aprovada é candidata. **Uso exige confirmação explícita e lote vinculado** (`confirmed=false` → 400): consome a coleta (novo estado terminal `USED`), então a mesma levedura não é pitchada duas vezes (409). Política por cervejaria, com padrão 10 gerações / 21 dias / 70%. `GET /fermentation/yeast/reuse`, `GET/PUT /fermentation/yeast/policy`, `POST /fermentation/yeast/harvests/{id}/use`. Migration V65; permissão `fermentation.yeast.policy.manage`. UI: painel de recomendação com fatores, editor de política e uso com confirmação. Backend +12 (domínio) +9 (IT); frontend +4 (store). |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

### FER-003 — decisões (confirmadas com o mantenedor)
- **Config no perfil de fermentação**, não em preferência global: como a versão publicada é imutável, o critério usado numa avaliação passada continua reproduzível, e Ale/Lager podem divergir. Padrão 48h / 3 leituras / 0,0020 SG para perfis que não declaram (inclusive os criados antes da V63).
- **Falso estável = janela não coberta**: leituras dentro da tolerância mas com intervalo total menor que a janela reprovam. Não foi implementada comparação com FG-alvo nem exigência de leitura manual.
- **~~Débito FER-003-1 — perfil vem por query param~~ — RESOLVIDO na FER-004**: o perfil passou a ser derivado da agenda do lote e o parâmetro saiu do endpoint.
- **Limitação conhecida**: leituras de densidade em PLATO são ignoradas (a tolerância é declarada em SG); converter aqui seria inventar comportamento. Se necessário, vira história com o `CalculatorEngine`.

### FER-004 — decisões (confirmadas com o mantenedor)
- **A agenda nasce do perfil publicado** e aceita etapas extras do lote. É esse vínculo lote↔perfil que **encerra o débito FER-003-1** — a avaliação de FG deixou de receber `profileId` por query param (mudança de contrato no `GET /fermentation/batches/{id}/fg-stability`; lote sem agenda agora responde 400).
- **"Dependência confirmada" = etapa encadeada e ainda pendente**: cada etapa declara se segue a anterior; a propagação para na primeira âncora (data própria) ou executada, e ambas aparecem em `blocked` com o motivo. A prévia (`apply=false`) não grava nada.
- **Alerta reusa a central do `production`** (PRD-006) por uma porta publicada nova, `BatchAlertPublisher` — uma central só para o cervejeiro. Segue sendo aviso: não altera setpoint, equipamento nem o estado da etapa.
- **Justificativa obrigatória só fora da tolerância**; dentro dela a execução passa sem atrito. O planejado nunca é reescrito.
- **Limitações desta fatia**: etapas extras avançam por condição MANUAL na UI (a API aceita TIME/GRAVITY); a varredura de atrasos é disparada por endpoint, não por agendador; e o campo "responsável" é um id digitado — mesmo acabamento pendente do seletor de cepa (YST-001/002).

### YST-002 — decisões (confirmadas com o mantenedor)
- **Política por cervejaria** (tabela `fermentation_yeast_policy`), não no perfil de fermentação: a decisão de repitch é da casa, não do estilo — e assim a recomendação não repete o débito FER-003-1 (não precisa de `profileId` por parâmetro). Padrão 10 gerações / 21 dias / 70% para quem não configurou.
- **"Desempenho" = idade, geração e viabilidade**, só o que a YST-001 já registra. Atenuação real do lote de origem e penalização por linhagem com irmãs reprovadas ficaram de fora — a primeira exigiria OG do lote e regra de atenuação aparente; a segunda seria regra inventada.
- **Uso confirmado consome a coleta** (estado terminal `USED` + lote vinculado), impedindo pitch duplo. Consumo parcial por quantidade não foi feito: a YST-001 não registra quantidade, e isso exigiria migration na coleta e uma história bem maior.
- **Limites são inclusivos**: exatamente no limite (geração 10, 21 dias, 70%) a coleta ainda é recomendada.

### YST-001 — decisões (confirmadas com o mantenedor)
- **Reprovação é decisão humana**, não limiar automático de viabilidade: a coleta nasce em quarentena e alguém aprova ou reprova com motivo. Cobre contaminação sem queda de viabilidade, que um limiar deixaria passar. A revisão é terminal (segunda tentativa → 409).
- **Geração derivada da coleta-mãe** (sem mãe = comprada, geração 1), nunca informada. Só coleta aprovada pode ser mãe — mãe em quarentena ou reprovada não propaga linhagem.
- **Levedura mora no módulo `fermentation`** (docs/02_MODULE_BOUNDARIES.md), sem módulo novo.
- **Ordem de merge**: a migration é V64, aplicada depois da V63 (FER-003).
- **Fora de escopo aqui**: recomendação de reúso, limite de gerações e vínculo do pitch a um lote são YST-002. Esta fatia só registra e libera/reprova.

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

- Build/commit: `main` em `bc0d0c6`; PRs #108 (FER-001), #112 (FER-002), #113 (FER-003), #114 (YST-001), #115 (YST-002) e #116 (FER-004) mergeados por squash.
- Testes executados: `./mvnw verify` — 397 unitários + 280 de integração (Testcontainers/PostgreSQL 18), verdes; frontend `ng build` + `ng test` — 186 testes, verdes.
- Migration aplicada: V61→V66, sequência contínua, aplicada em banco limpo a cada IT.
- Contratos atualizados: `contracts/openapi.yaml` (endpoints e schemas das seis histórias).
- Riscos remanescentes: (1) decisões da FER-002 ainda pendentes de confirmação; (2) varredura de atrasos da FER-004 sem agendador, disparada por endpoint; (3) sem harness de e2e no projeto, único item não atendido do DoD; (4) ids digitados à mão em três telas (responsável, cepa).
- Aceite: **pendente do mantenedor** — ver `ACCEPTANCE.md`.

## Débitos abertos ao fim da sprint

Nenhum débito com identificador segue aberto: o único criado (**FER-003-1**, perfil por query param) foi removido pela FER-004. Os itens acima são pendências de decisão ou de acabamento, não débito técnico com critério de remoção.
