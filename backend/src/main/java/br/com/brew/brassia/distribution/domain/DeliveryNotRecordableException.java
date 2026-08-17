package br.com.brew.brassia.distribution.domain;

/** A entrega não pode ser registrada agora — e a mensagem diz por quê. */
public class DeliveryNotRecordableException extends RuntimeException {

    private final String reasonCode;

    private DeliveryNotRecordableException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public static DeliveryNotRecordableException loadNotOnTheRoad() {
        return new DeliveryNotRecordableException("load_not_on_the_road",
                "A carga ainda não saiu. Uma entrega registrada antes da saída é um registro do que não "
                        + "aconteceu.");
    }

    public static DeliveryNotRecordableException alreadyRecorded() {
        return new DeliveryNotRecordableException("already_recorded",
                "Esta parada já tem prova de entrega. Para mudar o que foi registrado, corrija — a "
                        + "original continua de pé.");
    }

    public static DeliveryNotRecordableException notInStop() {
        return new DeliveryNotRecordableException("not_in_stop",
                "Um dos vasilhames não estava nesta parada. Entregar o que não saiu do depósito faria "
                        + "o estoque perder a conta.");
    }

    public static DeliveryNotRecordableException noOriginal() {
        return new DeliveryNotRecordableException("no_original",
                "Não há prova de entrega para corrigir nesta parada.");
    }

    public String reasonCode() {
        return reasonCode;
    }
}
