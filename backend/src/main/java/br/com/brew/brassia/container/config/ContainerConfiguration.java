package br.com.brew.brassia.container.config;

import br.com.brew.brassia.container.application.port.outbound.ContainerRepository;
import br.com.brew.brassia.container.application.port.outbound.FillRepository;
import br.com.brew.brassia.container.application.service.ContainerHandlers;
import br.com.brew.brassia.container.application.service.FillHandlers;
import br.com.brew.brassia.packaging.SellableLotLookup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ContainerConfiguration {

    @Bean
    ContainerHandlers containerHandlers(ContainerRepository containers, FillRepository fills) {
        return new ContainerHandlers(containers, fills);
    }

    @Bean
    FillHandlers fillHandlers(ContainerRepository containers, FillRepository fills,
            SellableLotLookup lots) {
        return new FillHandlers(containers, fills, lots);
    }
}
