# ADR — Arquitetura hexagonal nos domínios críticos

Status: Aceito

## Contexto

O sistema precisa crescer sem impor complexidade operacional incompatível com um desenvolvedor solo.

## Decisão

Domínio e aplicação críticos dependem de portas; frameworks ficam em adapters.

## Motivo

Mantém regras de alto risco testáveis e evita acoplamento ao Spring/JPA.

## Consequências

A decisão é obrigatória até que um novo ADR apresente evidência, migração e impacto de reversão.
