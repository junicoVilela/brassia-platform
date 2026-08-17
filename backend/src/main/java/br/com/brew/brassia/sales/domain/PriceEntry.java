package br.com.brew.brassia.sales.domain;

import br.com.brew.brassia.shared.money.Money;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Um preço que valeu de uma data até outra (SAL-001).
 *
 * <p><strong>Preço tem vigência, e não é um campo que se sobrescreve.</strong> Um pedido feito em março
 * tem que continuar sendo explicável em dezembro, e isso exige saber quanto o produto custava em março —
 * a mesma razão que faz o consentimento ser um livro na CRM-001. Guardar só o preço atual transformaria
 * todo pedido antigo num número sem origem.
 *
 * @param price valor com moeda explícita
 * @param taxIncluded se o número já contém imposto
 * @param validFrom primeiro dia em que vale, inclusive
 * @param validTo último dia em que vale, inclusive; {@code null} é "até segunda ordem"
 */
public record PriceEntry(Money price, boolean taxIncluded, LocalDate validFrom, LocalDate validTo) {

    public PriceEntry {
        Objects.requireNonNull(price, "preço");
        Objects.requireNonNull(validFrom, "início da vigência");
        if (!price.isPositive()) {
            // Preço zero é brinde ou é engano, e os dois precisam ser tratados de formas opostas.
            // Brinde é desconto no pedido (SAL-002), onde fica registrado que alguém decidiu dar.
            throw new IllegalArgumentException("o preço deve ser positivo");
        }
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("a vigência termina antes de começar");
        }
    }

    /** Se o preço vale no dia. As duas pontas são inclusivas: quem compra no último dia paga o preço. */
    public boolean coversOn(LocalDate day) {
        Objects.requireNonNull(day, "dia");
        return !day.isBefore(validFrom) && (validTo == null || !day.isAfter(validTo));
    }

    public boolean overlaps(PriceEntry other) {
        var thisEnd = Optional.ofNullable(validTo).orElse(LocalDate.MAX);
        var otherEnd = Optional.ofNullable(other.validTo).orElse(LocalDate.MAX);
        return !validFrom.isAfter(otherEnd) && !other.validFrom.isAfter(thisEnd);
    }

    /** Fecha a vigência na véspera de {@code day}, que é como um preço novo encerra o anterior. */
    public PriceEntry closedOnEveOf(LocalDate day) {
        return new PriceEntry(price, taxIncluded, validFrom, day.minusDays(1));
    }

    public boolean isOpenEnded() {
        return validTo == null;
    }
}
