package br.com.brew.brassia.planning.application.port.inbound;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Necessidade de materiais de uma entrada da agenda (usa a receita e o volume planejados). */
public interface ScheduleMaterialsUseCase {
    List<MaterialRequirementUseCase.Line> handle(Query query);

    record Query(UUID breweryId, UUID scheduleEntryId, BigDecimal lossPercent) {}
}
