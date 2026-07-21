# ADR — Federação opcional sem terceirizar autorização

Status: Aceito

## Contexto

O sistema precisa crescer sem impor complexidade operacional incompatível com um desenvolvedor solo.

## Decisão

Aceitar SAML 2.0 e OIDC como autenticação federada e SCIM como provisionamento, convertendo tudo em conta, sessão, permissões e escopos internos.

## Motivo

Interopera com padrões de mercado sem tornar protocolo externo a fonte das regras de acesso do domínio.

## Consequências

A decisão é obrigatória até que um novo ADR apresente evidência, migração e impacto de reversão.
