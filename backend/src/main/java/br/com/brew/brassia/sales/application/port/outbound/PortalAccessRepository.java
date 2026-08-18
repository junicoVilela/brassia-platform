package br.com.brew.brassia.sales.application.port.outbound;

import br.com.brew.brassia.sales.domain.CreditLimit;
import java.util.Optional;
import java.util.UUID;

/**
 * Quem entra no portal, e por qual cliente (SAL-003).
 *
 * <p>{@link #findAccess} é a única fonte do cliente de um usuário de portal. O identificador nunca vem
 * do caminho nem do corpo da requisição — se viesse, bastaria trocá-lo para ver o pedido de outro, e a
 * separação de endpoints não teria servido para nada.
 */
public interface PortalAccessRepository {

    void grant(UUID breweryId, UUID userId, UUID customerId, UUID channelId, UUID actorId);

    void revoke(UUID breweryId, UUID userId);

    Optional<PortalAccess> findAccess(UUID userId);

    CreditLimit creditOf(UUID breweryId, UUID customerId);

    void setCredit(UUID breweryId, UUID customerId, java.math.BigDecimal ceiling, String currency,
            UUID actorId);

    /**
     * O que o cliente deve: recebível (DEB-SAL-002).
     *
     * <p>Pedidos {@code PLACED} e {@code FULFILLED} menos os recebimentos, já descontados os estornos, e
     * <strong>por pedido</strong>: pagar a mais num pedido não pode gerar crédito nos outros.
     */
    java.math.BigDecimal committedAmount(UUID breweryId, UUID customerId, String currency);

    record PortalAccess(UUID userId, UUID breweryId, UUID customerId, UUID channelId) {}
}
