# Padrões de API

- Base `/api/v1`; JSON; OpenAPI é contrato versionado.
- Commands usam POST; substituição completa PUT; parcial PATCH quando semântica for clara.
- Erro segue `application/problem+json` com `type`, `title`, `status`, `detail`, `instance`, `code`, `traceId` e `errors`.
- Paginação por cursor para feeds grandes e página para cadastros simples.
- `Idempotency-Key` em liberação, movimentação, medição de sensor, envase e integrações.
- ETag/versão em alterações concorrentes.
- Nunca aceitar `brewery_id` como autoridade; derivar do contexto autenticado.
