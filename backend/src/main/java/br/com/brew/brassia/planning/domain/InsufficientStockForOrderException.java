package br.com.brew.brassia.planning.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A reserva de estoque da OP falhou por falta em um ou mais itens (STK-003-A).
 * All-or-nothing: nada foi reservado. Carrega as faltas para a resposta 409.
 */
public class InsufficientStockForOrderException extends RuntimeException {

    private final transient List<Shortfall> shortfalls;

    public InsufficientStockForOrderException(List<Shortfall> shortfalls) {
        super("estoque insuficiente para reservar a ordem");
        this.shortfalls = List.copyOf(shortfalls);
    }

    public List<Shortfall> shortfalls() {
        return shortfalls;
    }

    public record Shortfall(UUID ingredientId, BigDecimal requested, BigDecimal available, String unit) {}
}
