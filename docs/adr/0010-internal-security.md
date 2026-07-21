# ADR — Segurança interna e sessão opaca

Status: Aceito

## Contexto

O sistema precisa crescer sem impor complexidade operacional incompatível com um desenvolvedor solo.

## Decisão

Implementar identidade e acesso no módulo security, com Spring Security, sessão no servidor e cookie HttpOnly/Secure/SameSite/__Host-.

## Motivo

Atende o produto sem um servidor de identidade dedicado, evita tokens no navegador e mantém autenticação e autorização sob fronteira explícita.

## Consequências

A decisão é obrigatória até que um novo ADR apresente evidência, migração e impacto de reversão.
