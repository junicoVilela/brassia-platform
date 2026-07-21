# ADR — Tenant por brewery_id

Status: Aceito

## Contexto

O sistema precisa crescer sem impor complexidade operacional incompatível com um desenvolvedor solo.

## Decisão

Preparar todas as entidades tenant com brewery_id obrigatório.

## Motivo

Permite múltiplas cervejarias sem bancos separados no início.

## Consequências

A decisão é obrigatória até que um novo ADR apresente evidência, migração e impacto de reversão.
