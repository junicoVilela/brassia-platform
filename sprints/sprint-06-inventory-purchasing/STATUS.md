# Status — Sprint 06

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| STK-001 | Concluída | IA | (local) | Receber lote: módulos novos `purchasing` (Supplier + lookup) e `inventory` (StockLot). Qtd>0, custo≥0; inspeção APPROVED/BLOCKED (bloqueado → indisponível); valida ingrediente (catálogo) e fornecedor. `POST/GET /suppliers` e `/inventory/lots`; UI de Fornecedores e Estoque. Migrations V39/V40. Backend +12 testes; frontend 131. |
| STK-002 | A fazer | — | — | — |
| STK-003 | A fazer | — | — | — |
| STK-004 | A fazer | — | — | — |
| PUR-001 | A fazer | — | — | — |
| PUR-002 | A fazer | — | — | — |
| STK-005 | A fazer | — | — | — |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

### STK-001 — decisões (confirmadas com o mantenedor)
- **Fornecedor**: mini-cadastro `Supplier` (id, name, code único por cervejaria) no módulo `purchasing`, referenciado por `supplier_id` no lote; publicado via `SupplierLookup`. Permissões `purchasing.supplier.read/manage` (migration V39).
- **Inspeção**: `APPROVED`/`BLOCKED` no recebimento; disponível = APPROVED (`StockLot.available()`).
- **Quantidade recebida** é o fato de entrada; **saldo derivará do ledger no STK-002** (aqui não há saldo mutável). Sem evento (não há evento canônico de recebimento; `StockReserved` no STK-003); auditoria `inventory.lot.receive`.
- Módulo `inventory` valida ingrediente via `catalog.IngredientSpecLookup` e fornecedor via `purchasing.SupplierLookup` (APIs publicadas; Spring Modulith verde). Permissões `inventory.lot.read/manage` (V40).
- Unidades do lote {KG,G,MG,L,ML,UNIT}; validade opcional (nem todo insumo expira). **DÉBITO STK-001-A**: valores variáveis por safra/COA (alfa-ácido, extrato, células) pertencem ao lote → STK-005.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
