package br.com.brew.brassia.sales.adapter.inbound.web;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.sales.adapter.inbound.web.dto.SalesDtos.ChannelView;
import br.com.brew.brassia.sales.adapter.inbound.web.dto.SalesDtos.CreateChannelRequest;
import br.com.brew.brassia.sales.adapter.inbound.web.dto.SalesDtos.CreateProductRequest;
import br.com.brew.brassia.sales.adapter.inbound.web.dto.SalesDtos.PriceEntryView;
import br.com.brew.brassia.sales.adapter.inbound.web.dto.SalesDtos.PriceFromRequest;
import br.com.brew.brassia.sales.adapter.inbound.web.dto.SalesDtos.PriceScheduleView;
import br.com.brew.brassia.sales.adapter.inbound.web.dto.SalesDtos.ProductView;
import br.com.brew.brassia.sales.adapter.inbound.web.dto.SalesDtos.RenameRequest;
import br.com.brew.brassia.sales.adapter.inbound.web.dto.SalesDtos.SellableLotView;
import br.com.brew.brassia.sales.adapter.inbound.web.dto.SalesDtos.SetActiveRequest;
import br.com.brew.brassia.sales.application.port.inbound.ProductCommands;
import br.com.brew.brassia.sales.application.port.outbound.PriceRepository;
import br.com.brew.brassia.sales.application.port.outbound.ProductRepository;
import br.com.brew.brassia.sales.application.port.outbound.SalesChannelRepository;
import br.com.brew.brassia.sales.domain.Money;
import br.com.brew.brassia.sales.domain.Product;
import br.com.brew.brassia.sales.domain.SalesChannel;
import br.com.brew.brassia.sales.domain.UnknownProductException;
import br.com.brew.brassia.packaging.SellableLotLookup;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Produtos, canais e preços (SAL-001).
 *
 * <p><strong>Não há DELETE.</strong> Produto descontinua e canal desativa — pedido antigo aponta para os
 * dois, e um pedido cujo item ou canal não existe mais é um histórico que ninguém consegue explicar.
 *
 * <p><strong>Preço tem alçada própria e crítica</strong> (`sales.price.manage`): cadastrar um SKU não é
 * o mesmo ato de mudar quanto a cervejaria cobra por ele.
 */
@RestController
@RequestMapping("/api/v1/sales")
final class ProductController {

    private final ProductCommands commands;
    private final ProductRepository products;
    private final SalesChannelRepository channels;
    private final PriceRepository prices;
    private final SellableLotLookup sellableLots;
    private final AuditTrail audit;

    ProductController(ProductCommands commands, ProductRepository products,
            SalesChannelRepository channels, PriceRepository prices, SellableLotLookup sellableLots,
            AuditTrail audit) {
        this.commands = Objects.requireNonNull(commands);
        this.products = Objects.requireNonNull(products);
        this.channels = Objects.requireNonNull(channels);
        this.prices = Objects.requireNonNull(prices);
        this.sellableLots = Objects.requireNonNull(sellableLots);
        this.audit = Objects.requireNonNull(audit);
    }

    @GetMapping("/products")
    List<ProductView> listProducts(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam(defaultValue = "true") boolean onlyActive) {
        principal.requirePermission("sales.catalog.read");
        return products.list(principal.requireBrewery(), onlyActive).stream()
                .map(ProductController::view).toList();
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> createProduct(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody CreateProductRequest request) {
        principal.requirePermission("sales.catalog.manage");
        var brewery = principal.requireBrewery();
        var id = commands.createProduct(brewery, principal.userId(), request.sku(), request.name(),
                request.recipeId(), request.containerId());
        audit.record(AuditEvent.success(brewery, principal.userId(), "sales.product.create",
                "sales.product", id.toString(), Map.of("sku", request.sku())));
        return Map.of("id", id);
    }

    @PutMapping("/products/{id}")
    void renameProduct(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody RenameRequest request) {
        principal.requirePermission("sales.catalog.manage");
        var brewery = principal.requireBrewery();
        commands.renameProduct(brewery, principal.userId(), id, request.name());
        audit.record(AuditEvent.success(brewery, principal.userId(), "sales.product.rename",
                "sales.product", id.toString(), Map.of("name", request.name())));
    }

    @PutMapping("/products/{id}/active")
    void setProductActive(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody SetActiveRequest request) {
        principal.requirePermission("sales.catalog.manage");
        var brewery = principal.requireBrewery();
        commands.setProductActive(brewery, principal.userId(), id, request.active());
        audit.record(AuditEvent.success(brewery, principal.userId(), "sales.product.set-active",
                "sales.product", id.toString(), Map.of("active", String.valueOf(request.active()))));
    }

    @GetMapping("/channels")
    List<ChannelView> listChannels(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam(defaultValue = "true") boolean onlyActive) {
        principal.requirePermission("sales.catalog.read");
        return channels.list(principal.requireBrewery(), onlyActive).stream()
                .map(ProductController::view).toList();
    }

    @PostMapping("/channels")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> createChannel(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody CreateChannelRequest request) {
        principal.requirePermission("sales.catalog.manage");
        var brewery = principal.requireBrewery();
        var id = commands.createChannel(brewery, principal.userId(), request.code(), request.name());
        audit.record(AuditEvent.success(brewery, principal.userId(), "sales.channel.create",
                "sales.channel", id.toString(), Map.of("code", request.code())));
        return Map.of("id", id);
    }

    @PutMapping("/channels/{id}/active")
    void setChannelActive(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody SetActiveRequest request) {
        principal.requirePermission("sales.catalog.manage");
        var brewery = principal.requireBrewery();
        commands.setChannelActive(brewery, principal.userId(), id, request.active());
        audit.record(AuditEvent.success(brewery, principal.userId(), "sales.channel.set-active",
                "sales.channel", id.toString(), Map.of("active", String.valueOf(request.active()))));
    }

    @GetMapping("/products/{id}/prices")
    PriceScheduleView priceSchedule(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID id, @RequestParam UUID channelId) {
        principal.requirePermission("sales.catalog.read");
        var brewery = principal.requireBrewery();
        products.find(brewery, id).orElseThrow(() -> new UnknownProductException("o produto", id));
        var schedule = prices.load(brewery, id, channelId);
        // A linha do tempo inteira, e não só o preço vigente: é ela que explica quanto custava quando um
        // pedido antigo foi feito.
        var entries = schedule.entries().stream()
                .map(e -> new PriceEntryView(e.price().amount(), e.price().currency(), e.taxIncluded(),
                        e.validFrom(), e.validTo()))
                .toList();
        return new PriceScheduleView(id, channelId, entries);
    }

    @PostMapping("/products/{id}/prices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void priceFrom(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody PriceFromRequest request) {
        principal.requirePermission("sales.price.manage");
        var brewery = principal.requireBrewery();
        commands.priceFrom(brewery, principal.userId(), id, request.channelId(),
                new Money(request.amount(), request.currency()), request.taxIncluded(),
                request.validFrom());
        audit.record(AuditEvent.success(brewery, principal.userId(), "sales.price.set", "sales.product",
                id.toString(), Map.of("channelId", request.channelId().toString(),
                        "amount", request.amount().toPlainString(), "currency", request.currency(),
                        "validFrom", request.validFrom().toString())));
    }

    /**
     * Os lotes que dá para prometer deste produto (SAL-001-B).
     *
     * <p><strong>Só os vendáveis.</strong> Misturar o que não pode ser vendido com um aviso ao lado
     * convida ao erro de clicar no primeiro da lista. Quem quiser saber por que um lote específico não
     * está aqui pergunta o estado dele em {@code /packaging/finished-lots/{id}/sale-status}.
     *
     * <p>O produto é o par (receita, embalagem), e é assim que ele encontra os lotes: o lote acabado sabe
     * de que lote de produção veio, e o lote de produção sabe a receita.
     */
    @GetMapping("/products/{id}/sellable-lots")
    List<SellableLotView> sellableLots(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID id) {
        principal.requirePermission("sales.catalog.read");
        var brewery = principal.requireBrewery();
        var product = products.find(brewery, id)
                .orElseThrow(() -> new UnknownProductException("o produto", id));
        return sellableLots
                .sellableLots(brewery, product.recipeId(), product.containerId(), LocalDate.now())
                .stream()
                .map(l -> new SellableLotView(l.finishedLotId(), l.code(), l.batchCode(), l.units(),
                        l.containerVolumeMl(), l.packagedOn(), l.bestBefore()))
                .toList();
    }

    private static ProductView view(Product p) {
        return new ProductView(p.id(), p.sku(), p.name(), p.recipeId(), p.containerId(), p.isActive());
    }

    private static ChannelView view(SalesChannel c) {
        return new ChannelView(c.id(), c.code(), c.name(), c.isActive());
    }
}
