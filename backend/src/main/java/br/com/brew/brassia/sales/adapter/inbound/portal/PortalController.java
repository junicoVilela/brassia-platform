package br.com.brew.brassia.sales.adapter.inbound.portal;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.packaging.SellableLotLookup;
import br.com.brew.brassia.sales.application.port.inbound.OrderCommands;
import br.com.brew.brassia.sales.application.port.outbound.LotAvailabilityRepository;
import br.com.brew.brassia.sales.application.port.outbound.PortalAccessRepository;
import br.com.brew.brassia.sales.application.port.outbound.PriceRepository;
import br.com.brew.brassia.sales.application.port.outbound.ProductRepository;
import br.com.brew.brassia.sales.application.port.outbound.SalesOrderRepository;
import br.com.brew.brassia.sales.domain.CreditLimitExceededException;
import br.com.brew.brassia.shared.money.Money;
import br.com.brew.brassia.sales.domain.SalesOrder;
import br.com.brew.brassia.shared.security.ForbiddenException;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * O portal do cliente (SAL-003).
 *
 * <p><strong>Árvore separada de propósito, e é a decisão central desta história.</strong> Até aqui o
 * {@code SecurityPrincipal} carrega cervejaria e permissões, e todo endpoint de vendas filtra só por
 * cervejaria — um usuário externo com {@code sales.order.read} veria os pedidos de todos os clientes da
 * casa. A alternativa considerada era acrescentar {@code customerId} ao principal e filtrar em cada
 * consulta; foi recusada porque a proteção passaria a depender de cada endpoint novo lembrar do filtro,
 * que é exatamente o padrão que a OBS-REL-001 encontrou em dez escritas.
 *
 * <p>Aqui o isolamento é <strong>estrutural</strong>: o cliente vem de {@link #access}, que lê o vínculo
 * pelo identificador do usuário autenticado. <strong>Ele nunca vem do caminho nem do corpo</strong> — se
 * viesse, bastaria trocá-lo para ver o pedido de outro, e a separação não teria servido para nada. E um
 * endpoint interno novo não pode vazar por aqui, porque o portal não passa por ele.
 *
 * <p><strong>{@code portal.access} é a única permissão que um usuário de portal recebe</strong>, e ela
 * não abre nada interno.
 */
@RestController
@RequestMapping("/api/v1/portal")
final class PortalController {

    private final PortalAccessRepository portal;
    private final ProductRepository products;
    private final PriceRepository prices;
    private final SellableLotLookup sellableLots;
    private final LotAvailabilityRepository availability;
    private final OrderCommands orders;
    private final SalesOrderRepository orderRepository;
    private final AuditTrail audit;

    PortalController(PortalAccessRepository portal, ProductRepository products, PriceRepository prices,
            SellableLotLookup sellableLots, LotAvailabilityRepository availability, OrderCommands orders,
            SalesOrderRepository orderRepository, AuditTrail audit) {
        this.portal = Objects.requireNonNull(portal);
        this.products = Objects.requireNonNull(products);
        this.prices = Objects.requireNonNull(prices);
        this.sellableLots = Objects.requireNonNull(sellableLots);
        this.availability = Objects.requireNonNull(availability);
        this.orders = Objects.requireNonNull(orders);
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.audit = Objects.requireNonNull(audit);
    }

    /**
     * O catálogo do cliente: os produtos ativos, com o preço do canal DELE e o que há disponível.
     *
     * <p>Produto sem preço no canal não aparece. Mostrá-lo com um traço no lugar do valor faria o
     * cliente pedir e ser recusado depois — e no portal não há um vendedor por perto para explicar.
     */
    @GetMapping("/catalog")
    List<CatalogItem> catalog(@AuthenticationPrincipal SecurityPrincipal principal) {
        var acesso = access(principal);
        var hoje = LocalDate.now();
        return products.list(acesso.breweryId(), true).stream()
                .map(p -> {
                    var preco = prices.load(acesso.breweryId(), p.id(), acesso.channelId()).priceOn(hoje);
                    if (preco.isEmpty()) {
                        return null;
                    }
                    var lotes = sellableLots.sellableLots(acesso.breweryId(), p.recipeId(),
                            p.containerId(), hoje);
                    var livres = availability.freeUnits(acesso.breweryId(),
                            lotes.stream().map(SellableLotLookup.SellableLot::finishedLotId)
                                    .collect(Collectors.toSet()));
                    var disponivel = lotes.stream()
                            .mapToInt(l -> livres.getOrDefault(l.finishedLotId(), l.units()))
                            .sum();
                    return new CatalogItem(p.id(), p.sku(), p.name(), preco.get().price().amount(),
                            preco.get().price().currency(), preco.get().taxIncluded(), disponivel);
                })
                .filter(Objects::nonNull)
                // Sem nada disponível não é oferta: pedir e ser recusado é pior que não ver o item.
                .filter(i -> i.availableUnits() > 0)
                .toList();
    }

    /** Os pedidos do cliente — e só dele, porque o cliente vem do vínculo. */
    @GetMapping("/orders")
    List<PortalOrder> myOrders(@AuthenticationPrincipal SecurityPrincipal principal) {
        var acesso = access(principal);
        return orderRepository.list(acesso.breweryId()).stream()
                .filter(o -> o.customerId().equals(acesso.customerId()))
                .map(PortalController::view)
                .toList();
    }

    @GetMapping("/orders/{id}")
    PortalOrder myOrder(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id) {
        var acesso = access(principal);
        return orderRepository.find(acesso.breweryId(), id)
                .filter(o -> o.customerId().equals(acesso.customerId()))
                // Pedido de outro cliente responde igual a pedido inexistente: distinguir contaria que
                // o identificador existe em algum lugar, que é um oráculo entre clientes.
                .map(PortalController::view)
                .orElseThrow(() -> new br.com.brew.brassia.sales.domain.UnknownProductException(
                        "o pedido", id));
    }

    @GetMapping("/credit")
    CreditView credit(@AuthenticationPrincipal SecurityPrincipal principal) {
        var acesso = access(principal);
        var limite = portal.creditOf(acesso.breweryId(), acesso.customerId());
        var teto = limite.ceilingAmount();
        var comprometido = teto
                .map(c -> portal.committedAmount(acesso.breweryId(), acesso.customerId(), c.currency()))
                .orElse(BigDecimal.ZERO);
        return new CreditView(teto.map(Money::amount).orElse(null),
                teto.map(Money::currency).orElse(null), comprometido);
    }

    /**
     * O cliente faz o próprio pedido.
     *
     * <p>O canal e o cliente vêm do vínculo, e não do corpo: aceitar qualquer um dos dois de fora
     * permitiria comprar no preço de outro canal, ou em nome de outro cliente.
     */
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> place(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PortalOrderRequest request) {
        var acesso = access(principal);
        var total = estimate(acesso, request);
        requireWithinCredit(acesso, total);

        var id = orders.place(acesso.breweryId(), principal.userId(), new OrderCommands.PlaceOrder(
                request.code(), acesso.customerId(), acesso.channelId(),
                request.items().stream()
                        .map(i -> new OrderCommands.OrderItem(i.productId(), i.quantity())).toList(),
                null, request.promisedFor(), idempotencyKey));
        audit.record(AuditEvent.success(acesso.breweryId(), principal.userId(), "portal.order.place",
                "sales.order", id.toString(), Map.of("customerId", acesso.customerId().toString())));
        return Map.of("id", id);
    }

    /**
     * Recompra: o mesmo conteúdo de um pedido anterior, com preço e disponibilidade de hoje.
     *
     * <p><strong>Repete o que foi pedido, e não o que foi cobrado.</strong> Reaproveitar o preço antigo
     * faria o cliente comprar por um valor que não vale mais, e a cervejaria vender abaixo da lista sem
     * ninguém ter decidido isso. O que se repete é a intenção; o preço é o de agora.
     */
    @PostMapping("/orders/{id}/reorder")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> reorder(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable UUID id, @Valid @RequestBody ReorderRequest request) {
        var acesso = access(principal);
        var anterior = orderRepository.find(acesso.breweryId(), id)
                .filter(o -> o.customerId().equals(acesso.customerId()))
                .orElseThrow(() -> new br.com.brew.brassia.sales.domain.UnknownProductException(
                        "o pedido", id));
        var itens = anterior.lines().stream()
                .map(l -> new ItemRequest(l.productId(), l.quantity()))
                .toList();
        return place(principal, idempotencyKey,
                new PortalOrderRequest(request.code(), itens, request.promisedFor()));
    }

    /**
     * O cliente do usuário autenticado.
     *
     * <p>Um usuário com {@code portal.access} e sem vínculo é recusado: a permissão diz que ele pode
     * entrar no portal, e o vínculo diz de quem ele é. Sem o segundo não há a quem mostrar nada.
     */
    private PortalAccessRepository.PortalAccess access(SecurityPrincipal principal) {
        principal.requirePermission("portal.access");
        return portal.findAccess(principal.userId())
                .orElseThrow(() -> new ForbiddenException("usuário sem vínculo de portal"));
    }

    /** O que o pedido vai custar, para conferir o teto antes de reservar qualquer coisa. */
    private Money estimate(PortalAccessRepository.PortalAccess acesso, PortalOrderRequest request) {
        var hoje = LocalDate.now();
        Money total = null;
        for (var item : request.items()) {
            var product = products.find(acesso.breweryId(), item.productId())
                    .orElseThrow(() -> new br.com.brew.brassia.sales.domain.UnknownProductException(
                            "o produto", item.productId()));
            var preco = prices.load(acesso.breweryId(), product.id(), acesso.channelId()).priceOn(hoje)
                    .orElseThrow(() -> new br.com.brew.brassia.sales.domain.NoPriceForProductException(
                            product.sku(), hoje));
            var linha = preco.price().times(item.quantity());
            total = total == null ? linha : total.plus(linha);
        }
        return total;
    }

    private void requireWithinCredit(PortalAccessRepository.PortalAccess acesso, Money total) {
        var limite = portal.creditOf(acesso.breweryId(), acesso.customerId());
        if (!limite.isDefined()) {
            return;
        }
        var teto = limite.ceilingAmount().orElseThrow();
        var comprometido = new Money(
                portal.committedAmount(acesso.breweryId(), acesso.customerId(), teto.currency()),
                teto.currency());
        // A conferência acontece ANTES de reservar: recusar depois deixaria o estoque preso até alguém
        // perceber, e o cliente veria "sem crédito" num lote que ele mesmo travou.
        if (!limite.fits(comprometido, total)) {
            throw new CreditLimitExceededException(teto, comprometido, total);
        }
    }

    private static PortalOrder view(SalesOrder o) {
        var total = o.total();
        return new PortalOrder(o.id(), o.code(), o.status().name(), o.placedOn(),
                o.promisedFor().orElse(null), total.amount(), total.currency(),
                o.lines().stream()
                        .map(l -> new PortalOrderLine(l.sku(), l.quantity(), l.unitPrice().amount(),
                                l.unitPrice().currency()))
                        .toList());
    }

    record CatalogItem(UUID productId, String sku, String name, BigDecimal unitAmount, String currency,
            boolean taxIncluded, int availableUnits) {}

    record PortalOrderRequest(@NotBlank @Size(max = 40) String code,
            @NotEmpty @Valid List<ItemRequest> items, LocalDate promisedFor) {}

    record ItemRequest(@NotNull UUID productId, @Positive int quantity) {}

    record ReorderRequest(@NotBlank @Size(max = 40) String code, LocalDate promisedFor) {}

    /**
     * O pedido como o cliente o vê.
     *
     * <p><strong>Sem os lotes reservados.</strong> Eles são o rastro interno da cervejaria — o cliente
     * precisa saber o que comprou e para quando, não de qual brassa saiu.
     */
    record PortalOrder(UUID id, String code, String status, LocalDate placedOn, LocalDate promisedFor,
            BigDecimal total, String currency, List<PortalOrderLine> lines) {}

    record PortalOrderLine(String sku, int quantity, BigDecimal unitAmount, String currency) {}

    /** Nulos quando não há teto — e sem teto, tudo cabe. */
    record CreditView(BigDecimal ceiling, String currency, BigDecimal committed) {}
}
