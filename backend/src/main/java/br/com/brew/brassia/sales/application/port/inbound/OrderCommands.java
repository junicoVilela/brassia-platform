package br.com.brew.brassia.sales.application.port.inbound;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** O que se faz com pedido (SAL-002). */
public interface OrderCommands {

    /**
     * Registra o pedido, reservando lote.
     *
     * <p>Com {@code idempotencyKey}, repetir a chamada devolve o mesmo pedido em vez de criar outro —
     * um duplo clique ou um retry de rede não pode reservar o mesmo estoque duas vezes.
     */
    UUID place(UUID breweryId, UUID actorId, PlaceOrder command);

    void cancel(UUID breweryId, UUID actorId, UUID orderId);

    void promiseFor(UUID breweryId, UUID actorId, UUID orderId, LocalDate date);

    /**
     * @param placedOn    nulo é hoje; existe explícito para regularizar pedido tomado por telefone ontem
     * @param promisedFor nulo é "a combinar", e é estado legítimo
     * @param creditOverrideReason quando o pedido passa do teto de crédito, o motivo pelo qual alguém
     *                             autorizou mesmo assim (SAL-004). Nulo é o normal — e sem ele o pedido
     *                             acima do teto é recusado, como no portal. Exige
     *                             {@code sales.order.credit_override}, que é permissão crítica.
     */
    record PlaceOrder(String code, UUID customerId, UUID channelId, List<OrderItem> items,
            LocalDate placedOn, LocalDate promisedFor, String idempotencyKey,
            String creditOverrideReason) {}

    record OrderItem(UUID productId, int quantity) {}
}
