# Sprint 01 — Segurança interna, cervejaria e acesso

## Objetivo

Autenticar, autorizar e isolar operações sem provedor externo de identidade.

## Módulos

security, brewery

## Dependências

Sprint 00

## Histórias

- `SEC-001` — Usuários e ciclo da conta
- `SEC-002` — Login e sessão segura
- `SEC-003` — Política e histórico de senha
- `SEC-004` — Grupos, domínios e permissões
- `SEC-005` — Escopos de acesso
- `SEC-006` — Sessões, dispositivos e histórico de login
- `SEC-007` — Auditoria de segurança
- `SEC-008` — Acesso temporário
- `SEC-009` — MFA, passkeys e recuperação
- `SEC-010` — Recuperação e verificação de conta
- `SEC-011` — Credenciais de serviço e API keys
- `SEC-012` — Proteção e alertas
- `SEC-013` — Revisão de acessos e segregação
- `SEC-014` — Federação SAML 2.0
- `SEC-015` — Federação OpenID Connect
- `SEC-016` — SCIM e diretório corporativo
- `BRW-001` — Cadastrar cervejaria
- `BRW-002` — Preferências operacionais

## Entregáveis técnicos

- Spring Security e Spring Session JDBC
- Cookie __Host- e CSRF
- DelegatingPasswordEncoder com benchmark
- WebAuthn/TOTP
- RBAC + escopo/ABAC
- SAML 2.0/OIDC opcionais
- SCIM 2.0
- Rate limit e auditoria
- Testes de autorização negativa

## Riscos que precisam de teste

- implementação criptográfica caseira
- vazamento multi-tenant
- sequestro de sessão
- account linking indevido
- certificado SAML expirado
- recuperação fraca
- papel excessivo
- fuso incorreto

## Fora do escopo

Funcionalidades de sprints posteriores, refatorações sem vínculo e infraestrutura não necessária para o objetivo.
