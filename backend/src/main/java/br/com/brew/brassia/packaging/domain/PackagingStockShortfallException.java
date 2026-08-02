package br.com.brew.brassia.packaging.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Não havia embalagem suficiente para o plano. Nada foi reservado: o pedido e o disponível
 * acompanham o erro para o operador decidir entre comprar, reduzir a quantidade ou trocar
 * a embalagem, sem precisar consultar o estoque à parte.
 */
public final class PackagingStockShortfallException extends RuntimeException {

    private final UUID containerId;
    private final BigDecimal requested;
    private final BigDecimal available;
    private final String unit;

    public PackagingStockShortfallException(UUID containerId, BigDecimal requested, BigDecimal available,
            String unit) {
        super("embalagem insuficiente");
        this.containerId = Objects.requireNonNull(containerId);
        this.requested = Objects.requireNonNull(requested);
        this.available = Objects.requireNonNull(available);
        this.unit = Objects.requireNonNull(unit);
    }

    public UUID containerId() { return containerId; }
    public BigDecimal requested() { return requested; }
    public BigDecimal available() { return available; }
    public String unit() { return unit; }
}
