# Eventos de domínio e integração

Eventos são fatos no passado e versionados: `RecipePublished`, `BrewOrderReleased`, `StockReserved`, `BatchStarted`, `MeasurementRecorded`, `CorrectionAccepted`, `EquipmentReleased`, `FermentationStable`, `PackageLotCreated`, `BatchClosed`, `LotQuarantined` e `RecallOpened`.

Eventos internos podem ser síncronos. Integrações usam Outbox no mesmo commit do agregado. Consumidor é idempotente, registra cursor/tentativa e envia falha para tratamento, não para loop infinito.
