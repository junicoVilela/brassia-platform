package br.com.brew.brassia.referencedata.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.referencedata.application.port.inbound.ListReferenceDatasetsUseCase;
import br.com.brew.brassia.referencedata.application.port.inbound.ListReferenceSourcesUseCase;
import br.com.brew.brassia.referencedata.application.port.inbound.PublishReferenceDatasetUseCase;
import br.com.brew.brassia.referencedata.application.port.inbound.RecordReferenceDatasetUseCase;
import br.com.brew.brassia.referencedata.application.port.inbound.RegisterReferenceSourceUseCase;
import br.com.brew.brassia.referencedata.application.port.outbound.ReferenceDataEventPublisher;
import br.com.brew.brassia.referencedata.application.port.outbound.ReferenceDatasetRepository;
import br.com.brew.brassia.referencedata.application.port.outbound.ReferenceSourceRepository;
import br.com.brew.brassia.referencedata.application.service.ListReferenceDatasetsHandler;
import br.com.brew.brassia.referencedata.application.service.ListReferenceSourcesHandler;
import br.com.brew.brassia.referencedata.application.service.PublishReferenceDatasetHandler;
import br.com.brew.brassia.referencedata.application.service.RecordReferenceDatasetHandler;
import br.com.brew.brassia.referencedata.application.service.RegisterReferenceSourceHandler;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class ReferenceDataConfiguration {

    @Bean
    RegisterReferenceSourceUseCase registerReferenceSourceUseCase(ReferenceSourceRepository repository,
            AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new RegisterReferenceSourceHandler(repository, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    RecordReferenceDatasetUseCase recordReferenceDatasetUseCase(ReferenceSourceRepository sources,
            ReferenceDatasetRepository datasets, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new RecordReferenceDatasetHandler(sources, datasets, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    PublishReferenceDatasetUseCase publishReferenceDatasetUseCase(ReferenceSourceRepository sources,
            ReferenceDatasetRepository datasets, ReferenceDataEventPublisher events, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new PublishReferenceDatasetHandler(sources, datasets, events, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    ListReferenceSourcesUseCase listReferenceSourcesUseCase(ReferenceSourceRepository repository) {
        return new ListReferenceSourcesHandler(repository);
    }

    @Bean
    ListReferenceDatasetsUseCase listReferenceDatasetsUseCase(ReferenceSourceRepository sources,
            ReferenceDatasetRepository datasets) {
        return new ListReferenceDatasetsHandler(sources, datasets);
    }
}
