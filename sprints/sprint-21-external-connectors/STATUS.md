# Status — Sprint 21

Estado: FUTURA — bloqueada por credencial de terceiro

| História | Estado | Evidência | Observação |
|---|---|---|---|
| INT-004 | A fazer | — | Movida da Sprint 15 (DEC-INT-001). Requer credencial Brewfather de teste. |
| INT-005 | A fazer | — | Movida da Sprint 15 (DEC-INT-001). Requer API key Brewer's Friend de teste. |
| INT-007 | A fazer | — | Movida da Sprint 15 (DEC-INT-001). Depende de INT-004 ou INT-005 existir. |

## Decisões e bloqueios

### BLQ-INT-001 — Sem credencial de teste, a sprint não é aceitável

Os critérios de INT-004 e INT-005 são sobre comportamento de fronteira: paginação, rate limit, backoff,
timeout e revogação. Um dublê exercita o código que escrevemos contra o contrato que **supomos**, e é
justamente a suposição que falha em integração com terceiro. Enquanto não houver credencial, a sprint
permanece bloqueada em vez de entregue com dublê.

**Critério de desbloqueio:** conta de teste ativa nos dois provedores, ou em um deles — nesse caso executa-se
só a história correspondente e INT-007.
