# Status — Sprint 12

Estado: NÃO INICIADA

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| TRC-001 | A fazer | — | — | — |
| FDS-001 | A fazer | — | — | — |
| FDS-002 | A fazer | — | — | — |
| FDS-003 | A fazer | — | — | — |
| FDS-004 | A fazer | — | — | — |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

### Antes de começar

- **Jornada E2E de negócio — herdada do aceite da sprint 11 (2026-08-04).** O item de E2E do DoD
  ficou aberto lá: o harness cobre navegação, sessão e integração frontend↔API, não um fluxo de
  negócio ponta a ponta. Esta sprint é o lugar natural para fechá-lo — o simulado de recall
  (`FDS-004`) é, por definição, um fluxo de negócio completo com começo, meio e prova de que
  funcionou. Se a jornada couber na FDS-004, o item do DoD deixa de ser débito.
- **`FDS-001` fecha `PKG-004-A`** (alergênicos), aberto desde a sprint 10.
- **A genealogia é a fundação das outras quatro.** Quarentena, recall e simulado se apoiam no grafo
  de `TRC-001`, e o próprio README lista "elo ausente" como risco. Rodar `TRC-001` sozinha primeiro
  expõe os elos faltantes antes de três histórias dependerem deles.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
