# Backlog — Sprint 05


## STK-001 — Receber lote

**Objetivo:** Registrar fornecedor, validade, quantidade, custo e inspeção.

**Critérios específicos:**

- Quantidade positiva; lote bloqueado não fica disponível.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## STK-002 — Ledger de movimentos

**Objetivo:** Implementar entrada, reserva, consumo, devolução, perda e ajuste.

**Critérios específicos:**

- Saldo deriva do ledger; não existe update direto de saldo.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## STK-003 — Reserva FEFO

**Objetivo:** Reservar lotes para OP com concorrência segura.

**Critérios específicos:**

- Duas OPs não consomem a mesma disponibilidade; vencido é bloqueado.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## STK-004 — Inventário físico

**Objetivo:** Contar, conciliar e ajustar com aprovação.

**Critérios específicos:**

- Diferença gera movimento e auditoria; contagem original permanece.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## PUR-001 — Necessidade de compra

**Objetivo:** Gerar sugestão por estoque, OP e lead time.

**Critérios específicos:**

- Sugestão não cria pedido sem confirmação.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.
