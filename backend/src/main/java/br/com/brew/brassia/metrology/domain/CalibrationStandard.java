package br.com.brew.brassia.metrology.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Padrão de referência usado para calibrar (MTR-001): o artefato cuja rastreabilidade sustenta a
 * calibração — banho térmico, massa padrão, solução tampão.
 *
 * <p>Tem certificado e validade próprios, e é isso que dá sentido ao "rastreável": um instrumento
 * é confiável porque foi comparado a algo que, por sua vez, foi comparado a um padrão nacional.
 * Quebrada essa cadeia, a calibração vira ritual.
 */
public final class CalibrationStandard {

    private final UUID id;
    private final UUID breweryId;
    private final String code;
    private String description;
    private String certificateNumber;
    private String issuer;
    /** Órgão/rede que sustenta a rastreabilidade (ex.: RBC, INMETRO). */
    private String traceability;
    private LocalDate validUntil;
    private final long version;

    private CalibrationStandard(UUID id, UUID breweryId, String code, String description,
            String certificateNumber, String issuer, String traceability, LocalDate validUntil, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.code = requireText(code, "código", 40);
        this.description = requireText(description, "descrição", 200);
        this.certificateNumber = requireText(certificateNumber, "número do certificado", 60);
        this.issuer = requireText(issuer, "emissor", 120);
        this.traceability = requireText(traceability, "rastreabilidade", 120);
        this.validUntil = Objects.requireNonNull(validUntil, "validade do padrão é obrigatória");
        this.version = version;
    }

    public static CalibrationStandard register(UUID breweryId, String code, String description,
            String certificateNumber, String issuer, String traceability, LocalDate validUntil) {
        return new CalibrationStandard(UUID.randomUUID(), breweryId, code, description, certificateNumber,
                issuer, traceability, validUntil, 0);
    }

    public static CalibrationStandard reconstitute(UUID id, UUID breweryId, String code, String description,
            String certificateNumber, String issuer, String traceability, LocalDate validUntil, long version) {
        return new CalibrationStandard(id, breweryId, code, description, certificateNumber, issuer,
                traceability, validUntil, version);
    }

    /**
     * Vencido em {@code on}. A validade vale <em>até o fim</em> do dia informado no certificado:
     * um padrão que vence em 03/08 ainda calibra no dia 03. Mesma convenção do vencimento de
     * requalificação de cilindro (GAS-001).
     */
    public boolean expiredOn(LocalDate on) {
        return validUntil.isBefore(Objects.requireNonNull(on, "data de referência"));
    }

    /** Renova o certificado do padrão; a validade nova não pode ser anterior à emissão. */
    public void renew(String certificateNumber, String issuer, LocalDate validUntil, LocalDate issuedOn) {
        Objects.requireNonNull(validUntil, "validade");
        Objects.requireNonNull(issuedOn, "data de emissão");
        if (!validUntil.isAfter(issuedOn)) {
            throw new IllegalArgumentException("a validade deve ser posterior à emissão");
        }
        this.certificateNumber = requireText(certificateNumber, "número do certificado", 60);
        this.issuer = requireText(issuer, "emissor", 120);
        this.validUntil = validUntil;
    }

    public UUID id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public String code() {
        return code;
    }

    public String description() {
        return description;
    }

    public String certificateNumber() {
        return certificateNumber;
    }

    public String issuer() {
        return issuer;
    }

    public String traceability() {
        return traceability;
    }

    public LocalDate validUntil() {
        return validUntil;
    }

    public long version() {
        return version;
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
}
