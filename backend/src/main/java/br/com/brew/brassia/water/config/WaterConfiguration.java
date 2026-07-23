package br.com.brew.brassia.water.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.water.application.port.inbound.ListWaterProfilesUseCase;
import br.com.brew.brassia.water.application.port.inbound.ListWaterReportsUseCase;
import br.com.brew.brassia.water.application.port.inbound.ListWaterSourcesUseCase;
import br.com.brew.brassia.water.application.port.inbound.RecordWaterReportUseCase;
import br.com.brew.brassia.water.application.port.inbound.RegisterWaterProfileUseCase;
import br.com.brew.brassia.water.application.port.inbound.RegisterWaterSourceUseCase;
import br.com.brew.brassia.water.application.port.inbound.SimulateWaterBlendUseCase;
import br.com.brew.brassia.water.application.port.inbound.UpdateWaterProfileUseCase;
import br.com.brew.brassia.water.application.port.inbound.UpdateWaterSourceUseCase;
import br.com.brew.brassia.water.application.port.outbound.WaterProfileRepository;
import br.com.brew.brassia.water.application.port.outbound.WaterReportRepository;
import br.com.brew.brassia.water.application.port.outbound.WaterSourceRepository;
import br.com.brew.brassia.water.application.service.ListWaterProfilesHandler;
import br.com.brew.brassia.water.application.service.ListWaterReportsHandler;
import br.com.brew.brassia.water.application.service.ListWaterSourcesHandler;
import br.com.brew.brassia.water.application.service.RecordWaterReportHandler;
import br.com.brew.brassia.water.application.service.RegisterWaterProfileHandler;
import br.com.brew.brassia.water.application.service.RegisterWaterSourceHandler;
import br.com.brew.brassia.water.application.service.SimulateWaterBlendHandler;
import br.com.brew.brassia.water.application.service.UpdateWaterProfileHandler;
import br.com.brew.brassia.water.application.service.UpdateWaterSourceHandler;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class WaterConfiguration {

    @Bean
    RegisterWaterSourceUseCase registerWaterSourceUseCase(
            WaterSourceRepository repository, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new RegisterWaterSourceHandler(repository, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    UpdateWaterSourceUseCase updateWaterSourceUseCase(
            WaterSourceRepository repository, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new UpdateWaterSourceHandler(repository, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    ListWaterSourcesUseCase listWaterSourcesUseCase(WaterSourceRepository repository) {
        return new ListWaterSourcesHandler(repository);
    }

    @Bean
    RecordWaterReportUseCase recordWaterReportUseCase(WaterSourceRepository sources,
            WaterReportRepository reports, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new RecordWaterReportHandler(sources, reports, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    ListWaterReportsUseCase listWaterReportsUseCase(WaterReportRepository repository) {
        return new ListWaterReportsHandler(repository);
    }

    @Bean
    RegisterWaterProfileUseCase registerWaterProfileUseCase(
            WaterProfileRepository repository, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new RegisterWaterProfileHandler(repository, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    UpdateWaterProfileUseCase updateWaterProfileUseCase(
            WaterProfileRepository repository, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new UpdateWaterProfileHandler(repository, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    ListWaterProfilesUseCase listWaterProfilesUseCase(WaterProfileRepository repository) {
        return new ListWaterProfilesHandler(repository);
    }

    @Bean
    SimulateWaterBlendUseCase simulateWaterBlendUseCase(WaterSourceRepository sources,
            WaterReportRepository reports, WaterProfileRepository profiles) {
        return new SimulateWaterBlendHandler(sources, reports, profiles);
    }
}
