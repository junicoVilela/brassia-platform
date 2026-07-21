# ADR — Hexagonal pragmática

Status: Aceito

## Contexto

O sistema precisa crescer sem impor complexidade operacional incompatível com um desenvolvedor solo.

## Decisão

Aplicar portas e adapters completos nos domínios complexos; CRUDs de apoio usam camadas enxutas.

## Motivo

Mantém testabilidade onde o risco justifica sem multiplicar abstrações artificiais.

## Consequências

A decisão é obrigatória até que um novo ADR apresente evidência, migração e impacto de reversão.
