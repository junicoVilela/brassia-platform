# Evolução de segurança

## Baseline antes do primeiro usuário real

Sessão segura, senha adaptativa, recuperação, grupos/permissões/escopos, auditoria, revogação, rate limit, backup/restauração, TLS e secrets management.

## Antes de administradores adicionais

MFA obrigatório, passkeys, acesso temporário, dupla aprovação configurável, alertas, revisão de acessos e conta de emergência testada.

## Antes de integrações externas

Service accounts, API keys escopadas, rotação, inventário e alertas de expiração. SAML/OIDC entram como federação de login; SCIM como provisionamento. Avaliar Authorization Server próprio somente se clientes e consentimento exigirem.

## Antes de escala/regulação maior

SIEM, política formal de retenção, DAST contínuo proporcional, gestão central de segredos/HSM conforme risco, pentest independente e processo de resposta a incidentes com exercícios.
