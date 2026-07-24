# Backlog — Sprint 15


## INT-001 — Ingestão de sensor

**Objetivo:** Receber densidade, temperatura, pressão e vazão.

**Critérios específicos:**

- Mensagem duplicada é idempotente; qualidade e atraso são sinalizados.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## INT-002 — Webhooks

**Objetivo:** Publicar eventos assinados com retry controlado.

**Critérios específicos:**

- Falha não bloqueia domínio; destino e tentativas são auditados.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## PWA-001 — Roteiro offline

**Objetivo:** Disponibilizar OP, checklist e etapas essenciais.

**Critérios específicos:**

- Sem rede, leitura funciona; dados sensíveis seguem protegidos.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## PWA-002 — Fila offline

**Objetivo:** Registrar apontamentos e sincronizar com idempotência.

**Critérios específicos:**

- Conflito não sobrescreve silenciosamente; usuário resolve quando preciso.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## INT-003 — QR code

**Objetivo:** Abrir equipamento, lote, OP e embalagem.

**Critérios específicos:**

- Código não concede acesso; autorização é verificada após leitura.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.
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

## INT-006 — Adapters HTTP/MQTT para dispositivos

**Objetivo:** Receber leituras de densidade, temperatura, pressão e vazão por adapters configuráveis.

**Critérios específicos:**

- Payload externo é convertido para `sensor-reading.schema.json`.
- Identidade, chave, relógio, frequência e qualidade são definidos por dispositivo.
- Duplicidade é idempotente e leitura atrasada/ruidosa é sinalizada.
- Controle remoto permanece fora do escopo.

## INT-007 — Central de sincronização e conflitos

**Objetivo:** Exibir integrações, execuções, cursores, falhas, rate limit e conflitos.

**Critérios específicos:**

- Usuário testa, pausa, revoga e executa sincronização autorizada.
- Prévia mostra criar, atualizar, ignorar ou conflitar.
- Retry preserva cursor/idempotência.
- Credencial é mascarada e alteração crítica é auditada.
