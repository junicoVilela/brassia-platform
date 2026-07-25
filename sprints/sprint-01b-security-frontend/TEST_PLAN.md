# Plano de testes — Sprint 01-B

## Obrigatórios

- Unitários (Vitest) de stores/serviços: transições de estado, sucesso, vazio, erro e acesso negado.
- Componentes: renderização de loading/vazio/erro e ocultação por permissão.
- Fluxo E2E principal no frontend: login com MFA e um autoatendimento (senha ou sessões).
- Contrato consumido conforme Problem Details RFC 9457 (mensagens traduzidas pelo interceptor).

## Foco desta sprint

- Passo de MFA não vaza tentativa nem segredo; código inválido volta ao passo sem perder o e-mail.
- Reset de senha não faz auto-login e revoga sessões.
- Revogar a própria sessão corrente é sinalizado e confirmado.
- Item de menu/rota oculto sem a permissão correspondente.
