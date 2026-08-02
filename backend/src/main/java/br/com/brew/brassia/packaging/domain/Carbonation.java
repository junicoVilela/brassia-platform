package br.com.brew.brassia.packaging.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Carbonatação decidida para um plano de envase (PKG-002).
 *
 * <p>É uma <strong>decisão confirmada</strong>, não um número calculado de passagem: guarda as
 * entradas, o método, a versão da fórmula, o resultado e quem confirmou. Recalcular substitui a
 * decisão inteira — nunca sobrescreve só o resultado, para entrada e saída não divergirem.
 *
 * <p>Temperatura e CO₂ residual são obrigatórios nos dois métodos: no priming, ignorar o residual
 * pede açúcar demais e estoura a embalagem; na forçada, a mesma pressão carbonata muito menos numa
 * cerveja quente.
 */
public final class Carbonation {

    private final UUID planId;
    private final UUID breweryId;
    private final CarbonationMethod method;
    private final BigDecimal targetVolumes;
    private final BigDecimal referenceTempC;
    private final BigDecimal residualVolumes;
    private final PrimingSugar primingSugar;
    private final BigDecimal primingSugarGrams;
    private final BigDecimal pressureBar;
    private final String calculationMethod;
    private final String calculatorVersion;
    private final List<String> alerts;
    private final UUID confirmedBy;
    private final Instant confirmedAt;
    private final long version;

    private Carbonation(UUID planId, UUID breweryId, CarbonationMethod method, BigDecimal targetVolumes,
            BigDecimal referenceTempC, BigDecimal residualVolumes, PrimingSugar primingSugar,
            BigDecimal primingSugarGrams, BigDecimal pressureBar, String calculationMethod,
            String calculatorVersion, List<String> alerts, UUID confirmedBy, Instant confirmedAt, long version) {
        this.planId = Objects.requireNonNull(planId, "plano é obrigatório");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.method = Objects.requireNonNull(method, "método é obrigatório");
        this.targetVolumes = requirePositive(targetVolumes, "volumes alvo");
        this.referenceTempC = Objects.requireNonNull(referenceTempC, "temperatura de referência é obrigatória");
        this.residualVolumes = requireNonNegative(residualVolumes, "CO₂ residual");
        this.primingSugar = primingSugar;
        this.primingSugarGrams = primingSugarGrams;
        this.pressureBar = pressureBar;
        this.calculationMethod = requireText(calculationMethod, "método de cálculo", 400);
        this.calculatorVersion = requireText(calculatorVersion, "versão do cálculo", 20);
        this.alerts = List.copyOf(Objects.requireNonNull(alerts, "alerts"));
        this.confirmedBy = Objects.requireNonNull(confirmedBy, "confirmação é obrigatória");
        this.confirmedAt = Objects.requireNonNull(confirmedAt, "instante da confirmação é obrigatório");
        this.version = version;
        validateByMethod();
    }

    /**
     * Registra a carbonatação por priming. Recusa quando a cerveja já tem o alvo dissolvido:
     * adicionar açúcar aí não carbonata mais, só gera pressão que a embalagem não comporta.
     */
    public static Carbonation priming(UUID planId, UUID breweryId, BigDecimal targetVolumes,
            BigDecimal referenceTempC, BigDecimal residualVolumes, PrimingSugar sugar, BigDecimal sugarGrams,
            String calculationMethod, String calculatorVersion, List<String> alerts, UUID actorId, Instant at) {
        Objects.requireNonNull(sugar, "açúcar de priming é obrigatório");
        // Entrada malformada é erro de requisição, não conflito de carbonatação: valida antes.
        requirePositive(targetVolumes, "volumes alvo");
        requireNonNegative(residualVolumes, "CO₂ residual");
        if (residualVolumes.compareTo(targetVolumes) >= 0) {
            throw new OverCarbonationException(targetVolumes, residualVolumes);
        }
        return new Carbonation(planId, breweryId, CarbonationMethod.PRIMING, targetVolumes, referenceTempC,
                residualVolumes, sugar, sugarGrams, null, calculationMethod, calculatorVersion, alerts, actorId,
                at, 0);
    }

    public static Carbonation forced(UUID planId, UUID breweryId, BigDecimal targetVolumes,
            BigDecimal referenceTempC, BigDecimal residualVolumes, BigDecimal pressureBar,
            String calculationMethod, String calculatorVersion, List<String> alerts, UUID actorId, Instant at) {
        return new Carbonation(planId, breweryId, CarbonationMethod.FORCED, targetVolumes, referenceTempC,
                residualVolumes, null, null, pressureBar, calculationMethod, calculatorVersion, alerts, actorId,
                at, 0);
    }

    public static Carbonation reconstitute(UUID planId, UUID breweryId, CarbonationMethod method,
            BigDecimal targetVolumes, BigDecimal referenceTempC, BigDecimal residualVolumes,
            PrimingSugar primingSugar, BigDecimal primingSugarGrams, BigDecimal pressureBar,
            String calculationMethod, String calculatorVersion, List<String> alerts, UUID confirmedBy,
            Instant confirmedAt, long version) {
        return new Carbonation(planId, breweryId, method, targetVolumes, referenceTempC, residualVolumes,
                primingSugar, primingSugarGrams, pressureBar, calculationMethod, calculatorVersion, alerts,
                confirmedBy, confirmedAt, version);
    }

    private void validateByMethod() {
        if (method == CarbonationMethod.PRIMING) {
            Objects.requireNonNull(primingSugar, "açúcar de priming é obrigatório no priming");
            requireNonNegative(primingSugarGrams, "massa de açúcar");
            if (pressureBar != null) {
                throw new IllegalArgumentException("priming não tem pressão aplicada");
            }
        } else {
            requireNonNegative(pressureBar, "pressão");
            if (primingSugar != null || primingSugarGrams != null) {
                throw new IllegalArgumentException("carbonatação forçada não usa açúcar de priming");
            }
        }
    }

    /** Quanto de CO₂ ainda falta dissolver para chegar ao alvo. */
    public BigDecimal missingVolumes() {
        var missing = targetVolumes.subtract(residualVolumes);
        return missing.signum() < 0 ? BigDecimal.ZERO : missing;
    }

    private static BigDecimal requirePositive(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " é obrigatório");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(field + " deve ser positivo");
        }
        return value;
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " é obrigatório");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(field + " não pode ser negativo");
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

    public UUID planId() { return planId; }
    public UUID breweryId() { return breweryId; }
    public CarbonationMethod method() { return method; }
    public BigDecimal targetVolumes() { return targetVolumes; }
    public BigDecimal referenceTempC() { return referenceTempC; }
    public BigDecimal residualVolumes() { return residualVolumes; }
    public PrimingSugar primingSugar() { return primingSugar; }
    public BigDecimal primingSugarGrams() { return primingSugarGrams; }
    public BigDecimal pressureBar() { return pressureBar; }
    public String calculationMethod() { return calculationMethod; }
    public String calculatorVersion() { return calculatorVersion; }
    public List<String> alerts() { return alerts; }
    public UUID confirmedBy() { return confirmedBy; }
    public Instant confirmedAt() { return confirmedAt; }
    public long version() { return version; }
}
