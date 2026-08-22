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

    /**
     * O mesmo teto, <strong>com a linha do cliente travada até o commit</strong> (DEB-SAL-006).
     *
     * <p><strong>Por que existe uma segunda leitura.</strong> O teto compara um agregado sobre várias
     * linhas — a soma dos pedidos em aberto menos os recebimentos — contra um valor em outra tabela. Não
     * há índice único nem {@code CHECK} que expresse isso, então a invariante que atravessa linhas não
     * cabe no formato que esta casa usa em todo o resto. O que sobra é serializar por cliente: sob
     * {@code READ COMMITTED}, duas vendas simultâneas para o mesmo cliente liam o comprometido antes de
     * qualquer uma commitar, ambas concluíam que cabia, e o cliente terminava acima do teto <em>sem
     * nenhum {@code credit_override} registrado</em> — pior que furar o limite com autorização, porque
     * não deixava rastro.
     *
     * <p><strong>Trava o teto, e não os pedidos.</strong> A linha de {@code sales_customer_credit} é uma
     * por cliente e quase nunca escrita, então ela serve de ponto de encontro barato: quem vende para
     * clientes diferentes não espera ninguém. Travar as linhas de pedido não resolveria — a corrida é
     * sobre um pedido que <em>ainda não existe</em>, e não há o que travar.
     *
     * <p><strong>Sem linha, sem trava, e está certo.</strong> Cliente sem teto cadastrado não tem
     * invariante a proteger: tudo cabe, e não há corrida possível. Quem chama deve tratar o
     * {@link CreditLimit} indefinido antes de olhar qualquer valor.
     *
     * <p>Só vale dentro de transação — fora dela o banco solta a trava no fim da consulta, e a chamada
     * vira a leitura comum com um custo a mais.
     */
    CreditLimit lockCreditOf(UUID breweryId, UUID customerId);

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
