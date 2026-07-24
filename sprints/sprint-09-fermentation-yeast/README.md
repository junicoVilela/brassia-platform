# Sprint 09 — Fermentação, adega e levedura

## Objetivo

Acompanhar curvas, estabilidade e reutilização de levedura.

## Módulos

fermentation, yeast, equipment

## Dependências

Sprints 07 e 08

## Histórias

- `FER-001` — Perfil de fermentação
- `FER-002` — Leituras e curvas
- `FER-003` — Estabilidade de FG
- `FER-004` — Linha do tempo e agenda de fermentação
- `YST-001` — Coletar levedura
- `YST-002` — Recomendar reutilização

## Entregáveis técnicos

- Time series no PostgreSQL inicial
- Ingestão idempotente
- Curvas Angular
- Genealogia de cultura

## Riscos que precisam de teste

- sensor ruidoso
- FG falso estável
- levedura contaminada

## Fora do escopo

Funcionalidades de sprints posteriores, refatorações sem vínculo e infraestrutura não necessária para o objetivo.
