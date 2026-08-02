package br.com.brew.brassia.packaging.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Impressão de rótulos de um plano (PKG-004).
 *
 * <p>A <strong>reimpressão exige motivo</strong>: rótulo é material controlado, e a diferença entre
 * "imprimi 800 porque o lote tem 800" e "imprimi mais 40 porque a impressora borrou" é o que evita
 * rótulo sobrando circular fora do lote. Cada impressão guarda a versão do template usada, então um
 * rótulo antigo continua explicável mesmo depois do layout mudar.
 */
public final class LabelPrint {

    private final UUID id;
    private final UUID planId;
    private final UUID breweryId;
    private final UUID templateId;
    private final String templateCode;
    private final int templateVersion;
    private final int quantity;
    private final boolean reprint;
    private final String reason;
    private final UUID printedBy;
    private final Instant printedAt;

    private LabelPrint(UUID id, UUID planId, UUID breweryId, UUID templateId, String templateCode,
            int templateVersion, int quantity, boolean reprint, String reason, UUID printedBy,
            Instant printedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.planId = Objects.requireNonNull(planId, "plano é obrigatório");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.templateId = Objects.requireNonNull(templateId, "template é obrigatório");
        this.templateCode = requireText(templateCode, "código do template", 40);
        if (templateVersion < 1) {
            throw new IllegalArgumentException("versão do template começa em 1");
        }
        this.templateVersion = templateVersion;
        if (quantity < 1) {
            throw new IllegalArgumentException("quantidade impressa deve ser positiva");
        }
        this.quantity = quantity;
        this.reprint = reprint;
        if (reprint) {
            this.reason = requireText(reason, "motivo da reimpressão", 200);
        } else {
            this.reason = reason == null || reason.isBlank() ? null : requireText(reason, "motivo", 200);
        }
        this.printedBy = Objects.requireNonNull(printedBy, "responsável é obrigatório");
        this.printedAt = Objects.requireNonNull(printedAt, "instante da impressão é obrigatório");
    }

    /**
     * Registra uma impressão. {@code reprint} vem de já existir impressão anterior para o plano —
     * não é escolha de quem chama, para ninguém escapar do motivo marcando a segunda tiragem como
     * se fosse a primeira.
     */
    public static LabelPrint record(UUID planId, UUID breweryId, LabelTemplate template, int quantity,
            boolean reprint, String reason, UUID actorId, Instant at) {
        Objects.requireNonNull(template, "template é obrigatório");
        return new LabelPrint(UUID.randomUUID(), planId, breweryId, template.id(), template.code(),
                template.version(), quantity, reprint, reason, actorId, at);
    }

    public static LabelPrint reconstitute(UUID id, UUID planId, UUID breweryId, UUID templateId,
            String templateCode, int templateVersion, int quantity, boolean reprint, String reason,
            UUID printedBy, Instant printedAt) {
        return new LabelPrint(id, planId, breweryId, templateId, templateCode, templateVersion, quantity,
                reprint, reason, printedBy, printedAt);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        var trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(field + " excede " + max + " caracteres");
        }
        return trimmed;
    }

    public UUID id() { return id; }
    public UUID planId() { return planId; }
    public UUID breweryId() { return breweryId; }
    public UUID templateId() { return templateId; }
    public String templateCode() { return templateCode; }
    public int templateVersion() { return templateVersion; }
    public int quantity() { return quantity; }
    public boolean reprint() { return reprint; }
    public String reason() { return reason; }
    public UUID printedBy() { return printedBy; }
    public Instant printedAt() { return printedAt; }
}
