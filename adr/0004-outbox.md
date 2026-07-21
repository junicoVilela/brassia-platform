# ADR — Outbox transacional

Status: Aceito

## Contexto

O sistema precisa crescer sem impor complexidade operacional incompatível com um desenvolvedor solo.

## Decisão

Publicar integrações a partir de outbox persistida no mesmo commit.

## Motivo

Evita perda entre commit do domínio e envio externo.

## Consequências

A decisão é obrigatória até que um novo ADR apresente evidência, migração e impacto de reversão.
