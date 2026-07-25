# Backlog — Sprint 01-B


## SEC-F01 — MFA no login e gestão de fatores  (completa SEC-009)

**Objetivo:** Concluir o login em duas etapas e permitir gerenciar TOTP e códigos de recuperação.

**Critérios específicos:**

- Login que recebe `MFA_REQUIRED` abre a etapa de código (TOTP ou recuperação) e conclui a sessão; código inválido não revela tentativa nem trava a conta silenciosamente.
- "Minha conta" faz enroll (QR + segredo), confirmar, desativar e regenerar códigos de recuperação (exibidos uma única vez).
- Frontend trata loading, vazio, erro, conflito e acesso negado (Problem Details RFC 9457).
- Item de menu/rota respeita a permissão do principal e o `brewery_id` ativo.
- Testes (Vitest) cobrem sucesso, código inválido, erro e acesso negado; fidelidade ao tema Fila.


## SEC-F02 — Troca e recuperação de senha  (completa SEC-003 / SEC-010)

**Objetivo:** Trocar senha autenticado e recuperar acesso por e-mail.

**Critérios específicos:**

- Trocar senha exige a atual, aplica política e bloqueia reuso das últimas N; feedback de erro específico.
- "Esqueci a senha" tem resposta neutra; reset por token revoga sessões e não faz auto-login; verificação de e-mail conclui a ativação.
- Frontend trata loading, vazio, erro, conflito e acesso negado (Problem Details RFC 9457).
- Fluxos anônimos (forgot/reset/verify) ficam fora do `authGuard`.
- Testes (Vitest) cobrem sucesso, token inválido/expirado e resposta neutra.


## SEC-F03 — Minha conta: sessões e histórico de login  (completa SEC-006)

**Objetivo:** Página de conta reunindo perfil, sessões ativas e histórico de login (hub que hospeda SEC-F01/F02).

**Critérios específicos:**

- Lista as próprias sessões com revogar (a sessão atual é sinalizada e não se auto-revoga por engano).
- Histórico de login com data, resultado e IP/UA mascarados.
- Frontend trata loading, vazio, erro e acesso negado.
- Ação de revogar confirma intenção e reflete o estado após sucesso/falha (toast).
- Testes (Vitest) cobrem lista, revogação e vazio.


## SEC-F11 — Gate de navegação por permissão (transversal)

**Objetivo:** Esconder itens de menu e proteger rotas conforme as permissões do principal.

**Critérios específicos:**

- Menu e rotas usam as permissões do `SessionUser`; guard de permissão reutilizável.
- Acesso negado (403) exibe estado dedicado em vez de tela quebrada.
- Aplica-se a todas as telas de segurança (Usuários, Grupos e as novas SEC-F).
- Testes (Vitest) cobrem item oculto sem permissão e rota bloqueada.
