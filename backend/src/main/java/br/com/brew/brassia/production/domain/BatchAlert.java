package br.com.brew.brassia.production.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Item da central de alertas/ações do lote (PRD-006): adição, etapa, medição
 * atrasada ou decisão pendente. Persistido (sobrevive a recarga/reconexão);
 * carrega o horário planejado e o realizado (para mostrar atraso/impacto) sem
 * avançar etapa. A confirmação é idempotente.
 */
public final class BatchAlert {

    private final UUID id;
    private final UUID breweryId;
    private final UUID batchId;
    private final BatchAlertKind kind;
    private final String message;
    private final Instant plannedAt;
    private final Instant occurredAt;
    private final BatchAlertStatus status;
    private final Instant createdAt;
    private final UUID createdBy;
    private final Instant confirmedAt;
    private final UUID confirmedBy;

    private BatchAlert(UUID id, UUID breweryId, UUID batchId, BatchAlertKind kind, String message, Instant plannedAt,
            Instant occurredAt, BatchAlertStatus status, Instant createdAt, UUID createdBy, Instant confirmedAt,
            UUID confirmedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.batchId = Objects.requireNonNull(batchId, "batchId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.message = requireMessage(message);
        this.plannedAt = plannedAt;
        this.occurredAt = occurredAt;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.confirmedAt = confirmedAt;
        this.confirmedBy = confirmedBy;
    }

    public static BatchAlert open(UUID breweryId, UUID batchId, BatchAlertKind kind, String message,
            Instant plannedAt, Instant occurredAt, Instant createdAt, UUID createdBy) {
        return new BatchAlert(UUID.randomUUID(), breweryId, batchId, kind, message, plannedAt, occurredAt,
                BatchAlertStatus.PENDING, createdAt, createdBy, null, null);
    }

    public static BatchAlert reconstitute(UUID id, UUID breweryId, UUID batchId, BatchAlertKind kind, String message,
            Instant plannedAt, Instant occurredAt, BatchAlertStatus status, Instant createdAt, UUID createdBy,
            Instant confirmedAt, UUID confirmedBy) {
        return new BatchAlert(id, breweryId, batchId, kind, message, plannedAt, occurredAt, status, createdAt,
                createdBy, confirmedAt, confirmedBy);
    }

    public boolean confirmed() {
        return status == BatchAlertStatus.CONFIRMED;
    }

    private static String requireMessage(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("mensagem do alerta é obrigatória");
        }
        var trimmed = value.trim();
        if (trimmed.length() > 300) {
            throw new IllegalArgumentException("mensagem do alerta excede 300 caracteres");
        }
        return trimmed;
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public UUID batchId() { return batchId; }
    public BatchAlertKind kind() { return kind; }
    public String message() { return message; }
    public Instant plannedAt() { return plannedAt; }
    public Instant occurredAt() { return occurredAt; }
    public BatchAlertStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public UUID createdBy() { return createdBy; }
    public Instant confirmedAt() { return confirmedAt; }
    public UUID confirmedBy() { return confirmedBy; }
}
