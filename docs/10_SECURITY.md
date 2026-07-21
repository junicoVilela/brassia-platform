# Segurança

O módulo interno de segurança autentica usuários e mantém a sessão no servidor. O navegador recebe apenas cookie opaco `HttpOnly`, `Secure`, `SameSite` e com prefixo `__Host-`; CSRF é obrigatório. Não persistir JWT, senha, token de recuperação ou segredo MFA no navegador.

A autorização combina grupos/papéis, permissões granulares e escopos por cervejaria/recurso. Ownership, tenant, estado e ação são verificados em cada caso de uso. SAML 2.0 e OpenID Connect podem atuar como login federado, sempre convertidos em conta/sessão interna; SCIM 2.0 atende provisionamento corporativo e LDAP/AD é conector legado opcional. Integrações de API começam com credenciais de serviço escopadas; um servidor OAuth só será criado se existir ecossistema externo que o exija.

Controles: TLS, CSP, CORS restrito, rate limit, segredo externo, upload seguro, trilha append-only, backup criptografado, dependências verificadas, SAST/DAST proporcional e testes de isolamento.

Funções críticas: ajuste de estoque, reabertura, override de limpeza, reprocesso, recall e alteração regulatória exigem justificativa e, quando configurado, dupla aprovação.
