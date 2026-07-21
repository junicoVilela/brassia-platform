# Catálogo de API do módulo Segurança

| Método e rota | Capacidade | Acesso |
|---|---|---|
| `POST /api/v1/security/login` | iniciar login/MFA | público + CSRF + rate limit |
| `POST /api/v1/security/login/mfa` | concluir desafio | desafio curto + rate limit |
| `POST /api/v1/security/logout` | invalidar sessão | autenticado |
| `GET /api/v1/security/session` | obter usuário/tenant/capabilities | autenticado |
| `POST /api/v1/security/password/forgot` | solicitar recuperação | público, resposta genérica |
| `POST /api/v1/security/password/reset` | concluir recuperação | token single-use |
| `GET/POST /api/v1/security/passkeys/*` | listar/cadastrar/remover passkeys | autenticado + reautenticação |
| `GET/POST /api/v1/security/totp/*` | cadastrar/remover TOTP | autenticado + reautenticação |
| `GET/DELETE /api/v1/security/sessions/{id}` | listar/revogar sessões | dono ou `security.session.manage` |
| `GET/POST/PATCH /api/v1/security/users` | administrar contas | `security.user.*` + escopo |
| `GET/POST/PATCH /api/v1/security/groups` | administrar grupos | `security.group.*` + escopo |
| `GET /api/v1/security/permissions` | consultar catálogo | `security.permission.read` |
| `GET/POST/PATCH /api/v1/security/scopes` | administrar escopos | `security.scope.*` |
| `GET/POST /api/v1/security/temporary-access` | solicitar/aprovar/revogar | permissões separadas por ação |
| `GET /api/v1/security/login-events` | histórico de login | próprio ou `security.login.read` |
| `GET /api/v1/security/audit-events` | auditoria | `security.audit.read` |
| `GET/POST /api/v1/security/service-accounts` | contas técnicas | `security.service-account.*` |
| `POST /api/v1/security/api-credentials` | emitir/rotacionar chave | reautenticação + segredo mostrado uma vez |
| `GET/PATCH /api/v1/security/alerts` | tratar alertas | `security.alert.*` |
| `GET/POST/PATCH /api/v1/security/access-reviews` | campanhas de revisão | `security.access-review.*` |
| `GET/POST/PATCH /api/v1/security/federation-providers` | configurar/testar SAML/OIDC/LDAP | `security.federation.*` + reautenticação |
| `GET /saml2/service-provider-metadata/{registrationId}` | metadata do SP | público controlado |
| `GET /saml2/authenticate/{registrationId}` | iniciar SSO SAML | público + state/request tracking |
| `POST /login/saml2/sso/{registrationId}` | ACS SAML | validação completa da Response/assertion |
| `GET /oauth2/authorization/{registrationId}` | iniciar OIDC | público + state/nonce/PKCE |
| `GET /login/oauth2/code/{registrationId}` | callback OIDC | code/issuer/nonce/redirect validados |
| `/scim/v2/Users` e `/scim/v2/Groups` | provisionamento SCIM | service account dedicada e escopada |

Respostas de erro seguem Problem Details. Endpoints públicos têm limites mais fortes; endpoints administrativos exigem auditoria e `Idempotency-Key` quando uma repetição possa criar concessão/credencial duplicada.
