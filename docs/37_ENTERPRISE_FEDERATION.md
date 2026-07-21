# Federação corporativa: SAML 2.0, OIDC, SCIM e diretórios

O sistema continua sendo dono de contas, vínculos, grupos, permissões, escopos, sessões e auditoria. Um provedor federado apenas comprova a autenticação. Após SAML/OIDC, o backend resolve uma `external_identity`, cria o `SecurityPrincipal` interno e emite a mesma sessão opaca usada pelo login local.

## Matriz de escolha

| Padrão | Quando usar | Papel do sistema | Recomendação |
|---|---|---|---|
| SAML 2.0 | SSO empresarial, ADFS e organizações com metadata/certificados | Service Provider / Relying Party | suportar como opção corporativa |
| OpenID Connect | SSO moderno, provedores cloud e login social/enterprise | OAuth2 Client / OIDC Relying Party | preferir em integrações novas |
| SCIM 2.0 | criação, atualização, grupos e desligamento automatizado | SCIM Service Provider | adicionar quando houver cliente B2B/diretório |
| LDAP/Active Directory | ambiente legado/on-prem sem federação | cliente LDAP | último recurso; preferir SAML/OIDC |
| mTLS/X.509 | sensores, gateways e integrações de alta confiança | autenticador de máquina | opcional para dispositivos gerenciados |
| API key | integração simples e limitada | credencial de service account | baseline inicial com escopo/expiração |
| OAuth2 Client Credentials | ecossistema de APIs interoperável | Resource/Authorization Server | somente por ADR; não construir no MVP |

## Configuração SAML 2.0

Cada `federation_provider` SAML contém `registrationId`, entity ID do IdP, metadata URI/XML validado, domínios permitidos, política JIT e mapeamentos. O SP publica metadata, ACS e SLO conforme Spring Security. Chave privada do SP fica no gerenciador de segredos; banco pode guardar certificado público e referência da chave.

Validações obrigatórias: assinatura, certificado confiável/ativo, issuer, audience, destination, `InResponseTo`, condições de tempo com skew pequeno, replay e algoritmo permitido. Preferir fluxo iniciado pelo SP; IdP-initiated só após análise de replay/CSRF. Assertion ou Response deve estar assinada conforme política. SLO só é habilitado quando suportado e testado nas duas pontas.

Rotação de certificado usa período de sobreposição: novo certificado é publicado/aceito antes da remoção do antigo. Alertas em 60/30/15/7 dias e health check administrativo evitam expiração silenciosa. Metadata remoto é obtido com timeout, limite de tamanho, HTTPS e política de cache/fallback; indisponibilidade do IdP não pode derrubar login local de emergência.

## Configuração OIDC

Usar Authorization Code, discovery por `issuer-uri`, `state`, `nonce`, PKCE quando aplicável e redirect URI exata. Identidade externa é `(provider_id, issuer, sub)`; e-mail verificado ajuda exibição/convite, mas nunca é chave única de vinculação automática. Segredo do client fica fora do banco/config versionada. Logout federado e back-channel são capacidades separadas e testadas por provedor.

## Account linking e JIT

- Nunca ligar contas apenas porque os e-mails coincidem.
- Vínculo automático somente para convite pendente com nonce/contexto ou domínio verificado + política explícita.
- Administrador confirma conflitos; vincular/desvincular exige autenticação recente e auditoria.
- `subject`/NameID deve ser estável e persistente; formato transitório não serve como chave permanente.
- Atributos/grupos externos passam por allowlist. Nenhum grupo externo concede permissão crítica diretamente.
- Desativação no diretório revoga sessões, acessos temporários e credenciais delegadas conforme política.

## SCIM 2.0

Expor `/scim/v2/Users`, `/Groups`, `/ServiceProviderConfig`, `/ResourceTypes` e `/Schemas` quando a capacidade for ativada. Autenticação usa service account dedicada; suportar ETag/versionamento, PATCH, filtros mínimos exigidos, paginação, idempotência e respostas SCIM. `externalId` é chave de reconciliação por provider/tenant. DELETE normalmente desativa a conta e preserva auditoria; não apaga histórico.

## LDAP/AD

Usar LDAPS ou StartTLS com validação de certificado, bind técnico de somente leitura e base/filter allowlisted. Não copiar senha do diretório nem armazenar credencial de bind no banco. Timeout/circuit breaker impedem que diretório lento esgote threads. Federação é preferível porque não faz a aplicação receber a senha corporativa.

## Testes de conformidade por provedor

Metadata válida/inválida; certificado antigo/novo/expirado; signature wrapping; issuer/audience/destination errados; assertion expirada/futura/reutilizada; subject ausente/transitório; domínio e JIT; colisão de conta; group injection; SLO; IdP indisponível; clock skew; desprovisionamento SCIM; PATCH/ETag; LDAP sem TLS e certificado inválido.
