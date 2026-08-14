package br.com.brew.brassia.production.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.production.application.port.inbound.RecordMeasurementUseCase;
import br.com.brew.brassia.production.application.port.outbound.BatchRepository;
import br.com.brew.brassia.production.application.port.outbound.MeasurementRepository;
import br.com.brew.brassia.production.domain.BatchStatus;
import br.com.brew.brassia.production.domain.Measurement;
import br.com.brew.brassia.production.domain.MeasurementKind;
import br.com.brew.brassia.production.domain.MeasurementSource;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Registra uma medição imutável no lote (PRD-003), idempotente para a fila offline (PWA-002).
 *
 * <p>Só lote em andamento; a etapa (se informada) deve ser do lote; a unidade é validada contra a grandeza
 * no domínio. Append-only + auditoria.
 *
 * <p><strong>A idempotência é da restrição única, e a ordem das verificações importa (PWA-002).</strong>
 * O apontamento repetido é reconhecido <em>depois</em> das validações de estado, não antes: se o lote foi
 * encerrado enquanto a fila esperava rede, o reenvio precisa ser recusado como qualquer outro registro
 * tardio — devolver "já registrado" para um lote encerrado inventaria uma medição que nunca entrou.
 */
public final class RecordMeasurementHandler implements RecordMeasurementUseCase {

    private final BatchRepository batches;
    private final MeasurementRepository measurements;
    private final AuditTrail audit;

    public RecordMeasurementHandler(BatchRepository batches, MeasurementRepository measurements, AuditTrail audit) {
        this.batches = Objects.requireNonNull(batches);
        this.measurements = Objects.requireNonNull(measurements);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var batch = batches.findById(command.breweryId(), command.batchId())
                .orElseThrow(() -> new IllegalArgumentException("lote inexistente"));
        // Medição de brassa exige lote em andamento: acrescentar temperatura de mostura a um lote
        // encerrado descreveria um dia que já acabou.
        //
        // ABV é a exceção, e por definição (PKG-004-B): mede-se álcool na cerveja PRONTA, depois de
        // fermentar. Exigir lote em andamento para ele tornaria a medição inexprimível justamente no
        // momento em que ela existe — e o rótulo continuaria imprimindo a conta da receita para sempre.
        var kind = MeasurementKind.of(command.kind());
        var brewDayOnly = kind != MeasurementKind.ABV;
        if (brewDayOnly && batch.status() != BatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("lote não está em andamento");
        }
        if (!brewDayOnly && batch.status() == BatchStatus.CANCELLED) {
            // Lote cancelado não tem cerveja para medir.
            throw new IllegalStateException("lote cancelado");
        }
        if (command.stepId() != null
                && batch.steps().stream().noneMatch(s -> s.id().equals(command.stepId()))) {
            throw new IllegalArgumentException("etapa não pertence ao lote");
        }

        var measurement = Measurement.record(command.breweryId(), command.batchId(), command.stepId(),
                kind, command.value(), command.unit(), command.temperatureC(),
                command.method(), MeasurementSource.of(command.source()), Instant.now(), command.actorId(),
                command.clientRequestId());

        if (measurement.clientRequestId() == null) {
            // Registro pela tela, com rede: sem chave não há repetição a reconhecer.
            measurements.insert(measurement);
            auditRecorded(command, measurement);
            return new Result(measurement.id(), false);
        }

        if (measurements.insertIfAbsent(measurement)) {
            auditRecorded(command, measurement);
            return new Result(measurement.id(), false);
        }

        // Já existia. Devolve a medição GRAVADA, não a recém-montada: elas diferem no id e no instante, e
        // responder a segunda faria a fila do aparelho guardar um id que não existe no servidor.
        var stored = measurements.byClientRequestId(command.breweryId(), measurement.clientRequestId())
                .orElseThrow(() -> new IllegalStateException(
                        "apontamento duplicado sem original: " + measurement.clientRequestId()));

        // Não audita de novo: o registro já foi auditado no primeiro envio, e uma linha por retry encheria
        // a trilha com o comportamento normal de uma fila.
        return new Result(stored.id(), true);
    }

    private void auditRecorded(Command command, Measurement measurement) {
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "production.measurement.record",
                "production.batch", command.batchId().toString(),
                Map.of("kind", measurement.kind().name(), "unit", measurement.unit(),
                        "offline", String.valueOf(measurement.clientRequestId() != null))));
    }
}
