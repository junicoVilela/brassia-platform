package br.com.brew.brassia.sales.application.port.inbound;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A baixa de pagamento do pedido (DEB-SAL-002). */
public interface PaymentCommands {

    /**
     * Registra o recebimento.
     *
     * @param receivedOn nulo é hoje; existe explícito porque o depósito de sexta costuma ser lançado na
     *                   segunda, e a data do extrato é a que concilia
     */
    UUID record(UUID breweryId, UUID actorId, RecordPayment command);

    /**
     * Estorna um recebimento lançado errado — pelo valor cheio, e com motivo.
     *
     * <p>O original continua de pé: os dois lançamentos ficam, e a soma explica a si mesma.
     */
    UUID reverse(UUID breweryId, UUID actorId, UUID paymentId, String reason);

    record RecordPayment(UUID orderId, BigDecimal amount, String currency, LocalDate receivedOn,
            String method, String note) {}
}
