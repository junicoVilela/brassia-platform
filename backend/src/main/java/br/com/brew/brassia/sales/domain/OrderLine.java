package br.com.brew.brassia.sales.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Um item do pedido: o produto, quantas unidades, por quanto, e de quais lotes (SAL-002).
 *
 * <p><strong>O preço é congelado no pedido, e não lido da lista de preço na hora de faturar.</strong> A
 * lista muda — é para isso que ela tem vigência —, e um pedido de março precisa continuar explicável em
 * dezembro. Se a fatura relesse a lista, um aumento aplicado depois mudaria o valor de um pedido que o
 * cliente já aceitou.
 *
 * <p><strong>A soma das reservas tem que fechar com a quantidade.</strong> Um item de 100 unidades com 80
 * reservadas é uma promessa que a cervejaria não pode cumprir — e o pior momento para descobrir isso é na
 * expedição, com o caminhão parado.
 */
public record OrderLine(UUID productId, String sku, int quantity, Money unitPrice, boolean taxIncluded,
        List<LotReservation> reservations) {

    public OrderLine {
        Objects.requireNonNull(productId, "produto");
        Objects.requireNonNull(unitPrice, "preço unitário");
        Objects.requireNonNull(reservations, "reservas");
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("o SKU do item é obrigatório");
        }
        sku = sku.strip();
        if (quantity <= 0) {
            throw new IllegalArgumentException("a quantidade do item deve ser positiva");
        }
        if (!unitPrice.isPositive()) {
            throw new IllegalArgumentException("o preço do item deve ser positivo");
        }
        reservations = List.copyOf(reservations);
        var reservado = reservations.stream().mapToInt(LotReservation::units).sum();
        if (reservado != quantity) {
            throw new UnreservedQuantityException(sku, quantity, reservado);
        }
    }

    /** O que este item custa, antes do arredondamento — que é do total, e não da linha. */
    public Money total() {
        return unitPrice.times(quantity);
    }
}
