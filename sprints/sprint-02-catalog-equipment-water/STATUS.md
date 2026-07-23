# Status — Sprint 02

Estado: CONCLUÍDA

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| CAT-001 | Concluída | Claude/junico | IngredientTest + CatalogIngredientIT + ModularityTest verdes; frontend (ng build) verde | Módulo `catalog` novo (hexagonal). Ingredientes tipados (6 tipos) com atributos específicos em JSONB validados no domínio; unidades em vocabulário fechado; código único por cervejaria; lock otimista; permissões `catalog.ingredient.*`; auditoria. Tela de ingredientes (listar/filtrar/cadastrar). |
| EQP-001 | Concluída | Claude/junico | EquipmentTest + EquipmentIT + ModularityTest verdes; frontend (ng build) verde | Módulo `equipment` novo. Perfil (capacidade, dead space, eficiência, evaporação) com invariante dead space ≤ capacidade; lock otimista + revisão append-only por versão (GET revisions/{version}); permissões `equipment.*`; auditoria. Tela de equipamentos (listar/cadastrar). |
| EQP-002 | Concluída | Claude/junico | EquipmentMaintenanceTest + EquipmentMaintenanceIT + ModularityTest verdes; frontend (ng build) verde | Janelas de manutenção/calibração (indisponibilidade) no módulo `equipment`. Calibração exige instrumento; agendamento sobreposto é rejeitado (equipamento indisponível não pode ser reservado); consulta de disponibilidade por intervalo; cancelar libera; lock otimista no cancelamento; permissão `equipment.maintenance.manage`; auditoria. Tela de manutenção (selecionar equipamento, agendar/listar/cancelar). |
| WTR-001 | Concluída | Claude/junico | WaterReportTest + WaterIT + ModularityTest verdes; frontend (ng build) verde | Módulo `water` novo. Fontes (register/list/update com lock otimista) + laudos imutáveis (composição iônica em mg/L validada, data, método) append-only — laudo antigo permanece disponível no histórico; permissões `water.*`; auditoria. Tela de água (fontes + registrar/listar laudos por fonte). |
| WTR-002 | Concluída | Claude/junico | WaterBlendTest + WaterBlendIT + ModularityTest verdes; frontend (ng build) verde | Perfil mineral alvo (persistido, register/list/update com lock otimista + auditoria) + simulação de mistura por balanço de massa (média ponderada por volume dos laudos mais recentes) — balanço fecha; resultado informa entradas e método, e desvio vs alvo quando informado. Reaproveita permissões `water.*`. Tela de mistura (criar alvo, montar mistura, simular). |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

## Evidências de encerramento

- Build/commit: `main` em `8534453`; PRs #34 (CAT-001), #35 (EQP-001), #36 (EQP-002), #37 (WTR-001), #38 (WTR-002) — todos com CI verde (Backend, Frontend, Contratos, Segredos).
- Testes executados: domínio (JUnit) por história — `IngredientTest`, `EquipmentTest`, `EquipmentMaintenanceTest`, `WaterReportTest`, `WaterBlendTest`; integração com PostgreSQL real via Testcontainers — `CatalogIngredientIT`, `EquipmentIT`, `EquipmentMaintenanceIT`, `WaterIT`, `WaterBlendIT`; inspeção arquitetural — `ModularityTest`; frontend — `ng build` e `eslint`. Cada IT cobre sucesso, limite, falha, outra cervejaria e repetição.
- Migration aplicada: V21 (catalog_ingredient), V22 (equipment + equipment_revision), V23 (equipment_maintenance), V24 (water_source + water_report), V25 (water_profile). Idempotentes e testadas em banco limpo pela suíte de ITs.
- Contratos atualizados: `contracts/openapi.yaml` com os endpoints de catálogo, equipamentos (perfil, revisões, manutenção, disponibilidade) e água (fontes, laudos, perfis-alvo, simulação de mistura); Problem Details RFC 9457 em todos os erros.
- Riscos remanescentes: atributos específicos por tipo de ingrediente são um mínimo representativo (extensível); a simulação de mistura não é persistida (é consulta); UI dos ingredientes ainda não edita os atributos por tipo (aceitos pela API).
- Aceite: 5/5 histórias concluídas (CAT-001, EQP-001, EQP-002, WTR-001, WTR-002); módulos `catalog`, `equipment` e `water` criados na arquitetura hexagonal/Modulith; autorização negativa e isolamento entre cervejarias testados; auditoria nos comandos críticos.
