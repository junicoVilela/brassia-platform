# Backlog — Sprint 01-D


## SEC-B01 — Status de MFA do usuário (leitura)  (completa SEC-009 / SEC-F01)

**Objetivo:** Expor se a conta autenticada tem MFA ativo (e quantos códigos de recuperação restam).

**Critérios específicos:**

- GET autenticado devolve { mfaEnabled, recoveryCodesRemaining }; a tela "Minha conta" passa a indicar ativo/inativo no carregamento (hoje reflete só a sessão).
- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.


## SEC-B02 — Origem mascarada no histórico de login  (completa SEC-006 / SEC-F03)

**Objetivo:** Exibir IP/dispositivo de forma segura no histórico, sem expor dado pessoal em claro.

**Critérios específicos:**

- IP/user-agent hoje são só hash (irreversível). Requer capturar e persistir uma representação mascarada no login (migration aditiva) e incluí-la em `LoginEventView`.
- Retenção e privacidade preservadas; nada identificável em claro. Prioridade baixa — decisão de privacidade pode manter o desenho atual.
- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.


## SEC-B03 — Auditoria: filtros e paginação server-side  (completa SEC-007 / SEC-F08)

**Objetivo:** Consultar a trilha com filtros e paginação, além dos 50 mais recentes.

**Critérios específicos:**

- GET /audit-events aceita filtros (ação, tipo de recurso, ator, período) e paginação (page/size); a tela deixa de filtrar no cliente.
- Consulta escopada pela cervejaria ativa; diff continua mascarado.
- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.


## SEC-B04 — Credenciais de conta de serviço (leitura)  (completa SEC-011 / SEC-F09)

**Objetivo:** Listar as credenciais existentes de uma conta de serviço para gerir/revogar as já emitidas.

**Critérios específicos:**

- GET /service-accounts/{id}/credentials devolve prefixo, escopos, expiração e estado (revogada/ativa). Reutiliza `ApiCredentialRepository.listByServiceAccount` (já existe).
- Segredo nunca é retornado; apenas metadados.
- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.


## SEC-B05 — Administração de mapeamentos SCIM (sessão)  (completa SEC-016 / SEC-F10)

**Objetivo:** Permitir que um admin liste e defina mapeamentos de grupo SCIM (grupo externo → grupo interno) por provedor.

**Critérios específicos:**

- Endpoints de sessão (não só o fluxo máquina-a-máquina em /scim/v2) para listar/criar/desativar mapeamentos; permissão dedicada.
- Requer método de leitura no `ScimGroupMappingRepository` (hoje só `findActive`/`create`).
- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.


## SEC-B06 — Identidades externas vinculadas (leitura)  (completa SEC-014 / SEC-F10)

**Objetivo:** Listar as identidades externas vinculadas a um provedor/usuário.

**Critérios específicos:**

- GET por provedor (e/ou por usuário) devolve as identidades vinculadas (subject externo, e-mail normalizado, usuário interno). Requer método de listagem no `ExternalIdentityRepository` (hoje só `link`/`resolveUserId`).
- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.
