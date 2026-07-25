# Status — Sprint 01-B

Estado: CONCLUÍDA

Contexto: fecha o débito de frontend das capacidades de segurança entregues como "fatia 1" (só backend) na Sprint 01.

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| SEC-F01 | Concluída | IA | #60 | MFA no login (2 etapas: TOTP/recuperação) + Minha conta com enroll/confirm/desativar/regenerar. |
| SEC-F02 | Concluída | IA | #61 | Trocar senha (Minha conta) + forgot/reset/verify anônimos (fora do authGuard); reset sem auto-login. |
| SEC-F03 | Concluída | IA | #62 | Minha conta: sessões ativas (revogar/encerrar demais, sessão atual protegida) + histórico de login. |
| SEC-F11 | Concluída | IA | #63 | Gate de menu/rota por permissão: hasPermission/hasAnyPermission, permissionGuard, página /forbidden; menu de Segurança oculto sem permissão. |

## Decisões e bloqueios

- Nenhuma migration nova: todo o backend consumido já existe (SEC-002/003/006/009/010).
- Passkeys/WebAuthn e administração de terceiros permanecem fora de escopo (débito da Sprint 01).
- SEC-F01: o backend não expõe GET de status persistente de MFA — a tela "Minha conta" reflete apenas as ações da sessão. Débito: expor status de MFA (candidato a fatia futura do SEC-009) para indicar ativo/inativo ao carregar a página.
- SEC-F01: QR não é renderizado (sem lib de UI externa); enroll mostra segredo + URI otpauth para entrada manual. Débito: gerar QR inline (SVG) se desejável.
- SEC-F03: o backend expõe apenas `occurredAt/outcome/reasonCode` no histórico de login (IP/UA são armazenados em hash, não retornados). A tela mostra o que está disponível. Débito: expor IP/UA mascarados na resposta se necessário.

## Evidências de encerramento

- Build/commit: `main` verde após #60–#63; frontend build e ESLint limpos.
- Testes executados: Vitest 51/51 (auth.api, auth.service, permission.guard, mfa.*, password.*, activity.*, recovery.api).
- Contratos consumidos: MFA (login/mfa, totp/*, recovery-codes), senha (password/change|forgot|reset, email-verification/confirm), sessões (sessions, login-events). Nenhum contrato alterado.
- Migration aplicada: nenhuma (sprint de frontend).
- Riscos remanescentes: sem GET de status persistente de MFA; histórico sem IP/UA na resposta; QR não renderizado (entrada manual). Registrados como débitos.
- Aceite: 4/4 histórias (SEC-F01, F02, F03, F11) entregues, revisadas e mescladas em `main`.
