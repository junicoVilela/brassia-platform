# ADR — Baseline estável e política de atualização

Status: Aceito

## Contexto

O sistema precisa crescer sem impor complexidade operacional incompatível com um desenvolvedor solo.

## Decisão

Usar LTS no runtime, release estável nos frameworks, lockfiles e upgrades controlados por PR.

## Motivo

Atualidade sem reprodutibilidade e testes transforma manutenção em risco operacional.

## Consequências

A decisão é obrigatória até que um novo ADR apresente evidência, migração e impacto de reversão.
