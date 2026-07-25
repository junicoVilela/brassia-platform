# Sprint 01-D — Segurança: leituras e administração (backend)

## Objetivo

Fechar os débitos de backend descobertos ao entregar o frontend de segurança (Sprints 01-B e 01-C): endpoints de leitura e de administração que faltavam, permitindo remover os *workarounds* de cliente que as telas embarcaram.

## Módulos

security, audit

## Dependências

Sprints 01, 01-B e 01-C (capacidades e telas já entregues).

## Histórias

- `SEC-B01` — Status de MFA do usuário (leitura) — completa SEC-009 / SEC-F01
- `SEC-B02` — Origem mascarada no histórico de login — completa SEC-006 / SEC-F03 (opcional; requer migration)
- `SEC-B03` — Auditoria: filtros e paginação server-side — completa SEC-007 / SEC-F08
- `SEC-B04` — Credenciais de conta de serviço (leitura) — completa SEC-011 / SEC-F09
- `SEC-B05` — Administração de mapeamentos SCIM (sessão) — completa SEC-016 / SEC-F10
- `SEC-B06` — Identidades externas vinculadas (leitura) — completa SEC-014 / SEC-F10

## Entregáveis técnicos

- Endpoints aditivos de leitura/administração; sem alterar contratos existentes.
- Migrations aditivas apenas onde inevitável (SEC-B02).
- Ao concluir cada história, remover o débito correspondente registrado no STATUS das Sprints 01-B/01-C e simplificar a tela.

## Riscos que precisam de teste

- Vazamento multi-tenant nas novas leituras (escopo por `brewery_id`).
- Exposição de segredo/dado pessoal em claro (credenciais, IP/UA).
- Autorização negativa por permissão dedicada.

## Fora do escopo

- `SEC-B07` — Login SSO no browser (SAML/OIDC): feature maior, planejada na Sprint 15.
- QR inline no enroll de MFA: débito de frontend (SEC-F01), não backend.
