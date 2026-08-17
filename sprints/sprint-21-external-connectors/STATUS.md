# Status — Sprint 21

Estado: **SUSPENSA em 2026-08-18 por decisão do mantenedor** — antes disso, bloqueada por credencial de
terceiro

| História | Estado | Evidência | Observação |
|---|---|---|---|
| INT-004 | Suspensa | — | DEC-INT-002: o mantenedor não quer integração com esse provedor por enquanto. |
| INT-005 | Suspensa | — | DEC-INT-002: idem. |
| INT-007 | Suspensa | — | Sem conector, é uma tela sem conteúdo. |

## Decisões e bloqueios

### BLQ-INT-001 — Sem credencial de teste, a sprint não é aceitável

Os critérios de INT-004 e INT-005 são sobre comportamento de fronteira: paginação, rate limit, backoff,
timeout e revogação. Um dublê exercita o código que escrevemos contra o contrato que **supomos**, e é
justamente a suposição que falha em integração com terceiro. Enquanto não houver credencial, a sprint
permanece bloqueada em vez de entregue com dublê.

**Critério de desbloqueio:** conta de teste ativa nos dois provedores, ou em um deles — nesse caso executa-se
só a história correspondente e INT-007.

### DEC-INT-002 (2026-08-18) — O mantenedor não quer integração com esses provedores por enquanto

**A decisão.** Suspender a sprint inteira. Não é o bloqueio de credencial que a `BLQ-INT-001` registrou —
aquele era "não dá para verificar"; este é **"não queremos"**, e é decisão de produto, não de execução.

**O que isso significa hoje, e é bom que seja pouco:** o projeto **não tem uma linha de código** ligada a
esses provedores. Nenhuma dependência, nenhum cliente HTTP, nenhum campo de credencial, nada no
`openapi.yaml`. Eles só existiam como plano — o que a `DEC-INT-001` decidiu na Sprint 15 foi justamente
não escrever esse código sem poder verificá-lo, e essa contenção é o que torna a suspensão barata agora.

**O que fica intacto.** Os documentos de planejamento (backlog, README, e os docs de estratégia e
benchmark) continuam como estão: eles registram por que a decisão foi tomada, e apagá-los faria a próxima
pessoa a considerar o assunto começar do zero sem saber que já se decidiu duas vezes.

**O que NÃO fica prometido.** A capacidade de importar receita de fora continua inexistente. Se um dia ela
for pedida, é história nova — e a pergunta de qual provedor, ou se o caminho é importação de arquivo
BeerJSON/BeerXML (que a Sprint 04 já sabe fazer, e não depende de credencial de ninguém), volta a ser
aberta.

**Reabertura:** decisão do mantenedor. O critério de desbloqueio técnico da `BLQ-INT-001` continua válido
para o dia em que a vontade mudar.
