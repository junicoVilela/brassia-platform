package br.com.brew.brassia.sales.config;

import br.com.brew.brassia.sales.application.port.inbound.ProductCommands;
import br.com.brew.brassia.sales.application.port.outbound.PriceRepository;
import br.com.brew.brassia.sales.application.port.outbound.ProductRepository;
import br.com.brew.brassia.sales.application.port.outbound.SalesChannelRepository;
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
}
