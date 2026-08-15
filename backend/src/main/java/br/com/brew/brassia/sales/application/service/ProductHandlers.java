package br.com.brew.brassia.sales.application.service;

import br.com.brew.brassia.sales.application.port.inbound.ProductCommands;
import br.com.brew.brassia.sales.application.port.outbound.PriceRepository;
import br.com.brew.brassia.sales.application.port.outbound.ProductRepository;
import br.com.brew.brassia.sales.application.port.outbound.SalesChannelRepository;
import br.com.brew.brassia.sales.domain.DuplicateSkuException;
import br.com.brew.brassia.sales.domain.Money;
import br.com.brew.brassia.sales.domain.Product;
import br.com.brew.brassia.sales.domain.SalesChannel;
import br.com.brew.brassia.sales.domain.UnknownProductException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso da SAL-001. */
public class ProductHandlers implements ProductCommands {

    private final ProductRepository products;
    private final SalesChannelRepository channels;
    private final PriceRepository prices;

    public ProductHandlers(ProductRepository products, SalesChannelRepository channels,
            PriceRepository prices) {
        this.products = Objects.requireNonNull(products);
        this.channels = Objects.requireNonNull(channels);
        this.prices = Objects.requireNonNull(prices);
    }

    @Override
    @Transactional
    public UUID createProduct(UUID breweryId, UUID actorId, String sku, String name, UUID recipeId,
            UUID containerId) {
        var product = Product.create(UUID.randomUUID(), breweryId, sku, name, recipeId, containerId);
        // O domínio já normalizou para maiúsculas, então a checagem compara o que de fato será gravado.
        if (products.skuTaken(breweryId, product.sku())) {
            throw new DuplicateSkuException("um produto", product.sku());
        }
        products.insert(product, actorId);
        return product.id();
    }

    @Override
    @Transactional
    public void renameProduct(UUID breweryId, UUID actorId, UUID productId, String name) {
        var product = requireProduct(breweryId, productId);
        product.rename(name);
        products.update(product);
    }

    @Override
    @Transactional
    public void setProductActive(UUID breweryId, UUID actorId, UUID productId, boolean active) {
        var product = requireProduct(breweryId, productId);
        if (active) {
            product.restore();
        } else {
            product.discontinue();
        }
        products.update(product);
    }

    @Override
    @Transactional
    public UUID createChannel(UUID breweryId, UUID actorId, String code, String name) {
        var channel = SalesChannel.create(UUID.randomUUID(), breweryId, code, name);
        if (channels.codeTaken(breweryId, channel.code())) {
            throw new DuplicateSkuException("um canal", channel.code());
        }
        channels.insert(channel, actorId);
        return channel.id();
    }

    @Override
    @Transactional
    public void setChannelActive(UUID breweryId, UUID actorId, UUID channelId, boolean active) {
        var channel = requireChannel(breweryId, channelId);
        if (active) {
            channel.reactivate();
        } else {
            channel.deactivate();
        }
        channels.update(channel);
    }

    @Override
    @Transactional
    public void priceFrom(UUID breweryId, UUID actorId, UUID productId, UUID channelId, Money price,
            boolean taxIncluded, LocalDate from) {
        requireProduct(breweryId, productId);
        requireChannel(breweryId, channelId);
        // A linha do tempo inteira é carregada porque a invariante só é verificável olhando todas as
        // vigências: buscar "o preço atual" deixaria passar sobreposição com um período fechado no meio.
        var schedule = prices.load(breweryId, productId, channelId);
        var change = schedule.priceFrom(price, taxIncluded, from);
        // Fechar o anterior e abrir o novo no MESMO commit. Separados, existiria um instante com dois
        // preços abertos — e é justamente o instante que a restrição de exclusão recusaria, deixando a
        // operação pela metade.
        prices.applyChange(breweryId, productId, channelId, change, actorId);
    }

    private Product requireProduct(UUID breweryId, UUID productId) {
        return products.find(breweryId, productId)
                .orElseThrow(() -> new UnknownProductException("o produto", productId));
    }

    private SalesChannel requireChannel(UUID breweryId, UUID channelId) {
        return channels.find(breweryId, channelId)
                .orElseThrow(() -> new UnknownProductException("o canal", channelId));
    }
}
