# Checklist de implementação do módulo Segurança

## Antes do código

- Mapear ativo, ameaça, ator, permissão, escopo e dado sensível.
- Confirmar que a história pertence a `security` e não cria regra de outro módulo.
- Preferir Spring Security/Spring Session e biblioteca WebAuthn mantida; nunca criar criptografia, token, sessão, TOTP ou password encoder próprio.
- Definir abuso esperado: enumeração, credential stuffing, sequestro/fixação de sessão, CSRF, privilégio indevido e tenant cruzado.

## Autenticação e credenciais

- Senha passa por `DelegatingPasswordEncoder`; algoritmo adaptativo é medido no hardware-alvo e o hash inclui identificador de versão.
- Senha comprometida/comum é rejeitada; Unicode, espaços, colagem e gerenciadores de senha são aceitos.
- Respostas de login/recuperação não revelam se a conta existe e evitam diferença grosseira de tempo.
- Passkey/WebAuthn é o fator preferido; TOTP é fallback. Códigos de recuperação são hash, uso único e regeneração invalida os anteriores.
- Segredo TOTP é cifrado com chave externa versionada; senha, API key e token de conta nunca são cifrados para recuperação, mas armazenados por hash.

## Sessão web

- Sessão opaca no servidor; cookie `__Host-brew_session`, `HttpOnly`, `Secure`, `SameSite=Lax` ou `Strict`, `Path=/` e sem `Domain`.
- Identificador gira no login, elevação/redução de privilégio, troca de cervejaria e reautenticação.
- CSRF ativo nos comandos; CORS restrito; páginas sensíveis usam `Cache-Control: no-store`.
- Logout invalida servidor e cookie; usuário pode revogar uma ou todas as sessões.
- Timeout inativo e absoluto são configuráveis; ação crítica exige autenticação recente.

## Autorização

- Negar por padrão e verificar ação + cervejaria + escopo + ownership + estado no caso de uso.
- Controller não confia em `brewery_id`, grupo ou permissão enviados pelo cliente.
- Acesso temporário expira automaticamente; crítico exige justificativa e aprovação separada quando configurado.
- Testar ausência da permissão, escopo errado, tenant cruzado, conta suspensa e objeto inexistente.

## Auditoria e privacidade

- Registrar sucesso/falha relevante, ator, alvo, resultado, motivo, trace e diff mascarado.
- Nunca registrar senha, cookie, token completo, código de recuperação, segredo TOTP, chave privada ou conteúdo sensível desnecessário.
- IP e user-agent seguem minimização, hash/pseudonimização, retenção e acesso restrito.
- Alertas são acionáveis, deduplicados e possuem workflow de reconhecimento/resolução.

## Testes e liberação

- Testes unitários de política; integração com PostgreSQL/Spring Session; E2E de login, MFA, recuperação e revogação.
- Testes de rate limit, concorrência, token expirado/reutilizado, replay e alteração de privilégio com sessão ativa.
- SAST, dependências, secrets scanning e DAST proporcional sem achado alto/crítico aberto.
- Backup/restauração, rotação de chaves e conta administrativa de emergência são ensaiados antes da produção.
