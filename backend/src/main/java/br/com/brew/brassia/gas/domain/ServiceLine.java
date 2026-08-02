package br.com.brew.brassia.gas.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Linha de serviço (GAS-002): o caminho da cerveja do barril até a torneira, num ponto de uso.
 *
 * <p>Aplicar um balanceamento <strong>gera uma revisão</strong> e nunca reescreve a anterior. A
 * montagem física de ontem explica a cerveja servida ontem; sobrescrevê-la apagaria a única
 * evidência de por que aquele copo saiu com espuma.
 */
public final class ServiceLine {

    private final UUID id;
    private final UUID breweryId;
    private final String code;
    private String name;
    private final UUID pointOfUseEquipmentId;
    private int currentRevision;
    private final long version;

    private ServiceLine(UUID id, UUID breweryId, String code, String name, UUID pointOfUseEquipmentId,
            int currentRevision, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.code = requireText(code, "código", 40);
        this.name = requireText(name, "nome", 120);
        this.pointOfUseEquipmentId = Objects.requireNonNull(pointOfUseEquipmentId, "ponto de uso é obrigatório");
        if (currentRevision < 0) {
            throw new IllegalArgumentException("revisão não pode ser negativa");
        }
        this.currentRevision = currentRevision;
        this.version = version;
    }

    /** Linha recém-cadastrada ainda não tem montagem aplicada: revisão zero. */
    public static ServiceLine register(UUID breweryId, String code, String name, UUID pointOfUseEquipmentId) {
        return new ServiceLine(UUID.randomUUID(), breweryId, code, name, pointOfUseEquipmentId, 0, 0);
    }

    public static ServiceLine reconstitute(UUID id, UUID breweryId, String code, String name,
            UUID pointOfUseEquipmentId, int currentRevision, long version) {
        return new ServiceLine(id, breweryId, code, name, pointOfUseEquipmentId, currentRevision, version);
    }

    public void rename(String name) {
        this.name = requireText(name, "nome", 120);
    }

    /** Aplica uma montagem: a revisão avança e a anterior fica preservada no histórico. */
    public int applyRevision() {
        this.currentRevision = currentRevision + 1;
        return currentRevision;
    }

    public boolean everApplied() {
        return currentRevision > 0;
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
    public UUID breweryId() { return breweryId; }
    public String code() { return code; }
    public String name() { return name; }
    public UUID pointOfUseEquipmentId() { return pointOfUseEquipmentId; }
    public int currentRevision() { return currentRevision; }
    public long version() { return version; }

    /**
     * Montagem aplicada à linha num momento (GAS-002). Guarda a recomendação e o que foi de fato
     * montado: comprimento e pressão reais podem divergir do recomendado, e é esse desvio que
     * explica depois um serviço fora do padrão.
     */
    public record Revision(UUID id, UUID lineId, UUID breweryId, int revision, String material,
            BigDecimal internalDiameterMm, BigDecimal appliedLengthMeters, BigDecimal recommendedLengthMeters,
            BigDecimal appliedPressureBar, BigDecimal elevationMeters, BigDecimal residualPressureBar,
            BigDecimal targetFlowLpm, BigDecimal servingTempC, BigDecimal targetCo2Volumes,
            String calculationMethod, String calculatorVersion, String note, UUID appliedBy, Instant appliedAt) {

        public Revision {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(lineId, "linha é obrigatória");
            Objects.requireNonNull(breweryId, "breweryId");
            if (revision < 1) {
                throw new IllegalArgumentException("revisão aplicada começa em 1");
            }
            material = requireText(material, "material", 60);
            internalDiameterMm = requirePositive(internalDiameterMm, "diâmetro interno");
            appliedLengthMeters = requirePositive(appliedLengthMeters, "comprimento aplicado");
            Objects.requireNonNull(recommendedLengthMeters, "comprimento recomendado é obrigatório");
            appliedPressureBar = requirePositive(appliedPressureBar, "pressão aplicada");
            Objects.requireNonNull(elevationMeters, "desnível é obrigatório");
            residualPressureBar = requireNonNegative(residualPressureBar, "pressão residual");
            targetFlowLpm = requirePositive(targetFlowLpm, "vazão alvo");
            Objects.requireNonNull(servingTempC, "temperatura de serviço é obrigatória");
            targetCo2Volumes = requirePositive(targetCo2Volumes, "volumes de CO₂ alvo");
            calculationMethod = requireText(calculationMethod, "método de cálculo", 400);
            calculatorVersion = requireText(calculatorVersion, "versão do cálculo", 20);
            note = note == null || note.isBlank() ? null : requireText(note, "observação", 200);
            Objects.requireNonNull(appliedBy, "responsável é obrigatório");
            Objects.requireNonNull(appliedAt, "instante da aplicação é obrigatório");
        }

        /** Diferença entre o que foi montado e o que a recomendação pedia, em metros. */
        public BigDecimal lengthDeviationMeters() {
            return appliedLengthMeters.subtract(recommendedLengthMeters);
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
    }
}
