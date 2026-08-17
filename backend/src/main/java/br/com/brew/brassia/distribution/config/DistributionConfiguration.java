package br.com.brew.brassia.distribution.config;

import br.com.brew.brassia.container.ContainerMovementCommands;
import br.com.brew.brassia.container.ContainerShippingLookup;
import br.com.brew.brassia.distribution.application.port.outbound.LoadRepository;
import br.com.brew.brassia.distribution.application.port.outbound.ProofRepository;
import br.com.brew.brassia.distribution.application.port.outbound.SyncRepository;
import br.com.brew.brassia.distribution.application.service.DeliveryHandlers;
import br.com.brew.brassia.distribution.application.service.LoadHandlers;
import br.com.brew.brassia.distribution.application.service.SyncHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class DistributionConfiguration {

    @Bean
    DeliveryHandlers deliveryHandlers(LoadRepository loads, ProofRepository proofs,
            ContainerMovementCommands containers) {
        return new DeliveryHandlers(loads, proofs, containers);
    }

    @Bean
    SyncHandlers syncHandlers(SyncRepository operations, DeliveryHandlers deliveries) {
        return new SyncHandlers(operations, deliveries);
    }

    @Bean
    LoadHandlers loadHandlers(LoadRepository loads, ContainerShippingLookup containers) {
        return new LoadHandlers(loads, containers);
    }
}
