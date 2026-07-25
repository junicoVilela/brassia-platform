# Sprint 01-B — Segurança: autoatendimento (frontend)

## Objetivo

Entregar as telas de autoatendimento de segurança cujo backend já existe desde a Sprint 01 (fatia 1), fechando o débito de frontend das capacidades sensíveis para uso real: MFA no login, troca/recuperação de senha e gestão da própria conta (sessões e histórico), além do gate de navegação por permissão.

## Módulos

security (frontend), core/auth (frontend)

## Dependências

Sprint 01 (backend de `security` já entregue: SEC-002, SEC-003, SEC-006, SEC-009, SEC-010).

## Histórias

- `SEC-F01` — MFA no login e gestão de fatores (completa SEC-009)
- `SEC-F02` — Troca e recuperação de senha (completa SEC-003 / SEC-010)
- `SEC-F03` — Minha conta: sessões e histórico de login (completa SEC-006)
- `SEC-F11` — Gate de navegação por permissão (transversal)

## Entregáveis técnicos

- Login em duas etapas consumindo `MFA_REQUIRED` e `POST /login/mfa`.
- Página "Minha conta" (hub self-service) com abas/cartões: senha, MFA, sessões, histórico.
- Fluxos anônimos (forgot/reset/verify) fora do `authGuard`.
- Guard e diretiva de permissão reutilizáveis a partir do `SessionUser.permissions`.
- Specs Vitest e fidelidade ao tema Fila.

## Riscos que precisam de teste

- Vazamento de tentativa/segredo no passo de MFA.
- Auto-revogação acidental da sessão corrente.
- Reset de senha com auto-login indevido.
- Item de menu visível sem permissão.

## Fora do escopo

Passkeys/WebAuthn; administração de terceiros (sessões/MFA de outro usuário); telas de governança e integração corporativa (ficam na Sprint 01-C).
