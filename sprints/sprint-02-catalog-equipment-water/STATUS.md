# Status — Sprint 02

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| CAT-001 | Concluída | Claude/junico | IngredientTest + CatalogIngredientIT + ModularityTest verdes; frontend (ng build) verde | Módulo `catalog` novo (hexagonal). Ingredientes tipados (6 tipos) com atributos específicos em JSONB validados no domínio; unidades em vocabulário fechado; código único por cervejaria; lock otimista; permissões `catalog.ingredient.*`; auditoria. Tela de ingredientes (listar/filtrar/cadastrar). |
| EQP-001 | Concluída | Claude/junico | EquipmentTest + EquipmentIT + ModularityTest verdes; frontend (ng build) verde | Módulo `equipment` novo. Perfil (capacidade, dead space, eficiência, evaporação) com invariante dead space ≤ capacidade; lock otimista + revisão append-only por versão (GET revisions/{version}); permissões `equipment.*`; auditoria. Tela de equipamentos (listar/cadastrar). |
| EQP-002 | Concluída | Claude/junico | EquipmentMaintenanceTest + EquipmentMaintenanceIT + ModularityTest verdes; frontend (ng build) verde | Janelas de manutenção/calibração (indisponibilidade) no módulo `equipment`. Calibração exige instrumento; agendamento sobreposto é rejeitado (equipamento indisponível não pode ser reservado); consulta de disponibilidade por intervalo; cancelar libera; lock otimista no cancelamento; permissão `equipment.maintenance.manage`; auditoria. Tela de manutenção (selecionar equipamento, agendar/listar/cancelar). |
| WTR-001 | Concluída | Claude/junico | WaterReportTest + WaterIT + ModularityTest verdes; frontend (ng build) verde | Módulo `water` novo. Fontes (register/list/update com lock otimista) + laudos imutáveis (composição iônica em mg/L validada, data, método) append-only — laudo antigo permanece disponível no histórico; permissões `water.*`; auditoria. Tela de água (fontes + registrar/listar laudos por fonte). |
| WTR-002 | A fazer | — | — | — |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
