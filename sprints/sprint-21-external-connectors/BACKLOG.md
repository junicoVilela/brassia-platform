# Backlog — Sprint 21

## INT-004 — Conector Brewfather API v2

**Objetivo:** Importar receitas do próprio usuário mediante credencial e escopo explícitos.

**Critérios específicos:**

- Começa read-only e solicita apenas escopo de receita.
- Paginação, rate limit, backoff, timeout e revogação são testados.
- DTO externo passa pelo pipeline canônico BeerJSON/mapeamento da Sprint 04.
- Segredo fica em cofre e nunca aparece em log, evento ou exportação.

## INT-005 — Conector Brewer's Friend API v1

**Objetivo:** Importar receitas e, posteriormente, sessões autorizadas pelo usuário.

**Critérios específicos:**

- Usa `X-API-KEY` e trata a versão antiga como risco monitorado.
- Quando BeerXML for mais completo, a prévia informa a estratégia usada.
- Falha ou campo desconhecido gera relatório, não dado silenciosamente truncado.
- Escrita fica desabilitada até existir contrato documentado e testes de conflito.

## INT-007 — Central de sincronização e conflitos

**Objetivo:** Exibir integrações, execuções, cursores, falhas, rate limit e conflitos.

**Critérios específicos:**

- Usuário testa, pausa, revoga e executa sincronização autorizada.
- Prévia mostra criar, atualizar, ignorar ou conflitar.
- Retry preserva cursor/idempotência.
- Credencial é mascarada e alteração crítica é auditada.

## Critérios transversais

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.
- Conteúdo vindo de provedor externo é não confiável: não vira comando, não vira receita publicada sem
  revisão e não é interpolado em prompt sem tratamento.
