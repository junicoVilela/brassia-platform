package br.com.brew.brassia.brewery.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.brewery.application.port.inbound.ListBreweriesUseCase;
import br.com.brew.brassia.brewery.application.port.inbound.RegisterBreweryUseCase;
import br.com.brew.brassia.brewery.application.port.outbound.BreweryRepository;
import br.com.brew.brassia.brewery.application.service.ListBreweriesHandler;
import br.com.brew.brassia.brewery.application.service.RegisterBreweryHandler;
import java.util.Objects;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BreweryBootstrapProperties.class)
class BreweryConfiguration {
    @Bean
    RegisterBreweryUseCase registerBreweryUseCase(
            BreweryRepository repository,
            AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new RegisterBreweryHandler(repository, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(
                transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    ListBreweriesUseCase listBreweriesUseCase(BreweryRepository repository) {
        return new ListBreweriesHandler(repository);
    }
}
