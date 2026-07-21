# Backlog — Sprint 03


## REC-001 — Criar receita

**Objetivo:** Montar composição, equipamento, metas e processo completo.

**Critérios específicos:**

- Itens possuem unidade/etapa; capacidade e percentuais são validados.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## REC-002 — Motor de volumes

**Objetivo:** Calcular água, absorção, evaporação, perdas e volume final.

**Critérios específicos:**

- Dataset dourado passa; balanço mostra cada parcela.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## REC-003 — Metas cervejeiras

**Objetivo:** Calcular OG, FG, ABV, IBU, cor e atenuação.

**Critérios específicos:**

- Método e versão são persistidos; tolerância é explícita.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## REC-004 — Publicar versão

**Objetivo:** Congelar fórmula e impedir edição posterior.

**Critérios específicos:**

- Alteração gera nova versão; OP antiga continua com snapshot.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## REC-005 — Clonar, escalar e comparar

**Objetivo:** Recalcular por volume/eficiência e comparar versões.

**Critérios específicos:**

- Escala respeita capacidade; diferenças são listadas por campo.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## REC-006 — Importar/exportar

**Objetivo:** Suportar BeerXML/BeerJSON com relatório de compatibilidade.

**Critérios específicos:**

- Importação inválida não persiste; campos desconhecidos são reportados.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.
