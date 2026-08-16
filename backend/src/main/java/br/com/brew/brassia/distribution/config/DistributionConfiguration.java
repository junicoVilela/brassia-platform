package br.com.brew.brassia.distribution.config;

import br.com.brew.brassia.container.ContainerShippingLookup;
import br.com.brew.brassia.distribution.application.port.outbound.LoadRepository;
import br.com.brew.brassia.distribution.application.service.LoadHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class DistributionConfiguration {

    @Bean
    LoadHandlers loadHandlers(LoadRepository loads, ContainerShippingLookup containers) {
        return new LoadHandlers(loads, containers);
    }
}
