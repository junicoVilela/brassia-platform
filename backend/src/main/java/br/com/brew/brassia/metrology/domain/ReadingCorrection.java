package br.com.brew.brassia.metrology.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Correção metrológica de uma leitura (MTR-002).
 *
 * <p><strong>O valor bruto é imutável.</strong> A correção não substitui a medição: ela nasce ao
 * lado dela, com os passos que a produziram. Corrigir de novo cria outro registro — o histórico de
 * como um número foi obtido é o que permite auditar uma liberação meses depois.
 *
 * <p>Instrumento fora de aptidão <strong>não impede</strong> a correção: ela é calculada, e a
 * aptidão do momento fica gravada junto com uma ressalva. É o mesmo princípio de FSL-001 — purga
 * não conferida e vedação reprovada não mudam o número, mudam a confiança nele. Recusar esconderia
 * a medição; aceitar em silêncio mentiria sobre a evidência.
 */
public final class ReadingCorrection {

    private final UUID id;
    private final UUID breweryId;
    private final UUID instrumentId;
    /** Leitura de origem em outro módulo, quando houver; a correção não acopla os dois. */
    private final UUID sourceReadingId;
    private final BigDecimal rawValue;
    private final BigDecimal correctedValue;
    private final String unit;
    private final BigDecimal sampleTempC;
    private final BigDecimal calibrationTempC;
    private final List<CorrectionStep> steps;
    private final Fitness instrumentFitness;
    private final List<String> caveats;
    private final Instant appliedAt;
    private final UUID appliedBy;

    private ReadingCorrection(UUID id, UUID breweryId, UUID instrumentId, UUID sourceReadingId,
            BigDecimal rawValue, BigDecimal correctedValue, String unit, BigDecimal sampleTempC,
            BigDecimal calibrationTempC, List<CorrectionStep> steps, Fitness instrumentFitness,
            List<String> caveats, Instant appliedAt, UUID appliedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.instrumentId = Objects.requireNonNull(instrumentId, "instrumentId");
        this.sourceReadingId = sourceReadingId;
        this.rawValue = Objects.requireNonNull(rawValue, "valor bruto é obrigatório");
        this.correctedValue = Objects.requireNonNull(correctedValue, "valor corrigido é obrigatório");
        this.unit = requireText(unit);
        this.sampleTempC = sampleTempC;
        this.calibrationTempC = calibrationTempC;
        this.steps = List.copyOf(Objects.requireNonNull(steps, "passos"));
        this.instrumentFitness = Objects.requireNonNull(instrumentFitness, "aptidão do instrumento");
        this.caveats = List.copyOf(Objects.requireNonNull(caveats, "ressalvas"));
        this.appliedAt = Objects.requireNonNull(appliedAt, "instante");
        this.appliedBy = Objects.requireNonNull(appliedBy, "responsável");
        if (this.steps.isEmpty()) {
            throw new IllegalArgumentException("correção sem nenhum passo aplicado não é correção");
        }
    }

    /**
     * Monta o registro da correção. Não calcula: recebe o valor final já produzido pelos passos,
     * porque a correção por temperatura vem do hub `calculator` (fórmula compartilhada) e a da
     * curva vem do certificado do instrumento.
     */
    public static ReadingCorrection record(UUID breweryId, Instrument instrument, UUID sourceReadingId,
            BigDecimal rawValue, BigDecimal correctedValue, String unit, BigDecimal sampleTempC,
            BigDecimal calibrationTempC, List<CorrectionStep> steps, Instant appliedAt, UUID appliedBy,
            java.time.LocalDate on) {
        Objects.requireNonNull(instrument, "instrumento é obrigatório");
        var fitness = instrument.fitness(on);
        var caveats = new ArrayList<String>();
        if (!fitness.usable()) {
            caveats.add("O instrumento não estava apto quando a correção foi aplicada (%s); o valor "
                    .formatted(fitness.name())
                    + "corrigido carrega a mesma incerteza da leitura que o originou.");
        }
        instrument.lastCalibration()
                .map(Calibration::restriction)
                .filter(Objects::nonNull)
                .ifPresent(r -> caveats.add("O certificado aprova com restrição: " + r));
        return new ReadingCorrection(UUID.randomUUID(), breweryId, instrument.id(), sourceReadingId, rawValue,
                correctedValue, unit, sampleTempC, calibrationTempC, steps, fitness, caveats, appliedAt,
                appliedBy);
    }

    public static ReadingCorrection reconstitute(UUID id, UUID breweryId, UUID instrumentId,
            UUID sourceReadingId, BigDecimal rawValue, BigDecimal correctedValue, String unit,
            BigDecimal sampleTempC, BigDecimal calibrationTempC, List<CorrectionStep> steps,
            Fitness instrumentFitness, List<String> caveats, Instant appliedAt, UUID appliedBy) {
        return new ReadingCorrection(id, breweryId, instrumentId, sourceReadingId, rawValue, correctedValue,
                unit, sampleTempC, calibrationTempC, steps, instrumentFitness, caveats, appliedAt, appliedBy);
    }

    /** Diferença entre corrigido e bruto — o tamanho do erro que o instrumento carregava. */
    public BigDecimal delta() {
        return correctedValue.subtract(rawValue);
    }

    /** A correção é confiável quando o instrumento estava apto e não há ressalva. */
    public boolean trustworthy() {
        return instrumentFitness.usable() && caveats.isEmpty();
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

    public UUID sourceReadingId() {
        return sourceReadingId;
    }

    public BigDecimal rawValue() {
        return rawValue;
    }

    public BigDecimal correctedValue() {
        return correctedValue;
    }

    public String unit() {
        return unit;
    }

    public BigDecimal sampleTempC() {
        return sampleTempC;
    }

    public BigDecimal calibrationTempC() {
        return calibrationTempC;
    }

    public List<CorrectionStep> steps() {
        return steps;
    }

    public Fitness instrumentFitness() {
        return instrumentFitness;
    }

    public List<String> caveats() {
        return caveats;
    }

    public Instant appliedAt() {
        return appliedAt;
    }

    public UUID appliedBy() {
        return appliedBy;
    }

    private static String requireText(String unit) {
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("unidade é obrigatória");
        }
        var trimmed = unit.trim();
        if (trimmed.length() > 20) {
            throw new IllegalArgumentException("unidade excede 20 caracteres");
        }
        return trimmed;
    }
}
