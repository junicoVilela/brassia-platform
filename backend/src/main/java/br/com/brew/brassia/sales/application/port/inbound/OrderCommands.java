package br.com.brew.brassia.sales.application.port.inbound;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
     * @param creditOverrideReason o motivo <strong>como ficou gravado no pedido</strong>, vazio quando
     *                             não houve exceção. A auditoria precisa dele inteiro, e não de um
     *                             booleano acompanhado do texto da requisição: no reenvio idempotente as
     *                             duas fontes divergem — o pedido guardado carrega a autorização, e a
     *                             requisição repetida pode vir sem motivo nenhum ou com outro. Ler o
     *                             texto de lá gravaria na trilha uma justificativa que ninguém autorizou,
     *                             ou quebraria o reenvio com um nulo (SAL-004).
     */
    record PlacedOrder(UUID id, Optional<String> creditOverrideReason) {}

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
