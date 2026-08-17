# Sprint 21 — Conectores externos

**Estado: SUSPENSA** por decisão do mantenedor em 2026-08-18 (`DEC-INT-002`, registrada em `STATUS.md`).

## O que era

Importar receitas de softwares cervejeiros de terceiros mediante credencial e escopo explícitos do
usuário, com sincronização observável e conflito resolvido por decisão humana. Três histórias — dois
conectores de conta (INT-004 e INT-005) e a central de sincronização que os exibiria (INT-007).

## Por que não foi feita

Duas razões, em ordem:

1. **Não era verificável** (`BLQ-INT-001`). Os critérios de aceite eram sobre comportamento de fronteira —
   paginação, rate limit, backoff, timeout, revogação — e isso exige exercitar a API real. Um dublê
   exercita o código que escrevemos contra o contrato que *supomos*, e é a suposição que falha em
   integração com terceiro. Por isso as histórias saíram da Sprint 15 em vez de entrarem com dublê
   (`DEC-INT-001`).
2. **O mantenedor decidiu não integrar** com esses provedores por enquanto (`DEC-INT-002`). Isso é decisão
   de produto, e não de execução.

## O que existe hoje

**Nada de código.** Nenhuma dependência, nenhum cliente HTTP, nenhum campo de credencial, nada no
`openapi.yaml`. A contenção da `DEC-INT-001` — não escrever o que não se pode verificar — é o que tornou a
suspensão barata.

## Se a necessidade voltar

Trazer receita de fora **não depende de API de terceiro**: a Sprint 04 já entrega o pipeline canônico
BeerJSON/BeerXML, e importação por arquivo não precisa de credencial de ninguém. Um conector de conta
seria história nova, e a pergunta de qual provedor voltaria a ser aberta.
