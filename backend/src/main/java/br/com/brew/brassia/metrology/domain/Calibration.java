package br.com.brew.brassia.metrology.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Certificado de calibração de um instrumento (MTR-001).
 *
 * <p>É <strong>histórico imutável</strong>: registrar uma calibração nova não reescreve a
 * anterior, e vencer não apaga nada. É o "certificado permanece" do critério da história — meses
 * depois é preciso poder dizer contra o que o instrumento foi comparado quando produziu a leitura
 * que sustentou uma liberação de lote.
 *
 * <p>A periodicidade <em>não</em> é calculada pelo sistema: o vencimento vem do certificado. O
 * prazo depende da norma, do tipo de instrumento e da criticidade do uso, e derivá-lo de uma regra
 * fixa de meses criaria regra de negócio sem fonte (mesma postura de GAS-001-B).
 */
public final class Calibration {

    private final UUID id;
    private final UUID breweryId;
    private final UUID instrumentId;
    private final UUID standardId;
    private final String standardCode;
    private final LocalDate performedOn;
    private final LocalDate dueOn;
    private final String performedBy;
    private final String certificateNumber;
    private final CalibrationResult result;
    /** Maior desvio observado, na unidade da faixa do instrumento. */
    private final BigDecimal maxDeviation;
    private final String restriction;
    private final String note;

    private Calibration(UUID id, UUID breweryId, UUID instrumentId, UUID standardId, String standardCode,
            LocalDate performedOn, LocalDate dueOn, String performedBy, String certificateNumber,
            CalibrationResult result, BigDecimal maxDeviation, String restriction, String note) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.instrumentId = Objects.requireNonNull(instrumentId, "instrumentId");
        this.standardId = Objects.requireNonNull(standardId, "padrão é obrigatório");
        this.standardCode = requireText(standardCode, "código do padrão", 40);
        this.performedOn = Objects.requireNonNull(performedOn, "data de execução é obrigatória");
        this.dueOn = Objects.requireNonNull(dueOn, "vencimento é obrigatório");
        this.performedBy = requireText(performedBy, "executante", 120);
        this.certificateNumber = requireText(certificateNumber, "número do certificado", 60);
        this.result = Objects.requireNonNull(result, "resultado é obrigatório");
        this.maxDeviation = requireNonNegative(maxDeviation);
        this.restriction = normalizeRestriction(result, restriction);
        this.note = note == null || note.isBlank() ? null : note.trim();
        if (!this.dueOn.isAfter(this.performedOn)) {
            throw new IllegalArgumentException("o vencimento deve ser posterior à execução");
        }
    }

    public static Calibration record(UUID breweryId, UUID instrumentId, CalibrationStandard standard,
            LocalDate performedOn, LocalDate dueOn, String performedBy, String certificateNumber,
            CalibrationResult result, BigDecimal maxDeviation, String restriction, String note) {
        Objects.requireNonNull(standard, "padrão é obrigatório");
        Objects.requireNonNull(performedOn, "data de execução é obrigatória");
        if (standard.expiredOn(performedOn)) {
            throw new ExpiredStandardException(standard.code(), standard.validUntil(), performedOn);
        }
        return new Calibration(UUID.randomUUID(), breweryId, instrumentId, standard.id(), standard.code(),
                performedOn, dueOn, performedBy, certificateNumber, result, maxDeviation, restriction, note);
    }

    public static Calibration reconstitute(UUID id, UUID breweryId, UUID instrumentId, UUID standardId,
            String standardCode, LocalDate performedOn, LocalDate dueOn, String performedBy,
            String certificateNumber, CalibrationResult result, BigDecimal maxDeviation, String restriction,
            String note) {
        return new Calibration(id, breweryId, instrumentId, standardId, standardCode, performedOn, dueOn,
                performedBy, certificateNumber, result, maxDeviation, restriction, note);
    }

    /**
     * Vencida em {@code on}. A validade cobre o dia do vencimento: certificado que vence em 03/08
     * ainda vale no dia 03.
     */
    public boolean expiredOn(LocalDate on) {
        return dueOn.isBefore(Objects.requireNonNull(on, "data de referência"));
    }

    public UUID id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public UUID instrumentId() {
        return instrumentId;
    }

    public UUID standardId() {
        return standardId;
    }

    public String standardCode() {
        return standardCode;
    }

    public LocalDate performedOn() {
        return performedOn;
    }

    public LocalDate dueOn() {
        return dueOn;
    }

    public String performedBy() {
        return performedBy;
    }

    public String certificateNumber() {
        return certificateNumber;
    }

    public CalibrationResult result() {
        return result;
    }

    public BigDecimal maxDeviation() {
        return maxDeviation;
    }

    public String restriction() {
        return restriction;
    }

    public String note() {
        return note;
    }

    /** Restrição é obrigatória quando o certificado aprova com restrição, e proibida no resto. */
    private static String normalizeRestriction(CalibrationResult result, String restriction) {
        var texto = restriction == null || restriction.isBlank() ? null : restriction.trim();
        if (result == CalibrationResult.APPROVED_WITH_RESTRICTION && texto == null) {
            throw new IllegalArgumentException("aprovação com restrição exige descrever a restrição");
        }
        if (result != CalibrationResult.APPROVED_WITH_RESTRICTION && texto != null) {
            throw new IllegalArgumentException("só aprovação com restrição aceita restrição");
        }
        if (texto != null && texto.length() > 200) {
            throw new IllegalArgumentException("restrição excede 200 caracteres");
        }
        return texto;
    }

    private static BigDecimal requireNonNegative(BigDecimal value) {
        Objects.requireNonNull(value, "desvio máximo é obrigatório");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("o desvio máximo não pode ser negativo");
        }
        return value;
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
