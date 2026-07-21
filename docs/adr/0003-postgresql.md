# ADR — PostgreSQL como fonte transacional

Status: Aceito

## Contexto

O sistema precisa crescer sem impor complexidade operacional incompatível com um desenvolvedor solo.

## Decisão

Usar PostgreSQL para dados operacionais e relacionais.

## Motivo

Transações, constraints, JSON quando necessário e ecossistema maduro.

## Consequências

A decisão é obrigatória até que um novo ADR apresente evidência, migração e impacto de reversão.
