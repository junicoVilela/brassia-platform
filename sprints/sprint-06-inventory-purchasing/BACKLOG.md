# Backlog — Sprint 06


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
## PUR-002 — Lista de compras e consolidação por fornecedor

**Objetivo:** Converter faltas e previsões aprovadas em uma lista agrupável por fornecedor, unidade de compra e prazo.

**Critérios específicos:**

- Quantidade necessária distingue unidade técnica, embalagem de compra, estoque disponível e reserva.
- Consolidação não mistura produtos apenas por nome; usa referência, fabricante e código.
- Sugestão de fornecedor não cria pedido automaticamente.
- Lista pode ser exportada sem expor custos a usuário sem permissão.

## STK-005 — Vínculo entre referência técnica e lote recebido

**Objetivo:** Relacionar o lote real recebido ao ingrediente de referência sem perder valores específicos de safra, COA ou validade.

**Critérios específicos:**

- Alfa ácido, extrato, umidade, células e demais valores variáveis pertencem ao lote.
- Alteração do catálogo não modifica o lote recebido.
- Vínculo manual, importado ou sugerido registra fonte e confiança.
- COA privado não é republicado como dado global.
