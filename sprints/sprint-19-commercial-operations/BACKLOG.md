# Backlog — Sprint 19

## CRM-001 — Clientes, contatos e consentimentos

Registrar organização, contatos, canais, preferências, consentimentos, retenção e histórico de relacionamento.

## SAL-001 — Produtos, canais e listas de preço

Relacionar SKU/embalagem com lote vendável, preço, impostos externos, validade e canal.

## SAL-002 — Pedidos, reservas e promessa de entrega

Reservar estoque acabado, considerar capacidade e evitar promessa incompatível com lote/validade.

## SAL-003 — Portal B2B e recompra

Permitir catálogo e pedido autenticado por cliente, com limites, preços e disponibilidade próprios.

## FCST-001 — Previsão de demanda e capacidade

Produzir cenário explicável com sazonalidade, pedidos firmes, estoque, validade e faixa de confiança.

## INT-008 — Portas comerciais

Definir adapters para fiscal, contábil, POS e e-commerce sem contaminar o domínio cervejeiro.

## Critérios transversais

- LGPD, consentimento, finalidade e retenção aplicados.
- Dinheiro usa decimal e moeda explícita.
- Pedido e estoque usam idempotência e concorrência.
- Previsão não cria OP ou compra sem confirmação.
