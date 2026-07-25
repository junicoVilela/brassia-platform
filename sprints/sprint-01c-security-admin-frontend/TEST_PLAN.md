# Plano de testes — Sprint 01-C

## Obrigatórios

- Unitários (Vitest) de stores/serviços: transições de estado, sucesso, vazio, erro e acesso negado.
- Componentes: renderização de loading/vazio/erro e ocultação por permissão.
- Fluxo E2E principal no frontend: um fluxo de governança (acesso temporário ou revisão).
- Contrato consumido conforme Problem Details RFC 9457.

## Foco desta sprint

- Aprovação de acesso crítico bloqueada para o próprio solicitante.
- Segredo de API key visível uma única vez.
- Revisão REMOVE revoga o membership.
- Auditoria bloqueada sem `security.audit.read`.
- Bloqueio por segregação ao associar grupo.
