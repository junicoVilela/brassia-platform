# Status — Sprint 01-B

Estado: EM ANDAMENTO

Contexto: fecha o débito de frontend das capacidades de segurança entregues como "fatia 1" (só backend) na Sprint 01.

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| SEC-F01 | Concluída | IA | #60 | MFA no login (2 etapas: TOTP/recuperação) + Minha conta com enroll/confirm/desativar/regenerar. |
| SEC-F02 | A fazer | — | — | Troca autenticada + forgot/reset/verify anônimos. |
| SEC-F03 | A fazer | — | — | Minha conta: sessões próprias + histórico de login. |
| SEC-F11 | A fazer | — | — | Gate de menu/rota por permissão (transversal). |

## Decisões e bloqueios

- Nenhuma migration nova: todo o backend consumido já existe (SEC-002/003/006/009/010).
- Passkeys/WebAuthn e administração de terceiros permanecem fora de escopo (débito da Sprint 01).
- SEC-F01: o backend não expõe GET de status persistente de MFA — a tela "Minha conta" reflete apenas as ações da sessão. Débito: expor status de MFA (candidato a fatia futura do SEC-009) para indicar ativo/inativo ao carregar a página.
- SEC-F01: QR não é renderizado (sem lib de UI externa); enroll mostra segredo + URI otpauth para entrada manual. Débito: gerar QR inline (SVG) se desejável.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Contratos consumidos:
- Riscos remanescentes:
- Aceite:
