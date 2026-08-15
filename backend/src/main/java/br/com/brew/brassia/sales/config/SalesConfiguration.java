package br.com.brew.brassia.sales.config;

import br.com.brew.brassia.packaging.SellableLotLookup;
import br.com.brew.brassia.sales.application.port.inbound.OrderCommands;
import br.com.brew.brassia.sales.application.port.inbound.ProductCommands;
import br.com.brew.brassia.sales.application.port.outbound.PriceRepository;
import br.com.brew.brassia.sales.application.port.outbound.ProductRepository;
import br.com.brew.brassia.sales.application.port.outbound.SalesChannelRepository;
import br.com.brew.brassia.sales.application.port.outbound.LotAvailabilityRepository;
import br.com.brew.brassia.sales.application.port.outbound.SalesOrderRepository;
import br.com.brew.brassia.sales.application.service.OrderHandlers;
import br.com.brew.brassia.sales.application.service.ProductHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SalesConfiguration {

    @Bean
    ProductCommands productCommands(ProductRepository products, SalesChannelRepository channels,
            PriceRepository prices) {
        return new ProductHandlers(products, channels, prices);
    }

    @Bean
    OrderCommands orderCommands(SalesOrderRepository orders, ProductRepository products,
            SalesChannelRepository channels, PriceRepository prices,
            LotAvailabilityRepository availability, SellableLotLookup sellableLots) {
        return new OrderHandlers(orders, products, channels, prices, availability, sellableLots);
    }
}
