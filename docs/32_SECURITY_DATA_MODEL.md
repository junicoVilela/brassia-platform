# Modelo de dados de segurança

O DDL de referência está em `database/09_security.sql`. As tabelas pertencem ao módulo `security`; outros módulos não as consultam diretamente.

| Área | Tabelas principais | Regra de proteção |
|---|---|---|
| Contas | `security_user`, `password_credential`, `password_history` | hash adaptativo e ciclo de status explícito |
| Autorização | `security_group`, `permission_domain`, `security_permission`, `group_permission`, `user_group_membership`, `access_scope` | RBAC + escopo, vigência e menor privilégio |
| Políticas | `security_policy` | JSON versionado por tipo e cervejaria; validação por schema |
| MFA/recuperação | `mfa_authenticator`, `recovery_code`, `account_token` | segredo cifrado somente quando precisa ser recuperado; demais valores por hash |
| Dispositivo/login | `trusted_device`, `login_event` | identificadores pseudonimizados e retenção limitada |
| Auditoria | `security_audit_event` | append-only na aplicação e diff mascarado |
| Temporário | `temporary_access_grant` | vigência, justificativa, aprovação e expiração |
| Integrações | `service_account`, `api_credential` | identidade não humana, owner, hash, escopo e rotação |
| Federação | `federation_provider`, `external_identity`, `federation_certificate` | provider+subject estável, metadata validado e chave privada externa |
| Provisionamento | `provisioning_event` | idempotência, reconciliação e desativação auditada |
| Governança | `security_alert`, `access_review`, `access_review_item` | tratamento e evidência de revisão |

Spring Session JDBC usa migrations oficiais versionadas/adaptadas ao schema do projeto. Não criar tabela paralela de sessão. A sessão armazena identificadores mínimos; permissões críticas são revalidadas ou invalidadas quando mudam.

## Segredos

- Hash: senha, API key, token de convite/verificação/reset e recovery code.
- Cifra autenticada com chave externa/versionada: segredo TOTP, porque precisa ser recuperado para validar códigos.
- Somente público: chave pública WebAuthn; chave privada fica no autenticador.
- Nunca no banco/log: senha original, API key completa após criação, cookie de sessão ou recovery code legível.
