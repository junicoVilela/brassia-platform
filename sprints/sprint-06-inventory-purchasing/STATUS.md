# Status — Sprint 06

Estado: CONCLUÍDA

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| STK-001 | Concluída | IA | (local) | Receber lote: módulos novos `purchasing` (Supplier + lookup) e `inventory` (StockLot). Qtd>0, custo≥0; inspeção APPROVED/BLOCKED (bloqueado → indisponível); valida ingrediente (catálogo) e fornecedor. `POST/GET /suppliers` e `/inventory/lots`; UI de Fornecedores e Estoque. Migrations V39/V40. Backend +12 testes; frontend 131. |
| STK-002 | Concluída | IA | (local) | Ledger append-only `stock_movement`: entrada/consumo/devolução/perda/ajuste (reserva/liberação p/ STK-003). Saldo derivado (on_hand/reservado/disponível); recebimento lança ENTRY. Lock pessimista no lote nas saídas; saldo negativo → 409; ajuste exige motivo. `POST /inventory/lots/{id}/movements`, `GET .../balance` e `.../movements`; UI de movimentos. Migration V41. Backend +11 testes (inclui double-spend concorrente); frontend 136. |
| STK-003 | Concluída | IA | (local) | Reserva FEFO por ingrediente: aloca sobre lotes disponíveis (validade mais próxima primeiro), pula vencido/BLOCKED, converte unidades; lock pessimista dos lotes candidatos (duas OPs não estouram o disponível); insuficiente → 409 sem parcial; emite StockReserved. Release por referência. `POST /inventory/reservations` e `/release`; UI de reserva. Backend +8 testes (FEFO, expirado/bloqueado, concorrência) ; frontend 139. |
| STK-004 | Concluída | IA | (local) | Inventário físico: contagem (OPEN) por lote captura o saldo do sistema; aprovar (permissão distinta) reconcilia — delta = contado − on_hand vivo → ADJUSTMENT_IN/OUT ref=contagem; saldo final = contado; contagem original permanece; re-aprovar → 409. `POST /inventory/counts`, `/{id}/approve`, `GET`; UI de contagens. Migration V42. Backend +8 testes; frontend 144. |
| PUR-001 | Concluída | IA | (local) | Necessidade de compra: por ingrediente, `sugerido = max(0, demanda − saldo)`; demanda = Σ das OPs **RELEASED** (explosão da receita publicada pelo volume da OP), saldo = on-hand do ledger; unidade canônica. Itens cobertos são omitidos. `GET /purchasing/needs` (`purchasing.purchase.read`, V43); UI "Necessidade de compra". Backend +3 testes; frontend +4. |
| PUR-002 | Concluída | IA | (local) | Lista de compras consolidada por fornecedor: agrupa a necessidade (PUR-001) pelo fornecedor do último lote; distingue unidade técnica × unidade de compra (conversão), estoque e reserva; custo estimado (custo do último lote) gated por `purchasing.cost.read` (V44) — export CSV sai sem custo p/ quem não tem. Ingredientes sem histórico → "Sem fornecedor definido". `GET /purchasing/shopping-list`; UI "Lista de compras" com export CSV. Backend +4 testes; frontend +3. |
| STK-005 | Concluída | IA | (local) | Valores medidos por lote (alfa-ácido, extrato, umidade, células, COA): pertencem ao lote (não ao catálogo), imutáveis (write-once por propriedade → 409), tenant-local (não republicados como referência global) e registram fonte (MANUAL/IMPORTED/SUGGESTED) + confiança (HIGH/MEDIUM/LOW). Captura no recebimento (campo `properties`) ou depois via `POST /inventory/lots/{id}/properties`; leitura em `GET`. Migration V45. UI: painel "Valores (COA)" no Estoque. Backend +10 testes; frontend build/lint verdes. |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

### STK-001 — decisões (confirmadas com o mantenedor)
- **Fornecedor**: mini-cadastro `Supplier` (id, name, code único por cervejaria) no módulo `purchasing`, referenciado por `supplier_id` no lote; publicado via `SupplierLookup`. Permissões `purchasing.supplier.read/manage` (migration V39).
- **Inspeção**: `APPROVED`/`BLOCKED` no recebimento; disponível = APPROVED (`StockLot.available()`).
- **Quantidade recebida** é o fato de entrada; **saldo derivará do ledger no STK-002** (aqui não há saldo mutável). Sem evento (não há evento canônico de recebimento; `StockReserved` no STK-003); auditoria `inventory.lot.receive`.
- Módulo `inventory` valida ingrediente via `catalog.IngredientSpecLookup` e fornecedor via `purchasing.SupplierLookup` (APIs publicadas; Spring Modulith verde). Permissões `inventory.lot.read/manage` (V40).
- Unidades do lote {KG,G,MG,L,ML,UNIT}; validade opcional (nem todo insumo expira). **DÉBITO STK-001-A**: valores variáveis por safra/COA (alfa-ácido, extrato, células) pertencem ao lote → STK-005.

### STK-002 — decisões (confirmadas com o mantenedor)
- **Saldo derivado do ledger** (sem coluna mutável): dimensões **on_hand** e **reservado**; disponível = on_hand − reservado. `RESERVATION/RELEASE` afetam só o reservado (alocação FEFO fica no STK-003).
- **Recebimento** passou a lançar a **ENTRY** no ledger no mesmo commit (saldo 100% derivado).
- **Concorrência**: **lock pessimista** no lote (`SELECT ... FOR UPDATE`) antes de somar o ledger e inserir a saída — fecha double spend (testado com 2 threads).
- **Saldo negativo**: saída que deixaria on_hand < 0 é **rejeitada (409)**. **DÉBITO STK-002-A RESOLVIDO**: exceção autorizada via `allowNegative` (opt-in por requisição) + permissão crítica `inventory.stock.override` (V46); override é auditado (`negativeOverride`). Sem a permissão, pedir `allowNegative` → 403; sem opt-in, negativo segue 409.
- **Ajuste** exige motivo (regra 4). Endpoint manual aceita só consumo/devolução/perda/ajuste; reserva/liberação são do STK-003.

### STK-003 — decisões (confirmadas com o mantenedor)
- **Primitiva por ingrediente**: `POST /inventory/reservations` {ingredientId, quantity, unit, orderId?}; FEFO entre lotes. A orquestração da OP inteira (reservar todos os itens da OP atomicamente) fica como **DÉBITO STK-003-A** (extensão em planning).
- **FEFO**: candidatos = mesmo ingrediente, `APPROVED`, não vencido (`expiry >= hoje` ou nulo), ordenados por validade asc; disponível = on_hand − reservado; converte unidade do pedido ↔ unidade do lote (mesma dimensão).
- **Concorrência**: lock pessimista dos lotes candidatos (`SELECT ... ORDER BY expiry FOR UPDATE`) — testado com 2 threads (não dá double spend). Insuficiência → **409** sem reserva parcial (transação reverte).
- **Release** por `orderId` (RELEASE compensatório do reservado líquido por lote) — base para o cancelamento de OP liberar reservas. Emite `StockReserved` na reserva; auditoria em reserva e liberação. **DÉBITO STK-003-B RESOLVIDO**: o cancelamento de OP publica `BrewOrderCancelled` e o estoque libera as reservas no mesmo commit (listener síncrono).

### STK-004 — decisões (confirmadas com o mantenedor)
- **Conciliação ajustando até o contado**: na aprovação, para cada linha, delta = contado − on_hand vivo (lote travado) → `ADJUSTMENT_IN/OUT` (ref = contagem, motivo "inventário físico"); saldo final = contado, mesmo com movimentos entre contar e aprovar. A **contagem original permanece** (linhas imutáveis; `system_quantity` guarda o snapshot para a diferença exibida).
- **Fluxo com aprovação separada**: contagem nasce `OPEN` (`inventory.count.manage`); aprovar exige permissão distinta **`inventory.count.approve`** (segregação de funções, marcada crítica). Re-aprovar → 409 (guarda por estado).
- Migration V42 (`physical_count`, `physical_count_line`). Ajustes usam o ledger do STK-002 (append-only).

### PUR-001 — decisões (confirmadas com o mantenedor)
- **Fonte da demanda**: apenas OPs **RELEASED** (não rascunhos/agendamento). Para cada OP, explode a **receita publicada** pelo volume da ordem (`MaterialExplosion`, sem perda extra) e agrega por ingrediente em unidade canônica. Publicado via `planning.OrderDemandLookup`.
- **Necessidade** = `max(0, demanda − on_hand)`; itens totalmente cobertos são omitidos (só aparece o que falta). On-hand vem do ledger (todos os lotes, canônico).
- **Inversão de dependência (evita ciclo)**: `inventory` já depende de `purchasing` (`SupplierLookup`). O saldo é exposto por uma porta `purchasing.StockOnHandLookup` **declarada em compras** e **implementada pelo adaptador de estoque** — mantém o sentido `inventory → purchasing` (Spring Modulith verde), sem `purchasing → inventory`.
- **Leitura pura**: não cria pedido de compra nem reserva. Permissão `purchasing.purchase.read` (V43). **DÉBITO PUR-001-A**: lead time / ponto de pedido (antecedência do fornecedor) não considerado — futura consolidação (PUR-002).

### PUR-002 — decisões (confirmadas com o mantenedor)
- **Fornecedor preferencial** = fornecedor do **último lote recebido** do ingrediente (derivado do histórico, sem cadastro novo). Sem histórico → grupo **"Sem fornecedor definido"**. Consolidação por `ingredientId` (referência), não por nome. Porta `purchasing.IngredientSourcingLookup` declarada em compras e implementada pelo estoque (mesma inversão do PUR-001).
- **Unidade de compra**: converte o sugerido (unidade técnica canônica) para a `purchaseUnit` do catálogo quando de mesma dimensão (`catalog.IngredientPurchaseLookup`); exibe também estoque e reservado. Sem tamanho de embalagem modelado → **DÉBITO PUR-002-A**: arredondamento para múltiplos de embalagem (PACK/pack size) fica fora; PACK cai no fallback (mantém unidade técnica).
- **Custo**: estimado = sugerido × custo do último lote (convertido p/ a unidade canônica). Exibição/CSV de custos **gated** por `purchasing.cost.read` (V44); sem a permissão, custos vêm nulos do backend e o export sai sem colunas de custo. **Não cria pedido** (sugestão apenas).
- Reservado passou a ser propagado no `Need` do PUR-001 (saldo do ledger com dimensão reservada), aditivo à resposta de `GET /purchasing/needs`.

### STK-005 — decisões (confirmadas com o mantenedor)
- **Valores pertencem ao lote**: tabela `stock_lot_property` (V45) com valor medido + unidade, ligada ao lote; o catálogo (faixas de referência) não é alterado. Fecha o **DÉBITO STK-001-A**.
- **Captura**: no recebimento (campo opcional `properties` no `POST /inventory/lots`, mesmo commit) e/ou depois via `POST /inventory/lots/{id}/properties` — cobre manual, importado e sugerido em qualquer momento.
- **Imutabilidade write-once**: única por (lote, propriedade); regravar → **409** (preserva a evidência). **DÉBITO STK-005-A**: correção/histórico versionado de um valor já gravado fica para depois.
- **Fonte e confiança**: `source` ∈ {MANUAL, IMPORTED, SUGGESTED}; `confidence` ∈ {HIGH, MEDIUM, LOW}. Auditoria `inventory.lot.property.record`.
- **COA privado**: os valores são do tenant (brewery_id) e nunca republicados como dado global/catálogo — não há caminho de escrita para o catálogo. Permissões reutilizadas: `inventory.lot.manage` (gravar) e `inventory.lot.read` (ler).

## Evidências de encerramento

- Build/commit: backend `mvn compile/test-compile` verdes; frontend `ng build` + `ng lint` verdes. PRs #83–#88 + STK-005 (mergeados/em revisão).
- Testes executados: domínio (StockUnit, StockMovement, StockLot, PhysicalCount, StockLotProperty) + ITs (StockLotIT, StockLedgerIT, StockReservationIT, PhysicalCountIT, PurchaseNeedIT, ShoppingListIT, StockLotPropertyIT) + ModularityTest — todos verdes; frontend specs verdes.
- Migration aplicada: V39–V45 (fornecedor, lote, ledger, reserva, contagem, necessidade/compra, custo, valores por lote).
- Contratos atualizados: `contracts/openapi.yaml` cobre suppliers, inventory (lotes/movimentos/reservas/contagens/propriedades) e purchasing (needs, shopping-list).
- Riscos remanescentes / débitos: STK-002-A (negativo autorizado), STK-003-A (reserva atômica da OP), STK-003-B (liberar reservas no cancelamento — BOP-003), STK-005-A (correção/histórico de valor do lote), PUR-001-A (lead time), PUR-002-A (arredondamento por embalagem).
- Aceite: 7/7 histórias concluídas (STK-001..005, PUR-001, PUR-002); uma história por vez, sem implementação parcial de posteriores.
