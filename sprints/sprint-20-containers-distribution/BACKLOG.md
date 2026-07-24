# Backlog — Sprint 20

## CON-001 — Identidade e ciclo do contêiner

Identificar por QR/barcode/RFID, tipo, proprietário, condição, inspeção e estado operacional.

## CON-002 — Enchimento, vínculo ao lote e posição

Associar conteúdo, quantidade, validade e histórico de localização sem quebrar genealogia.

## LOG-001 — Carga, rota e entrega

Planejar carga, sequência, capacidade, janela e responsável, com separação de deveres.

## LOG-002 — Prova de entrega e coleta

Registrar ocorrência, quantidade, horário, assinatura/foto quando consentida e geolocalização minimizada.

## CON-003 — Depósito, atraso, perda e manutenção

Controlar prazo de retorno, depósito, avaria, higienização, manutenção e baixa auditada.

## MOB-001 — Aplicação móvel de distribuição

Ler códigos, operar offline e sincronizar com idempotência e conflito explícito.

## Critérios transversais

- Todo movimento é append-only e corrige por evento compensatório.
- QR/RFID não concede autorização.
- Dados pessoais/geográficos têm finalidade, retenção e acesso restrito.
