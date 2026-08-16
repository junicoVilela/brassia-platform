package br.com.brew.brassia.container.config;

import br.com.brew.brassia.container.application.port.outbound.ContainerRepository;
import br.com.brew.brassia.container.application.service.ContainerHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ContainerConfiguration {

    @Bean
    ContainerHandlers containerHandlers(ContainerRepository containers) {
        return new ContainerHandlers(containers);
    }
}
