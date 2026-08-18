package br.com.brew.brassia.sales.application.port.outbound;

import br.com.brew.brassia.sales.domain.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Os recebimentos de um pedido. Só grava; nunca atualiza (DEB-SAL-002). */
public interface PaymentRepository {

    void record(Payment payment);

    Optional<Payment> find(UUID breweryId, UUID paymentId);

    /** Recebimentos e estornos do pedido, na ordem em que foram lançados. */
    List<Payment> ofOrder(UUID breweryId, UUID orderId);
}
