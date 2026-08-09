# Plano de testes — Sprint 21

## Obrigatórios

- Unitários para mapeamento externo → canônico, incluindo campo ausente, campo desconhecido e unidade divergente.
- Integração com PostgreSQL/Testcontainers e migrations desde banco vazio.
- Autorização sem permissão e acesso a integração de outra cervejaria.
- Idempotência/repetição e concorrência em comandos críticos.
- Contrato HTTP, Problem Details RFC 9457 e payloads de evento.
- Fluxo E2E principal no frontend.

## Foco desta sprint

- Testar paginação com cursor retomável após falha no meio da página.
- Testar rate limit do provedor tratado como espera, não como erro permanente.
- Testar backoff, timeout e cancelamento.
- Testar revogação de credencial durante sincronização em andamento.
- Testar que credencial não aparece em log, evento, auditoria nem exportação.
- Testar prévia: criar, atualizar, ignorar e conflitar.
- Testar campo desconhecido gerando relatório, não truncamento silencioso.

## Verificação contra provedor real

Paginação, rate limit, backoff, timeout e revogação precisam de ao menos uma execução manual registrada
contra a API real do provedor, com evidência no `STATUS.md`. Dublê cobre o código; só o provedor cobre o
contrato.
