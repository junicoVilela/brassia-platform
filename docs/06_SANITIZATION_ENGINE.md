# Motor de limpeza e sanitização

A recomendação depende de equipamento, material, lado quente/frio, sujidade, produto anterior, risco, POP e produto químico disponível.

## Estrutura

`CleaningProfile` → `ProcedureVersion` → `ProcedureStep` → `SanitationRun` → `Verification` → `EquipmentRelease`.

Cada etapa possui método, produto, faixa autorizada pelo fabricante, temperatura, tempo, vazão/ação mecânica, sequência, EPIs, alternativa, proibição e evidência.

## Guardrails

- Não calcular mistura química não prevista na ficha do produto.
- Bloquear combinação incompatível, ciclo fora do equipamento e sanitização sobre superfície reprovada.
- Registrar pH/condutividade de enxágue, inspeção, ATP/micro quando aplicável.
- Manter equipamentos dedicados e matriz de alergênicos/contaminação cruzada.
