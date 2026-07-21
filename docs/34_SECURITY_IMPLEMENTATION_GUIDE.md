# Guia de implementação do módulo Segurança

## Estrutura backend

```text
br.com.brew.brassia/
├── security/                         # módulo proprietário
│   ├── api/                          # consultas/contratos publicados
│   ├── domain/                       # Account, Grant, Permission, políticas
│   ├── application/
│   │   ├── port/inbound/             # Login, Invite, Grant, Revoke, EnrollMfa
│   │   ├── port/outbound/            # repositório, e-mail, clock, random, breach lookup
│   │   └── service/                  # casos de uso e transações
│   ├── adapter/inbound/web/           # endpoints e Problem Details
│   ├── adapter/outbound/persistence/  # JPA/Spring Session
│   ├── adapter/outbound/notification/ # convite, reset, alertas
│   └── config/                        # SecurityFilterChain e composição
└── shared/security/
    └── SecurityPrincipal.java         # contrato mínimo consumido pelos módulos
```

`security` merece hexagonal pragmática porque possui regras críticas e adapters variáveis (persistência, e-mail, WebAuthn, blocklist). O contrato `SecurityPrincipal` é mínimo; módulos não importam entidades ou repositories de segurança.

## Estrutura frontend

```text
src/app/
├── core/auth/                         # sessão atual, CSRF, guards e interceptor
└── features/security/
    ├── users/
    ├── groups/
    ├── permissions/
    ├── scopes/
    ├── sessions/
    ├── login-history/
    ├── audit/
    ├── password-policy/
    ├── temporary-access/
    ├── mfa-passkeys/
    ├── service-accounts/
    ├── alerts/
    └── access-reviews/
```

Guard melhora UX, mas não autoriza: o backend decide. Estado remoto fica nos stores de `data-access`; tela não manipula cookie de sessão. O token CSRF pode ser legível pelo Angular; o cookie de sessão nunca.

## Encoder de senha

Usar `DelegatingPasswordEncoder` e algoritmo adaptativo suportado. Benchmarkar Argon2id, scrypt ou bcrypt no ambiente alvo e ajustar custo próximo de um segundo sem causar indisponibilidade. Guardar `{id}hash` para upgrade. Se pepper for usado, fica no gerenciador de segredos e possui plano de rotação; ele não substitui salt/encoder.

## Dependências justificadas

- Spring Security: filtros, autenticação, autorização, CSRF e encoders.
- Spring Session JDBC: sessão compartilhada/revogável no PostgreSQL.
- Biblioteca WebAuthn/FIDO2 mantida: validação do protocolo; escolher na Sprint 01 após spike de compatibilidade.
- Spring Security SAML2 Service Provider: login/logout/metadata sem parser XML ou validação de assinatura próprios.
- Spring OAuth2 Client: OIDC Authorization Code e discovery sem implementar protocolo manualmente.
- SCIM 2.0: implementar endpoints/representações RFC 7643/7644 somente quando houver integração B2B concreta.
- Spring LDAP: conector opcional para diretório legado, sempre com TLS.
- Bucket4j/limiter externo só entra se o limiter simples em processo não atender múltiplas instâncias; documentar comportamento distribuído.

Evitar framework de IAM interno genérico, policy language complexa, Redis e OAuth Authorization Server no MVP. Adicionar somente quando requisito mensurável justificar.
