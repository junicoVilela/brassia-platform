# Status — Sprint 11

Estado: NÃO INICIADA

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| MTR-001 | A fazer | — | — | — |
| MTR-002 | A fazer | — | — | — |
| QLT-001 | A fazer | — | — | — |
| QLT-002 | A fazer | — | — | — |
| SEN-001 | A fazer | — | — | — |
| SEN-002 | A fazer | — | — | — |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

### Antes de começar

- **MTR-002 usa o hub `calculator`, não um motor próprio.** A história pede correção de leitura
  "mostrando fórmula e versão, com o original imutável" — que é exatamente o contrato do hub, já
  usado por `hydrometer-temp-correction` (anterior) e pelas cinco calculadoras que a sprint 10
  acrescentou. Criar um motor de correção dentro de `metrology` duplicaria versionamento de
  fórmula e abriria uma segunda fonte de verdade para o mesmo cálculo, contra
  `docs/05_CALCULATION_ENGINE.md`. O que cabe a `metrology` é o cadastro do instrumento, a curva
  de calibração e a guarda do valor original; o cálculo em si é chamada ao hub. **Revisar esta
  decisão se** a curva de calibração por instrumento não couber no contrato de entradas do hub —
  nesse caso a alternativa é o hub receber a curva como parâmetro, não `metrology` calcular.
- **Dependência das sprints 09 e 10:** ambas aceitas em 2026-08-03. Quatro débitos de decisão de
  negócio da sprint 10 seguem abertos e dois encostam neste escopo: `PKG-002-A` (pressão máxima
  por embalagem) e `PKG-001-A` (validade do CIP por tempo) são faixas de controle, e QLT-001
  define faixas de controle. Decidir onde essas duas vivem **antes** de modelar QLT-001 evita
  criar um segundo lugar para o mesmo dado.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
