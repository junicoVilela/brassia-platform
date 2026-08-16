package br.com.brew.brassia.container.domain;

/**
 * O vasilhame não pode receber <em>este</em> conteúdo.
 *
 * <p>Diferente de {@link ContainerNotFillableException}, que é sobre o vasilhame. Aqui o problema é o
 * líquido: lote vencido, lote em quarentena, volume maior que o contêiner.
 */
public class FillNotAllowedException extends RuntimeException {

    private final String reasonCode;

    private FillNotAllowedException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public static FillNotAllowedException alreadyFull(String lot) {
        return new FillNotAllowedException("already_full",
                "O contêiner já está cheio com o lote " + lot + ". Dois lotes no mesmo vasilhame seria "
                        + "mistura sem registro, e o recall não saberia o que recolher.");
    }

    public static FillNotAllowedException overCapacity() {
        return new FillNotAllowedException("over_capacity",
                "O volume informado não cabe no vasilhame.");
    }

    public static FillNotAllowedException lot(String code, String message) {
        return new FillNotAllowedException(code, message);
    }

    public String reasonCode() {
        return reasonCode;
    }
}
