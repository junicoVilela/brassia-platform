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
- `INT-006` — Adapters HTTP/MQTT para dispositivos
- `SEC-B07` — Login SSO no browser (SAML/OIDC) — fecha o ciclo de federação (SEC-014/015)

INT-004, INT-005 e INT-007 saíram para a Sprint 21 — ver DEC-INT-001 em `STATUS.md`.

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
