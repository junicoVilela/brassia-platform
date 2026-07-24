# Sprint 15 — Integrações, sensores e PWA offline

## Objetivo

Operar em campo e receber dados externos com segurança.

## Módulos

integration, pwa, sensor

## Dependências

Sprints 07–14

## Histórias

- `INT-001` — Ingestão de sensor
- `INT-002` — Webhooks
- `PWA-001` — Roteiro offline
- `PWA-002` — Fila offline
- `INT-003` — QR code
- `INT-004` — Conector Brewfather API v2
- `INT-005` — Conector Brewer's Friend API v1
- `INT-006` — Adapters HTTP/MQTT para dispositivos
- `INT-007` — Central de sincronização e conflitos

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
