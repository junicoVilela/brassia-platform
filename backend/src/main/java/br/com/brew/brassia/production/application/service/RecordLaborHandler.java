package br.com.brew.brassia.production.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.production.application.port.inbound.RecordLaborUseCase;
import br.com.brew.brassia.production.application.port.outbound.BatchRepository;
import br.com.brew.brassia.production.application.port.outbound.LaborRepository;
import br.com.brew.brassia.production.domain.BatchStatus;
import br.com.brew.brassia.production.domain.LaborEntry;
import br.com.brew.brassia.production.domain.UnknownBatchException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Aponta horas trabalhadas no lote (CST-001-A).
 *
 * <p><strong>Lote cancelado não recebe apontamento</strong>, e encerrado recebe: trabalho de limpeza e
 * fechamento acontece depois de o lote acabar, e recusá-lo obrigaria a apontar antes de trabalhar. O que
 * não faz sentido é apontar hora num lote que foi cancelado — ninguém trabalhou nele.
 */
public final class RecordLaborHandler implements RecordLaborUseCase {

    private final BatchRepository batches;
    private final LaborRepository labor;
    private final AuditTrail audit;

    public RecordLaborHandler(BatchRepository batches, LaborRepository labor, AuditTrail audit) {
        this.batches = Objects.requireNonNull(batches);
        this.labor = Objects.requireNonNull(labor);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public LaborEntry handle(Command command) {
        var batch = batches.findById(command.breweryId(), command.batchId())
                .orElseThrow(() -> new UnknownBatchException(command.batchId()));
        if (batch.status() == BatchStatus.CANCELLED) {
            throw new IllegalStateException("lote cancelado não recebe apontamento de hora");
        }

        var entry = LaborEntry.record(command.breweryId(), command.batchId(), command.activity(),
                command.startedAt(), command.endedAt(), command.people(), command.actorId(),
                Instant.now());
        labor.insert(entry);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "production.labor.record",
                "production.batch", command.batchId().toString(),
                Map.of("activity", entry.activity(),
                        "manHours", entry.manHours().toPlainString(),
                        "people", String.valueOf(entry.people()))));
        return entry;
    }
}
