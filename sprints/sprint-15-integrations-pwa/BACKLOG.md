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

## INT-006 — Adapters HTTP/MQTT para dispositivos

**Objetivo:** Receber leituras de densidade, temperatura, pressão e vazão por adapters configuráveis.

**Critérios específicos:**

- Payload externo é convertido para `sensor-reading.schema.json`.
- Identidade, chave, relógio, frequência e qualidade são definidos por dispositivo.
- Duplicidade é idempotente e leitura atrasada/ruidosa é sinalizada.
- Controle remoto permanece fora do escopo.

## SEC-B07 — Login SSO no browser (SAML/OIDC)

**Objetivo:** Autenticar via provedor de federação validado, com fluxo real no browser (SP-initiated) e JIT provisioning.

**Critérios específicos:**

- Redirect/callback SAML 2.0 e OIDC (Authorization Code + PKCE) consumindo provedores validados; sessão criada com escopo e cervejaria corretos.
- Reaproveita `SamlAssertionValidator`/`OidcTokenClaimsValidator` já existentes; account linking seguro (sem sequestro).
- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.
