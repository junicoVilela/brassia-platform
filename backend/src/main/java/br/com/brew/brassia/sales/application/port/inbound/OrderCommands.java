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
    PlacedOrder place(UUID breweryId, UUID actorId, PlaceOrder command);

    /**
     * O que o pedido registrado precisa contar para fora.
     *
     * @param creditOverrideApplied se a autorização acima do teto foi <strong>de fato usada</strong>. A
     *                              auditoria precisa disto e não pode derivá-lo da presença do motivo na
     *                              requisição: um pedido que cabia no limite não registra exceção
     *                              nenhuma, e uma trilha que dissesse o contrário faria quem audita
     *                              contar exceções que nunca aconteceram (SAL-004).
     */
    record PlacedOrder(UUID id, boolean creditOverrideApplied) {}

    void cancel(UUID breweryId, UUID actorId, UUID orderId);

    void promiseFor(UUID breweryId, UUID actorId, UUID orderId, LocalDate date);

    /**
     * @param placedOn    nulo é hoje; existe explícito para regularizar pedido tomado por telefone ontem
     * @param promisedFor nulo é "a combinar", e é estado legítimo
     * @param creditOverrideReason quando o pedido passa do teto de crédito, o motivo pelo qual alguém
     *                             autorizou mesmo assim (SAL-004). Nulo é o normal — e sem ele o pedido
     *                             acima do teto é recusado, como no portal.
     *                             <p><strong>Enviá-lo exige {@code sales.order.credit_override} — que é
     *                             permissão crítica — mesmo num pedido que acabe cabendo no teto.</strong>
     *                             A alçada é conferida na porta, antes de o total ser conhecido, e é
     *                             deliberado que seja assim: quem manda uma justificativa está
     *                             reivindicando autoridade para furar o limite, e reivindicá-la é o ato
     *                             que a permissão governa. O que <em>não</em> acontece é o registro: o
     *                             pedido que coube não guarda autorização nenhuma.
     */
    record PlaceOrder(String code, UUID customerId, UUID channelId, List<OrderItem> items,
            LocalDate placedOn, LocalDate promisedFor, String idempotencyKey,
            String creditOverrideReason) {}

    record OrderItem(UUID productId, int quantity) {}
}
