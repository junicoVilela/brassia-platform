# ADR — Spring Modulith antes de módulos Maven

Status: Aceito

## Contexto

O sistema precisa crescer sem impor complexidade operacional incompatível com um desenvolvedor solo.

## Decisão

Começar com um módulo Maven e módulos de negócio por pacote verificados pelo Spring Modulith.

## Motivo

Reduz custo de build/configuração e ainda bloqueia ciclos e acessos indevidos.

## Consequências

A decisão é obrigatória até que um novo ADR apresente evidência, migração e impacto de reversão.
