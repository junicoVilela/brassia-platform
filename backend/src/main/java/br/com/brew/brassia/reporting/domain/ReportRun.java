package br.com.brew.brassia.reporting.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Uma execução de relatório salvo (RPT-003): o que saiu, sob qual alçada e para quem foi.
 *
 * <p><strong>Recusar é um desfecho, não um erro.</strong> Quando o dono perdeu a permissão, a
 * execução acontece e registra a recusa com o motivo. Sumir em silêncio faria a fábrica achar que o
 * relatório continua indo; rodar assim mesmo entregaria dado que ninguém autoriza mais.
 *
 * <p><strong>Entrega é separada de produção.</strong> Uma falha de envio não pode gerar dado novo:
 * o artefato já existe, e reenviar é atualizar a linha de entrega daquele destinatário, nunca
 * refazer a consulta. É o que a chave (execução, destinatário) garante.
 */
public final class ReportRun {

    private final UUID id;
    private final UUID reportId;
    private final UUID breweryId;
    private final int definitionVersion;
    private final String idempotencyKey;
    private final Status status;
    private final String refusalReason;
    private final String content;
    private final Instant periodFrom;
    private final Instant periodTo;
    private final Instant expiresAt;
    private final Instant executedAt;
    private final Map<UUID, Delivery> deliveries;

    private ReportRun(UUID id, UUID reportId, UUID breweryId, int definitionVersion,
            String idempotencyKey, Status status, String refusalReason, String content,
            Instant periodFrom, Instant periodTo, Instant expiresAt, Instant executedAt,
            Map<UUID, Delivery> deliveries) {
        this.id = Objects.requireNonNull(id);
        this.reportId = Objects.requireNonNull(reportId);
        this.breweryId = Objects.requireNonNull(breweryId);
        this.definitionVersion = definitionVersion;
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "chave de idempotência");
        this.status = Objects.requireNonNull(status);
        this.refusalReason = refusalReason;
        this.content = content;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.expiresAt = expiresAt;
        this.executedAt = Objects.requireNonNull(executedAt);
        this.deliveries = Map.copyOf(deliveries);
        requireCoherentOutcome();
    }

    /** Execução que produziu o artefato, com entregas pendentes para cada destinatário. */
    public static ReportRun succeeded(SavedReport report, String idempotencyKey, String content,
            Instant periodFrom, Instant periodTo, Instant executedAt, Set<UUID> recipients) {
        return new ReportRun(UUID.randomUUID(), report.id(), report.breweryId(),
                report.definitionVersion(), idempotencyKey, Status.SUCCEEDED, null,
                Objects.requireNonNull(content, "conteúdo é obrigatório na execução bem-sucedida"),
                periodFrom, periodTo, report.expiryFrom(executedAt), executedAt,
                pending(recipients));
    }

    /** Execução recusada: o dono não tem mais a alçada, e isso fica registrado com o motivo. */
    public static ReportRun refused(SavedReport report, String idempotencyKey, String reason,
            Instant executedAt) {
        return new ReportRun(UUID.randomUUID(), report.id(), report.breweryId(),
                report.definitionVersion(), idempotencyKey, Status.REFUSED,
                requireReason(reason), null, null, null, null, executedAt, Map.of());
    }

    public static ReportRun reconstitute(UUID id, UUID reportId, UUID breweryId,
            int definitionVersion, String idempotencyKey, Status status, String refusalReason,
            String content, Instant periodFrom, Instant periodTo, Instant expiresAt,
            Instant executedAt, Map<UUID, Delivery> deliveries) {
        return new ReportRun(id, reportId, breweryId, definitionVersion, idempotencyKey, status,
                refusalReason, content, periodFrom, periodTo, expiresAt, executedAt, deliveries);
    }

    /**
     * Marca a entrega de um destinatário.
     *
     * <p>Reentregar para quem já recebeu <strong>não duplica</strong>: a linha é atualizada e a
     * contagem de tentativas sobe. É o que impede uma falha parcial de reenviar para a lista inteira.
     */
    public ReportRun deliver(UUID recipient, Delivery.Status status, String detail, Instant at) {
        if (!deliveries.containsKey(recipient)) {
            throw new IllegalArgumentException("destinatário não pertence a esta execução");
        }
        var updated = new LinkedHashMap<>(deliveries);
        var current = updated.get(recipient);
        updated.put(recipient, new Delivery(recipient, status, detail, current.attempts() + 1, at));
        return new ReportRun(id, reportId, breweryId, definitionVersion, idempotencyKey, this.status,
                refusalReason, content, periodFrom, periodTo, expiresAt, executedAt, updated);
    }

    /** Verdadeiro quando o prazo do artefato passou: o link não serve mais. */
    public boolean expired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public boolean succeeded() {
        return status == Status.SUCCEEDED;
    }

    public List<Delivery> deliveryList() {
        return deliveries.values().stream()
                .sorted(java.util.Comparator.comparing(delivery -> delivery.userId().toString()))
                .toList();
    }

    public boolean fullyDelivered() {
        return !deliveries.isEmpty()
                && deliveries.values().stream().allMatch(d -> d.status() == Delivery.Status.DELIVERED);
    }

    private static Map<UUID, Delivery> pending(Set<UUID> recipients) {
        var deliveries = new LinkedHashMap<UUID, Delivery>();
        for (UUID recipient : recipients) {
            deliveries.put(recipient, new Delivery(recipient, Delivery.Status.PENDING, null, 0, null));
        }
        return deliveries;
    }

    private void requireCoherentOutcome() {
        if (status == Status.SUCCEEDED && (content == null || expiresAt == null)) {
            throw new IllegalStateException("execução bem-sucedida precisa de conteúdo e prazo");
        }
        if (status != Status.SUCCEEDED && content != null) {
            throw new IllegalStateException("execução sem sucesso não pode ter conteúdo");
        }
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("motivo da recusa é obrigatório");
        }
        return reason;
    }

    public UUID id() { return id; }
    public UUID reportId() { return reportId; }
    public UUID breweryId() { return breweryId; }
    public int definitionVersion() { return definitionVersion; }
    public String idempotencyKey() { return idempotencyKey; }
    public Status status() { return status; }
    public String refusalReason() { return refusalReason; }
    public String content() { return content; }
    public Instant periodFrom() { return periodFrom; }
    public Instant periodTo() { return periodTo; }
    public Instant expiresAt() { return expiresAt; }
    public Instant executedAt() { return executedAt; }

    public enum Status {
        SUCCEEDED,
        REFUSED,
        FAILED
    }

    /**
     * A entrega a um destinatário.
     *
     * @param attempts quantas vezes já se tentou. Sobe a cada tentativa, e é o que distingue "não
     *                 foi entregue ainda" de "não vai ser entregue nunca"
     */
    public record Delivery(UUID userId, Status status, String detail, int attempts,
            Instant lastAttemptAt) {

        public Delivery {
            Objects.requireNonNull(userId, "destinatário é obrigatório");
            Objects.requireNonNull(status, "situação da entrega é obrigatória");
        }

        public enum Status {
            PENDING,
            DELIVERED,
            REFUSED
        }
    }
}
