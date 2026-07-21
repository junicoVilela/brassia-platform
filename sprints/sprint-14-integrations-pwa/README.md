# Sprint 14 — Integrações, sensores e PWA offline

## Objetivo

Operar em campo e receber dados externos com segurança.

## Módulos

integration, pwa, sensor

## Dependências

Sprints 06–13

## Histórias

- `INT-001` — Ingestão de sensor
- `INT-002` — Webhooks
- `PWA-001` — Roteiro offline
- `PWA-002` — Fila offline
- `INT-003` — QR code

## Entregáveis técnicos

- Inbox/idempotency
- Service worker
- Conflict model
- Assinatura HMAC de webhook

## Riscos que precisam de teste

- duplicidade
- ordem de eventos
- cache sensível
- conflito offline

## Fora do escopo

Funcionalidades de sprints posteriores, refatorações sem vínculo e infraestrutura não necessária para o objetivo.
