# Backlog — Sprint 14


## AIA-001 — Gateway de modelos

**Objetivo:** Abstrair provedor, timeout, custo, schema e fallback.

**Critérios específicos:**

- Provedor desabilitado não quebra fluxo; resposta inválida é rejeitada.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## RAG-001 — Indexar documentos

**Objetivo:** Processar POP, manual, ficha e laudo com metadados tenant.

**Critérios específicos:**

- Documento sem permissão não é recuperado; versão/vigência preservadas.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## RAG-002 — Responder com evidência

**Objetivo:** Mostrar fontes, trechos e separar inferência.

**Critérios específicos:**

- Sem fonte, resposta declara limitação; prompt injection não ganha ferramenta.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## AIA-002 — Avaliar receita/lote

**Objetivo:** Usar fatos determinísticos e histórico para explicar riscos.

**Critérios específicos:**

- Nenhum número é inventado; cálculos referenciam serviço de domínio.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## AIA-003 — Propor comando

**Objetivo:** Retornar comando estruturado sujeito a confirmação.

**Critérios específicos:**

- Nova autorização ocorre ao confirmar; interação e aceite são auditados.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.
