# Sprint 01-C — Segurança: administração e governança (frontend)

## Objetivo

Entregar as telas de administração e governança de segurança cujo backend já existe desde a Sprint 01 (fatia 1): memberships, acesso temporário, revisão de acessos e segregação, alertas, auditoria consultável e — como fatia de integração corporativa — contas de serviço/API keys e administração de federação/SCIM.

## Módulos

security (frontend)

## Dependências

Sprint 01 (backend entregue: SEC-004, SEC-007, SEC-008, SEC-011..016) e Sprint 01-B (gate de permissão SEC-F11).

## Histórias

- `SEC-F04` — Memberships: grupos do usuário (completa SEC-004 / SEC-013)
- `SEC-F05` — Acesso temporário (completa SEC-008)
- `SEC-F06` — Revisão de acessos e segregação (completa SEC-013)
- `SEC-F07` — Alertas de segurança (completa SEC-012)
- `SEC-F08` — Auditoria consultável (completa SEC-007)
- `SEC-F09` — Contas de serviço e API keys (completa SEC-011)
- `SEC-F10` — Federação (SAML/OIDC) e SCIM — administração (completa SEC-014/015/016)

## Entregáveis técnicos

- Detalhe de usuário com memberships e bloqueio por segregação.
- Fluxo de acesso temporário (solicitar/aprovar/revogar) com vigência.
- Campanha de revisão de acessos e regras de segregação.
- Lista/tratamento de alertas e visualizador de auditoria com filtros.
- Administração de contas de serviço, API keys, providers de federação e mapeamentos SCIM.
- Specs Vitest e fidelidade ao tema Fila.

## Riscos que precisam de teste

- Aprovação de acesso crítico pelo próprio solicitante.
- Segredo de API key exibido mais de uma vez.
- Revisão que remove sem revogar membership.
- Auditoria acessível sem `security.audit.read`.

## Fora do escopo

Fluxo real de SSO no browser (SAML/OIDC login) e LDAP real — permanecem como débito da Sprint 01. Passkeys/WebAuthn.

## Prioridade sugerida

Rodar antes da Sprint 17 (hardening). SEC-F09/F10 (integração corporativa) podem migrar para a Sprint 15 (integrações/PWA) se houver folga.
