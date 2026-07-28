package br.com.brew.brassia.sanitation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Etapa de um ciclo (CLN-003): snapshot imutável das faixas autorizadas da versão
 * publicada do POP + o registro de execução. Um parâmetro medido fora da faixa é
 * bloqueado, salvo override com justificativa (autorizado com alçada no caso de uso).
 */
public final class CycleStep {

    private final UUID id;
    private final int sequence;
    private final String method;
    private final String product;
    private final BigDecimal concentrationMinPct;
    private final BigDecimal concentrationMaxPct;
    private final BigDecimal tempMinC;
    private final BigDecimal tempMaxC;
    private final Integer timeMinutes;
    private final String prohibition;
    private final boolean evidenceRequired;

    private CycleStepStatus status;
    private BigDecimal measuredConcentrationPct;
    private BigDecimal measuredTempC;
    private Integer measuredTimeMinutes;
    private String flowActual;
    private String evidence;
    private String outOfOrderReason;
    private boolean overridden;
    private String overrideReason;
    private Instant executedAt;

    private CycleStep(UUID id, int sequence, String method, String product, BigDecimal concentrationMinPct,
            BigDecimal concentrationMaxPct, BigDecimal tempMinC, BigDecimal tempMaxC, Integer timeMinutes,
            String prohibition, boolean evidenceRequired, CycleStepStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.sequence = sequence;
        this.method = method;
        this.product = product;
        this.concentrationMinPct = concentrationMinPct;
        this.concentrationMaxPct = concentrationMaxPct;
        this.tempMinC = tempMinC;
        this.tempMaxC = tempMaxC;
        this.timeMinutes = timeMinutes;
        this.prohibition = prohibition;
        this.evidenceRequired = evidenceRequired;
        this.status = Objects.requireNonNull(status, "status");
    }

    /** Congela a etapa de um POP publicado como etapa PENDING do ciclo. */
    public static CycleStep snapshot(ProcedureStep source) {
        return new CycleStep(UUID.randomUUID(), source.sequence(), source.method(), source.product(),
                source.concentrationMinPct(), source.concentrationMaxPct(), source.tempMinC(), source.tempMaxC(),
                source.timeMinutes(), source.prohibition(), source.evidenceRequired(), CycleStepStatus.PENDING);
    }

    public static CycleStep reconstitute(UUID id, int sequence, String method, String product,
            BigDecimal concentrationMinPct, BigDecimal concentrationMaxPct, BigDecimal tempMinC, BigDecimal tempMaxC,
            Integer timeMinutes, String prohibition, boolean evidenceRequired, CycleStepStatus status,
            BigDecimal measuredConcentrationPct, BigDecimal measuredTempC, Integer measuredTimeMinutes,
            String flowActual, String evidence, String outOfOrderReason, boolean overridden, String overrideReason,
            Instant executedAt) {
        var step = new CycleStep(id, sequence, method, product, concentrationMinPct, concentrationMaxPct, tempMinC,
                tempMaxC, timeMinutes, prohibition, evidenceRequired, status);
        step.measuredConcentrationPct = measuredConcentrationPct;
        step.measuredTempC = measuredTempC;
        step.measuredTimeMinutes = measuredTimeMinutes;
        step.flowActual = flowActual;
        step.evidence = evidence;
        step.outOfOrderReason = outOfOrderReason;
        step.overridden = overridden;
        step.overrideReason = overrideReason;
        step.executedAt = executedAt;
        return step;
    }

    boolean pending() {
        return status == CycleStepStatus.PENDING;
    }

    /**
     * Registra a execução da etapa. Sem override, valida as medições contra a faixa
     * congelada (concentração/temperatura como intervalo; tempo como dwell mínimo) e
     * bloqueia o que estiver fora da ficha. Com override, exige justificativa e apenas
     * marca o desvio (a alçada é verificada no caso de uso).
     */
    void execute(StepExecution exec) {
        if (status == CycleStepStatus.DONE) {
            throw new IllegalStateException("etapa " + sequence + " já foi registrada");
        }
        if (evidenceRequired && isBlank(exec.evidence())) {
            throw new IllegalArgumentException("etapa " + sequence + " exige evidência");
        }
        if (exec.override()) {
            if (isBlank(exec.overrideReason())) {
                throw new IllegalArgumentException("override exige justificativa");
            }
            this.overridden = true;
            this.overrideReason = exec.overrideReason().trim();
        } else {
            checkRange(exec.measuredConcentrationPct(), concentrationMinPct, concentrationMaxPct, "concentração");
            checkRange(exec.measuredTempC(), tempMinC, tempMaxC, "temperatura");
            checkMinDwell(exec.measuredTimeMinutes(), timeMinutes);
        }
        this.measuredConcentrationPct = exec.measuredConcentrationPct();
        this.measuredTempC = exec.measuredTempC();
        this.measuredTimeMinutes = exec.measuredTimeMinutes();
        this.flowActual = trimToNull(exec.flow());
        this.evidence = trimToNull(exec.evidence());
        this.outOfOrderReason = trimToNull(exec.outOfOrderReason());
        this.status = CycleStepStatus.DONE;
        this.executedAt = Instant.now();
    }

    private void checkRange(BigDecimal value, BigDecimal min, BigDecimal max, String field) {
        if (value == null) {
            return;
        }
        if (min != null && value.compareTo(min) < 0 || max != null && value.compareTo(max) > 0) {
            throw new IllegalArgumentException("parâmetro fora da ficha (etapa " + sequence + "): " + field
                    + " = " + value.toPlainString() + " fora da faixa autorizada");
        }
    }

    private void checkMinDwell(Integer measured, Integer required) {
        if (measured != null && required != null && measured < required) {
            throw new IllegalArgumentException("parâmetro fora da ficha (etapa " + sequence + "): tempo "
                    + measured + " min abaixo do mínimo de " + required + " min");
        }
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }

    private static String trimToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    public UUID id() { return id; }
    public int sequence() { return sequence; }
    public String method() { return method; }
    public String product() { return product; }
    public BigDecimal concentrationMinPct() { return concentrationMinPct; }
    public BigDecimal concentrationMaxPct() { return concentrationMaxPct; }
    public BigDecimal tempMinC() { return tempMinC; }
    public BigDecimal tempMaxC() { return tempMaxC; }
    public Integer timeMinutes() { return timeMinutes; }
    public String prohibition() { return prohibition; }
    public boolean evidenceRequired() { return evidenceRequired; }
    public CycleStepStatus status() { return status; }
    public BigDecimal measuredConcentrationPct() { return measuredConcentrationPct; }
    public BigDecimal measuredTempC() { return measuredTempC; }
    public Integer measuredTimeMinutes() { return measuredTimeMinutes; }
    public String flowActual() { return flowActual; }
    public String evidence() { return evidence; }
    public String outOfOrderReason() { return outOfOrderReason; }
    public boolean overridden() { return overridden; }
    public String overrideReason() { return overrideReason; }
    public Instant executedAt() { return executedAt; }
}
