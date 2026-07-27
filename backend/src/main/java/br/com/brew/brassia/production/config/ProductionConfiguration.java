package br.com.brew.brassia.production.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.production.application.port.inbound.GetBatchUseCase;
import br.com.brew.brassia.production.application.port.inbound.ListBatchesUseCase;
import br.com.brew.brassia.production.application.port.inbound.OpenBatchUseCase;
import br.com.brew.brassia.production.application.port.outbound.BatchRepository;
import br.com.brew.brassia.production.application.service.GetBatchHandler;
import br.com.brew.brassia.production.application.service.ListBatchesHandler;
import br.com.brew.brassia.production.application.service.OpenBatchHandler;
import br.com.brew.brassia.recipe.RecipeLookup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ProductionConfiguration {

    @Bean
    OpenBatchUseCase openBatchUseCase(BatchRepository repository, RecipeLookup recipes, AuditTrail audit) {
        // Executado dentro da transação do início da OP (listener síncrono).
        return new OpenBatchHandler(repository, recipes, audit);
    }

    @Bean
    ListBatchesUseCase listBatchesUseCase(BatchRepository repository) {
        return new ListBatchesHandler(repository);
    }

    @Bean
    GetBatchUseCase getBatchUseCase(BatchRepository repository) {
        return new GetBatchHandler(repository);
    }
}
