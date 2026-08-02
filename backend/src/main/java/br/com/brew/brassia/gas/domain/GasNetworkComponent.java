package br.com.brew.brassia.gas.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Componente da rede de gás (GAS-001): regulador ou manifold.
 *
 * <p>Invariante central: a pressão ajustada de um regulador nunca passa da pressão máxima do
 * próprio componente. O manifold não tem ajuste — só o teto que ele suporta.
 */
public final class GasNetworkComponent {

    private final UUID id;
    private final UUID breweryId;
    private final ComponentKind kind;
    private final String code;
    private String name;
    private BigDecimal maxPressureBar;
    private BigDecimal setPressureBar;
    private boolean active;
    private final long version;

    private GasNetworkComponent(UUID id, UUID breweryId, ComponentKind kind, String code, String name,
            BigDecimal maxPressureBar, BigDecimal setPressureBar, boolean active, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.kind = Objects.requireNonNull(kind, "tipo é obrigatório");
        this.code = requireText(code, "código", 40);
        this.name = requireText(name, "nome", 120);
        this.maxPressureBar = requirePositive(maxPressureBar, "pressão máxima");
        this.setPressureBar = validateSetPressure(kind, setPressureBar, this.maxPressureBar);
        this.active = active;
        this.version = version;
    }

    public static GasNetworkComponent register(UUID breweryId, ComponentKind kind, String code, String name,
            BigDecimal maxPressureBar, BigDecimal setPressureBar) {
        return new GasNetworkComponent(UUID.randomUUID(), breweryId, kind, code, name, maxPressureBar,
                setPressureBar, true, 0);
    }

    public static GasNetworkComponent reconstitute(UUID id, UUID breweryId, ComponentKind kind, String code,
            String name, BigDecimal maxPressureBar, BigDecimal setPressureBar, boolean active, long version) {
        return new GasNetworkComponent(id, breweryId, kind, code, name, maxPressureBar, setPressureBar, active,
                version);
    }

    public void update(String name, BigDecimal maxPressureBar, BigDecimal setPressureBar) {
        this.name = requireText(name, "nome", 120);
        this.maxPressureBar = requirePositive(maxPressureBar, "pressão máxima");
        this.setPressureBar = validateSetPressure(kind, setPressureBar, this.maxPressureBar);
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    private static BigDecimal validateSetPressure(ComponentKind kind, BigDecimal setPressureBar,
            BigDecimal maxPressureBar) {
        if (kind == ComponentKind.MANIFOLD) {
            if (setPressureBar != null) {
                throw new IllegalArgumentException("manifold não tem pressão ajustada");
            }
            return null;
        }
        var value = requirePositive(setPressureBar, "pressão ajustada");
        if (value.compareTo(maxPressureBar) > 0) {
            throw new IllegalArgumentException("pressão ajustada acima da pressão máxima do regulador");
        }
        return value;
    }

    private static BigDecimal requirePositive(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " é obrigatória");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(field + " deve ser positiva");
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
    public ComponentKind kind() { return kind; }
    public String code() { return code; }
    public String name() { return name; }
    public BigDecimal maxPressureBar() { return maxPressureBar; }
    public BigDecimal setPressureBar() { return setPressureBar; }
    public boolean active() { return active; }
    public long version() { return version; }
}
