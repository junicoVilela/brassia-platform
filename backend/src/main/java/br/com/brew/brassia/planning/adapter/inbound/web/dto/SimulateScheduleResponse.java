package br.com.brew.brassia.planning.adapter.inbound.web.dto;

import br.com.brew.brassia.planning.application.port.inbound.SimulateScheduleUseCase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Resultado da simulação: sinaliza conflito de equipamento sem persistir. */
public record SimulateScheduleResponse(boolean hasConflict, List<ConflictView> conflicts) {

    public record ConflictView(UUID entryId, Instant scheduledStart, Instant scheduledEnd) {}

    public static SimulateScheduleResponse from(SimulateScheduleUseCase.Result result) {
        var conflicts = result.conflicts().stream()
                .map(c -> new ConflictView(c.entryId(), c.scheduledStart(), c.scheduledEnd()))
                .toList();
        return new SimulateScheduleResponse(result.hasConflict(), conflicts);
    }
}
