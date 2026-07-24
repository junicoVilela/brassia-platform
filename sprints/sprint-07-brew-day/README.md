# Sprint 07 — Brassagem assistida

## Objetivo

Executar água, mostura, fervura e transferência com correções.

## Módulos

production, recipe, equipment

## Dependências

Sprints 05 e 06

## Histórias

- `PRD-001` — Iniciar lote
- `PRD-002` — Modo passo a passo
- `PRD-003` — Registrar medição
- `PRD-004` — Correções determinísticas
- `PRD-005` — Transferência ao fermentador
- `PRD-006` — Central de alertas, cronômetros e ações
- `CAL-002` — Calculadoras de correção durante a execução

## Entregáveis técnicos

- Cronômetros server-aware
- WebSocket/SSE opcional
- Calculation ports
- UI tablet

## Riscos que precisam de teste

- timer perdido
- correção insegura
- medição fora de contexto

## Fora do escopo

Funcionalidades de sprints posteriores, refatorações sem vínculo e infraestrutura não necessária para o objetivo.
