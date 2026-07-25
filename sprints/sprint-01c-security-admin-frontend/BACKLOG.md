# Backlog — Sprint 01-C


## SEC-F04 — Memberships: grupos do usuário  (completa SEC-004 / SEC-013)

**Objetivo:** Associar e remover grupos de um usuário na tela de Usuários.

**Critérios específicos:**

- Detalhe do usuário mostra grupos efetivos e permite adicionar/remover; violação de segregação aparece como bloqueio explicado.
- Operação respeita permissão (`security.membership.manage`) e `brewery_id` ativo.
- Frontend trata loading, vazio, erro, conflito e acesso negado.
- Testes (Vitest) cobrem associação, remoção e bloqueio por segregação.


## SEC-F05 — Acesso temporário  (completa SEC-008)

**Objetivo:** Solicitar, aprovar e revogar concessões temporárias.

**Critérios específicos:**

- Solicitação com permissão-alvo, vigência e justificativa; concessão crítica exige aprovação de 2º usuário (≠ solicitante); revogação disponível.
- Lista mostra estado, vigência e aprovador; expirada/revogada não concede.
- Frontend trata loading, vazio, erro, conflito e acesso negado.
- Testes (Vitest) cobrem solicitar, aprovar, revogar e janela expirada.


## SEC-F06 — Revisão de acessos e segregação  (completa SEC-013)

**Objetivo:** Conduzir campanhas de revisão e configurar regras de segregação de funções.

**Critérios específicos:**

- Revisor decide manter/remover por item; REMOVE revoga o membership correspondente.
- Tela de regras de segregação (criar/remover) com conflito sinalizado.
- Operação respeita permissão (`security.segregation.manage`) e `brewery_id`.
- Testes (Vitest) cobrem decisão de revisão, revogação e conflito.


## SEC-F07 — Alertas de segurança  (completa SEC-012)

**Objetivo:** Visualizar e tratar alertas (ex.: throttle de login / atividade suspeita).

**Critérios específicos:**

- Lista alertas por severidade e estado; atualizar status (OPEN → ACK → RESOLVED) gera auditoria.
- Frontend trata loading, vazio, erro e acesso negado.
- Testes (Vitest) cobrem lista e transição de status.


## SEC-F08 — Auditoria consultável  (completa SEC-007)

**Objetivo:** Visualizador da trilha de auditoria por cervejaria.

**Critérios específicos:**

- Lista eventos com filtro por ação, recurso, ator e período; diff mascarado; paginação.
- Acesso somente com `security.audit.read`; escopo pelo `brewery_id` ativo.
- Frontend trata loading, vazio, erro e acesso negado.
- Testes (Vitest) cobrem filtro, paginação e acesso negado.


## SEC-F09 — Contas de serviço e API keys  (completa SEC-011)

**Objetivo:** Gerir contas de serviço e emitir/revogar API keys.

**Critérios específicos:**

- Criar conta de serviço; emitir key (segredo `brassia_…` exibido uma única vez); revogar; escopos visíveis.
- Frontend trata loading, vazio, erro, conflito e acesso negado.
- Testes (Vitest) cobrem emissão (segredo único), revogação e escopos.


## SEC-F10 — Federação (SAML/OIDC) e SCIM — administração  (completa SEC-014/015/016)

**Objetivo:** Administrar provedores de federação e mapeamentos SCIM (sem fluxo de SSO no browser).

**Critérios específicos:**

- CRUD de providers SAML/OIDC + validar metadata; mapeamentos de grupo SCIM.
- Fluxo de login SSO real permanece fora de escopo (débito registrado).
- Frontend trata loading, vazio, erro, conflito e acesso negado.
- Testes (Vitest) cobrem CRUD de provider e validação de metadata.
