package br.com.brew.brassia.gas.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Resistência de um tubo de serviço (GAS-002): quanto de pressão ele dissipa por metro, na vazão em
 * que o fabricante mediu.
 *
 * <p>Os números vêm da ficha do tubo, não do sistema: resistência depende do material, do diâmetro
 * interno e da vazão de referência, e cada fabricante publica a sua. Guardar a vazão de referência
 * ao lado da resistência é o que permite escalar corretamente para outra vazão.
 */
public final class LineResistance {

    private final UUID id;
    private final UUID breweryId;
    private final String material;
    private final BigDecimal internalDiameterMm;
    private BigDecimal resistanceBarPerMeter;
    private BigDecimal referenceFlowLpm;
    private final long version;

    private LineResistance(UUID id, UUID breweryId, String material, BigDecimal internalDiameterMm,
            BigDecimal resistanceBarPerMeter, BigDecimal referenceFlowLpm, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.material = requireText(material, "material", 60);
        this.internalDiameterMm = requirePositive(internalDiameterMm, "diâmetro interno");
        this.resistanceBarPerMeter = requirePositive(resistanceBarPerMeter, "resistência");
        this.referenceFlowLpm = requirePositive(referenceFlowLpm, "vazão de referência");
        this.version = version;
    }

    public static LineResistance register(UUID breweryId, String material, BigDecimal internalDiameterMm,
            BigDecimal resistanceBarPerMeter, BigDecimal referenceFlowLpm) {
        return new LineResistance(UUID.randomUUID(), breweryId, material, internalDiameterMm,
                resistanceBarPerMeter, referenceFlowLpm, 0);
    }

    public static LineResistance reconstitute(UUID id, UUID breweryId, String material,
            BigDecimal internalDiameterMm, BigDecimal resistanceBarPerMeter, BigDecimal referenceFlowLpm,
            long version) {
        return new LineResistance(id, breweryId, material, internalDiameterMm, resistanceBarPerMeter,
                referenceFlowLpm, version);
    }

    /** Material e diâmetro são a identidade do tubo; só os números da ficha mudam. */
    public void update(BigDecimal resistanceBarPerMeter, BigDecimal referenceFlowLpm) {
        this.resistanceBarPerMeter = requirePositive(resistanceBarPerMeter, "resistência");
        this.referenceFlowLpm = requirePositive(referenceFlowLpm, "vazão de referência");
    }

    private static BigDecimal requirePositive(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " é obrigatório");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(field + " deve ser positivo");
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

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public String material() { return material; }
    public BigDecimal internalDiameterMm() { return internalDiameterMm; }
    public BigDecimal resistanceBarPerMeter() { return resistanceBarPerMeter; }
    public BigDecimal referenceFlowLpm() { return referenceFlowLpm; }
    public long version() { return version; }
}
