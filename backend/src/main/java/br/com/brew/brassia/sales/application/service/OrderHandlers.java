package br.com.brew.brassia.sales.application.service;

import br.com.brew.brassia.packaging.SellableLotLookup;
import br.com.brew.brassia.sales.application.port.inbound.OrderCommands;
import br.com.brew.brassia.sales.application.port.outbound.LotAvailabilityRepository;
import br.com.brew.brassia.sales.application.port.outbound.PortalAccessRepository;
import br.com.brew.brassia.sales.application.port.outbound.PriceRepository;
import br.com.brew.brassia.sales.application.port.outbound.ProductRepository;
import br.com.brew.brassia.sales.application.port.outbound.SalesChannelRepository;
import br.com.brew.brassia.sales.SalesOrderCancelled;
import br.com.brew.brassia.sales.SalesOrderPlaced;
import br.com.brew.brassia.sales.application.port.outbound.SalesOrderEventPublisher;
import br.com.brew.brassia.sales.application.port.outbound.SalesOrderRepository;
import br.com.brew.brassia.sales.domain.CreditLimitExceededException;
import br.com.brew.brassia.sales.domain.CreditOverride;
import br.com.brew.brassia.sales.domain.InsufficientLotStockException;
import br.com.brew.brassia.sales.domain.LotReservation;
import br.com.brew.brassia.sales.domain.NoPriceForProductException;
import br.com.brew.brassia.sales.domain.OrderLine;
import br.com.brew.brassia.sales.domain.Product;
import br.com.brew.brassia.sales.domain.SalesOrder;
import br.com.brew.brassia.sales.domain.UnknownProductException;
import br.com.brew.brassia.shared.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso do pedido (SAL-002). */
public class OrderHandlers implements OrderCommands {

    private final SalesOrderRepository orders;
    private final ProductRepository products;
    private final SalesChannelRepository channels;
    private final PriceRepository prices;
    private final LotAvailabilityRepository availability;
    private final SellableLotLookup sellableLots;
    private final SalesOrderEventPublisher events;
    private final PortalAccessRepository credit;

    public OrderHandlers(SalesOrderRepository orders, ProductRepository products,
            SalesChannelRepository channels, PriceRepository prices,
            LotAvailabilityRepository availability, SellableLotLookup sellableLots,
            SalesOrderEventPublisher events, PortalAccessRepository credit) {
        this.orders = Objects.requireNonNull(orders);
        this.products = Objects.requireNonNull(products);
        this.channels = Objects.requireNonNull(channels);
        this.prices = Objects.requireNonNull(prices);
        this.availability = Objects.requireNonNull(availability);
        this.sellableLots = Objects.requireNonNull(sellableLots);
        this.events = Objects.requireNonNull(events);
        this.credit = Objects.requireNonNull(credit);
    }

    @Override
    @Transactional
    public UUID place(UUID breweryId, UUID actorId, PlaceOrder command) {
        // IDEMPOTÊNCIA ANTES DE TUDO. Um duplo clique ou um retry de rede não pode criar um segundo
        // pedido que reserva o mesmo estoque — o segundo tiraria do próximo comprador uma cerveja que
        // ninguém vai levar. A garantia de verdade é o índice único; esta consulta é o caminho feliz,
        // que devolve o pedido que já existe em vez de um erro.
        if (command.idempotencyKey() != null) {
            var existente = orders.findByIdempotencyKey(breweryId, command.idempotencyKey());
            if (existente.isPresent()) {
                return existente.get().id();
            }
        }
        channels.find(breweryId, command.channelId())
                .orElseThrow(() -> new UnknownProductException("o canal", command.channelId()));

        var placedOn = command.placedOn() == null ? LocalDate.now() : command.placedOn();
        var linhas = new ArrayList<OrderLine>();
        for (var item : command.items()) {
            linhas.add(montarLinha(breweryId, command.channelId(), item, placedOn));
        }

        var order = SalesOrder.place(UUID.randomUUID(), breweryId, command.customerId(),
                command.channelId(), command.code(), linhas, placedOn, command.promisedFor(),
                Instant.now());
        // O TETO VALE NAS DUAS PORTAS (SAL-004). Antes disto ele só era conferido no portal, e o mesmo
        // cliente tinha dois tratamentos dependendo de por onde o pedido entrou.
        conferirCredito(breweryId, actorId, command, order);
        // O agregado valida a promessa contra a validade ANTES de qualquer reserva ser gravada: recusar
        // depois de reservar deixaria o estoque preso até alguém perceber.
        orders.insert(order, actorId, command.idempotencyKey());
        // Publicado DENTRO da transação: o outbox grava a intenção de entregar no mesmo commit do
        // pedido. Se a transação reverter, o webhook "pedido confirmado" não sai para um pedido que
        // não existe — e um webhook não se desmanda.
        var total = order.total();
        // O total vai em centavos, e não nas quatro casas do armazenamento: quem integra espera o
        // valor da nota, e "120.0000" faz o consumidor adivinhar se é precisão ou descuido.
        events.publish(new SalesOrderPlaced(breweryId, order.id(), order.code(), order.customerId(),
                order.channelId(), total.toMinorUnit(), total.currency(), order.placedOn(),
                order.promisedFor().orElse(null), Instant.now()));
        return order.id();
    }

    /**
     * Confere o teto antes de reservar, e registra quem autorizou passar dele.
     *
     * <p><strong>Antes de reservar</strong>, pela mesma razão do portal: recusar depois deixaria o estoque
     * preso até alguém perceber.
     *
     * <p>A autorização <strong>só é registrada quando foi de fato usada</strong>. Guardá-la num pedido que
     * cabia no limite criaria um registro dizendo "liberado acima do teto" para uma venda que nunca passou
     * de teto nenhum — e quem auditasse contaria exceções que não aconteceram.
     */
    private void conferirCredito(UUID breweryId, UUID actorId, PlaceOrder command, SalesOrder order) {
        var limite = credit.creditOf(breweryId, command.customerId());
        if (!limite.isDefined()) {
            // Sem teto tudo cabe: não recusar por falta de decisão é reversível.
            return;
        }
        var teto = limite.ceilingAmount().orElseThrow();
        var comprometido = new Money(
                credit.committedAmount(breweryId, command.customerId(), teto.currency()),
                teto.currency());
        var total = order.total();
        if (limite.fits(comprometido, total)) {
            return;
        }
        if (command.creditOverrideReason() == null || command.creditOverrideReason().isBlank()) {
            throw new CreditLimitExceededException(teto, comprometido, total);
        }
        order.authorizeAboveCredit(
                new CreditOverride(command.creditOverrideReason(), actorId, Instant.now()));
    }

    @Override
    @Transactional
    public void cancel(UUID breweryId, UUID actorId, UUID orderId) {
        var order = orders.find(breweryId, orderId)
                .orElseThrow(() -> new UnknownProductException("o pedido", orderId));
        order.cancel();
        orders.updateStatusAndPromise(order);
        // Devolver na mesma transação do cancelamento: separados, existiria um instante em que o pedido
        // está cancelado e o estoque continua preso — e uma falha no meio o deixaria preso para sempre.
        order.reservations()
                .forEach(r -> availability.release(breweryId, r.finishedLotId(), r.units()));
        events.publish(new SalesOrderCancelled(breweryId, order.id(), order.code(), order.customerId(),
                Instant.now()));
    }

    @Override
    @Transactional
    public void promiseFor(UUID breweryId, UUID actorId, UUID orderId, LocalDate date) {
        var order = orders.find(breweryId, orderId)
                .orElseThrow(() -> new UnknownProductException("o pedido", orderId));
        order.promiseFor(date);
        orders.updateStatusAndPromise(order);
    }

    private OrderLine montarLinha(UUID breweryId, UUID channelId, OrderItem item, LocalDate placedOn) {
        var product = products.find(breweryId, item.productId())
                .orElseThrow(() -> new UnknownProductException("o produto", item.productId()));
        var preco = prices.load(breweryId, product.id(), channelId).priceOn(placedOn)
                .orElseThrow(() -> new NoPriceForProductException(product.sku(), placedOn));

        var reservas = reservar(breweryId, product, item.quantity(), placedOn);
        // O preço é congelado aqui: a lista muda, e um pedido de março precisa continuar explicável em
        // dezembro.
        return new OrderLine(product.id(), product.sku(), item.quantity(), preco.price(),
                preco.taxIncluded(), reservas);
    }

    /**
     * Reserva do lote que vence primeiro.
     *
     * <p><strong>FEFO, e não FIFO.</strong> Sai antes o que vence antes, e não o que foi produzido
     * antes — são a mesma coisa quase sempre, e diferentes justamente quando importa: um lote novo com
     * validade curta precisa sair na frente de um velho com validade longa, ou vence na prateleira.
     */
    private List<LotReservation> reservar(UUID breweryId, Product product, int quantidade,
            LocalDate on) {
        var candidatos = sellableLots.sellableLots(breweryId, product.recipeId(), product.containerId(), on)
                .stream()
                .sorted(java.util.Comparator.comparing(SellableLotLookup.SellableLot::bestBefore))
                .toList();

        var reservas = new ArrayList<LotReservation>();
        var faltam = quantidade;
        for (var lote : candidatos) {
            if (faltam == 0) {
                break;
            }
            availability.ensure(breweryId, lote.finishedLotId(), lote.units());
            var pegar = Math.min(faltam, lote.units());
            // Tenta o máximo e vai baixando: o lote pode ter parte reservada por outro pedido, e o
            // UPDATE condicional é a única forma de saber quanto realmente cabe sem correr o risco de
            // ler um número que muda entre a leitura e a escrita.
            while (pegar > 0 && !availability.reserve(breweryId, lote.finishedLotId(), pegar)) {
                pegar--;
            }
            if (pegar > 0) {
                reservas.add(new LotReservation(lote.finishedLotId(), lote.code(), pegar,
                        lote.bestBefore()));
                faltam -= pegar;
            }
        }
        if (faltam > 0) {
            // A transação inteira desfaz as reservas parciais: aceitar meio pedido faria o cliente
            // receber confirmação de algo que ninguém conseguiu segurar.
            throw new InsufficientLotStockException(product.sku(), quantidade, quantidade - faltam);
        }
        return reservas;
    }
}
