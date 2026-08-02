package br.com.brew.brassia.packaging.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * O balanço de volume do envase não fecha: as unidades declaradas (boas e rejeitadas) contêm mais
 * cerveja do que a que saiu do tanque. Alguma das três medidas está errada, e adivinhar qual seria
 * inventar produção — os números vão no erro para o operador conferir.
 */
public final class VolumeBalanceException extends RuntimeException {

    private final BigDecimal inputVolumeLiters;
    private final BigDecimal packagedVolumeLiters;
    private final BigDecimal rejectedVolumeLiters;

    public VolumeBalanceException(BigDecimal inputVolumeLiters, BigDecimal packagedVolumeLiters,
            BigDecimal rejectedVolumeLiters) {
        super("balanço de volume não fecha");
        this.inputVolumeLiters = Objects.requireNonNull(inputVolumeLiters);
        this.packagedVolumeLiters = Objects.requireNonNull(packagedVolumeLiters);
        this.rejectedVolumeLiters = Objects.requireNonNull(rejectedVolumeLiters);
    }

    public BigDecimal inputVolumeLiters() { return inputVolumeLiters; }

    public BigDecimal packagedVolumeLiters() { return packagedVolumeLiters; }

    public BigDecimal rejectedVolumeLiters() { return rejectedVolumeLiters; }

    /** O quanto falta de cerveja para as unidades declaradas fecharem com o tanque. */
    public BigDecimal shortfallLiters() {
        return packagedVolumeLiters.add(rejectedVolumeLiters).subtract(inputVolumeLiters);
    }
}
