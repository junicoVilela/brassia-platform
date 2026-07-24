# Sprint 06 — Estoque, lotes e compras

## Objetivo

Rastrear insumo e reservar/consumir por lote.

## Módulos

inventory, purchasing

## Dependências

Sprints 02 e 05

## Histórias

- `STK-001` — Receber lote
- `STK-002` — Ledger de movimentos
- `STK-003` — Reserva FEFO
- `STK-004` — Inventário físico
- `PUR-001` — Necessidade de compra
- `PUR-002` — Lista de compras e consolidação por fornecedor
- `STK-005` — Vínculo entre referência técnica e lote recebido

## Entregáveis técnicos

- Optimistic/pessimistic strategy documentada
- Ledger append-only
- Índices de validade
- Testes de corrida

## Riscos que precisam de teste

- saldo negativo
- double spend
- conversão de unidade

## Fora do escopo

Funcionalidades de sprints posteriores, refatorações sem vínculo e infraestrutura não necessária para o objetivo.
