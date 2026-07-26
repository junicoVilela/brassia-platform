package br.com.brew.brassia.inventory.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Contagem física de estoque (STK-004): um conjunto de linhas contadas. Nasce
 * {@code OPEN}; ao aprovar, o caso de uso gera os movimentos de ajuste (a
 * geração depende do saldo vivo, então fica na aplicação). A contagem em si é
 * evidência imutável.
 */
public final class PhysicalCount {

    private final PhysicalCountId id;
    private final UUID breweryId;
    private final PhysicalCountStatus status;
    private final List<CountLine> lines;
    private final Instant createdAt;
    private final Instant approvedAt;
    private final long version;

    private PhysicalCount(PhysicalCountId id, UUID breweryId, PhysicalCountStatus status, List<CountLine> lines,
            Instant createdAt, Instant approvedAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.status = Objects.requireNonNull(status, "status");
        this.lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        if (this.lines.isEmpty()) {
            throw new IllegalArgumentException("contagem precisa de ao menos uma linha");
        }
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.approvedAt = approvedAt;
        this.version = version;
    }

    public static PhysicalCount open(UUID breweryId, List<CountLine> lines, Instant createdAt) {
        return new PhysicalCount(PhysicalCountId.newId(), breweryId, PhysicalCountStatus.OPEN, lines, createdAt,
                null, 1);
    }

    public static PhysicalCount reconstitute(PhysicalCountId id, UUID breweryId, PhysicalCountStatus status,
            List<CountLine> lines, Instant createdAt, Instant approvedAt, long version) {
        return new PhysicalCount(id, breweryId, status, lines, createdAt, approvedAt, version);
    }

    public boolean approvable() {
        return status == PhysicalCountStatus.OPEN;
    }

    /** Marca como aprovada (a geração dos ajustes é responsabilidade do caso de uso). */
    public PhysicalCount approve(Instant at) {
        if (!approvable()) {
            throw new IllegalStateException("contagem não está aberta");
        }
        return new PhysicalCount(id, breweryId, PhysicalCountStatus.APPROVED, lines, createdAt,
                Objects.requireNonNull(at, "at"), version + 1);
    }

    public PhysicalCountId id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public PhysicalCountStatus status() {
        return status;
    }

    public List<CountLine> lines() {
        return lines;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant approvedAt() {
        return approvedAt;
    }

    public long version() {
        return version;
    }
}
