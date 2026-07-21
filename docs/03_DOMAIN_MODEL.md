# Modelo de domínio

## Agregados principais

- `Brewery`: preferências, fuso, unidades e políticas.
- `EquipmentProfile`: capacidade, perdas, eficiência, manutenção e estado.
- `Recipe` / `RecipeVersion`: identidade, composição, processo, metas e fórmula versionada.
- `BrewOrder`: planejamento, snapshot, reservas e transições.
- `Batch`: execução, etapas, medições, correções, transferências e encerramento.
- `IngredientLot`: origem, validade, especificação e condição.
- `StockLedger`: movimentos imutáveis e saldo derivado.
- `SanitationRun`: procedimento, parâmetros, evidências e liberação.
- `FermentationRun`: estágios, leituras, ações e estabilidade.
- `PackageLot`: unidades, carbonatação, oxigênio, validade e rastreio.
- `QualityCase`: desvio, contenção, causa, CAPA e eficácia.

## Estados críticos

`BrewOrder`: DRAFT → RELEASED → IN_PRODUCTION → FERMENTING → CONDITIONING → PACKAGED → CLOSED; CANCELLED é terminal salvo reabertura autorizada.

`SanitationRun`: PLANNED → CLEANING → RINSE_VERIFICATION → SANITIZING → VERIFICATION → RELEASED/REJECTED.

## Valores

Quantidade sempre carrega valor decimal, unidade e dimensão. Medição carrega também temperatura, instante, método, origem, instrumento, amostra e operador.
