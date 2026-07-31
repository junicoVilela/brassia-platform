package br.com.brew.brassia.fermentation.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.fermentation.application.port.inbound.RecordReadingUseCase;
import br.com.brew.brassia.fermentation.application.port.outbound.ReadingRepository;
import br.com.brew.brassia.fermentation.domain.FermentationReading;
import br.com.brew.brassia.fermentation.domain.ReadingKind;
import br.com.brew.brassia.fermentation.domain.ReadingSource;
import br.com.brew.brassia.production.BatchLookup;
import java.util.Map;
import java.util.Objects;

/**
 * Ingestão idempotente de leitura de fermentação (FER-002). Valida o lote (via lookup
 * publicado do production), computa a validade pela plausibilidade e grava se ausente;
 * reenvio idêntico é no-op. Audita apenas quando cria.
 */
public final class RecordReadingHandler implements RecordReadingUseCase {

    private final ReadingRepository readings;
    private final BatchLookup batches;
    private final AuditTrail audit;

    public RecordReadingHandler(ReadingRepository readings, BatchLookup batches, AuditTrail audit) {
        this.readings = Objects.requireNonNull(readings);
        this.batches = Objects.requireNonNull(batches);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        if (!batches.exists(command.breweryId(), command.batchId())) {
            throw new IllegalArgumentException("lote inexistente: " + command.batchId());
        }
        var reading = FermentationReading.record(command.breweryId(), command.batchId(),
                ReadingKind.of(command.kind()), ReadingSource.of(command.source()), command.value(), command.unit(),
                command.measuredAt());

        var result = readings.upsertIfAbsent(reading);
        var stored = result.stored();

        if (result.created()) {
            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "fermentation.reading.record",
                    "fermentation.reading", stored.id().toString(),
                    Map.of("batchId", command.batchId().toString(), "kind", stored.kind().name(),
                            "valid", String.valueOf(stored.valid()))));
        }
        return new Result(stored.id(), result.created(), stored.valid(), stored.invalidReason());
    }
}
