# ADR — Gateway de IA

Status: Aceito

## Contexto

O sistema precisa crescer sem impor complexidade operacional incompatível com um desenvolvedor solo.

## Decisão

Nenhum módulo chama provedor diretamente; usar gateway com schema e auditoria.

## Motivo

Evita lock-in e aplica segurança/controle uniformes.

## Consequências

A decisão é obrigatória até que um novo ADR apresente evidência, migração e impacto de reversão.
