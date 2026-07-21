# Administração de federação e SSO

## 1. Provedores de identidade

Lista com nome, protocolo, cervejaria/escopo, domínio de descoberta, status, último teste, último login, certificado mais próximo de expirar e ações `editar`, `testar`, `ativar`, `desativar` e `ver eventos`. Não permitir exclusão física de provedor já utilizado.

## 2. Assistente de configuração SAML 2.0

1. **Identificação** — nome, `registrationId` imutável, domínio(s), modo de descoberta e contato responsável.
2. **Metadata do IdP** — URL HTTPS ou upload XML limitado; mostrar entity ID, SSO/SLO, certificados, validade e assinatura do metadata.
3. **Configuração do SP** — entity ID, base URL canônica, ACS, SLO, NameID format, assinatura de AuthnRequest, exigência de Response/assertion assinada e assertion cifrada.
4. **Credenciais** — certificado público do SP, referência da chave privada, finalidade, início/fim, rotação e sobreposição.
5. **Mapeamento** — subject/NameID estável, e-mail, nome, grupos allowlisted e transformação explícita; preview sem persistir segredo.
6. **Provisionamento** — JIT desabilitado, somente convidados ou domínio verificado; grupo padrão mínimo; nenhuma permissão crítica automática.
7. **Teste** — metadata, TLS, assinatura, tempo, AuthnRequest, ACS, linking e logout em sessão de teste separada.
8. **Ativação** — resumo do diff, autenticação recente, confirmação e auditoria. Versão anterior fica disponível para rollback de configuração.

URLs mostradas para cópia/download:

- metadata SP: `/saml2/metadata/{registrationId}`;
- início de login: `/saml2/authenticate/{registrationId}`;
- ACS: `/login/saml2/sso/{registrationId}`;
- SLO: `/logout/saml2/slo` quando habilitado.

A base URL deve respeitar proxy confiável configurado; nunca aceitar `Host`/forwarded headers arbitrários para gerar entity ID ou ACS.

## 3. Configuração OIDC

Issuer/discovery, client ID, referência do secret, scopes (`openid profile email` como início), redirect URI, claim de subject, domínios, JIT, mapeamento, logout suportado e teste. Não oferecer campo para colar access/refresh token. Segredo é write-only e sua troca possui rotação/rollback.

## 4. SCIM e diretórios

Exibir base URL SCIM, service account, expiração/rotação da credencial, schemas suportados, mapeamentos, último cursor/sync, falhas, reconciliação e botão de desativação segura. LDAP/AD inclui URL TLS, CA confiável, secret reference, base DN, filtros allowlisted, timeout e teste somente leitura.

## 5. Certificados, metadados e eventos

Inventário com thumbprint, finalidade, origem, validade, status, dependências e alertas. Eventos mostram provider, subject pseudonimizado, resultado, motivo, correlação e tempo, sem assertion, token ou XML completo. A UI nunca retorna chave privada ou client secret após salvar.
