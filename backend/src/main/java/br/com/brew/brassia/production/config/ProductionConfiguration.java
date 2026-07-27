package br.com.brew.brassia.production.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.calculator.CalculatorEngine;
import br.com.brew.brassia.production.application.port.inbound.CompleteBatchStepUseCase;
import br.com.brew.brassia.production.application.port.inbound.ListBrewCorrectionsUseCase;
import br.com.brew.brassia.production.application.port.inbound.PreviewCorrectionUseCase;
import br.com.brew.brassia.production.application.port.inbound.GetBatchUseCase;
import br.com.brew.brassia.production.application.port.inbound.ListBatchesUseCase;
import br.com.brew.brassia.production.application.port.inbound.ListMeasurementsUseCase;
import br.com.brew.brassia.production.application.port.inbound.OpenBatchUseCase;
import br.com.brew.brassia.production.application.port.inbound.RecordMeasurementUseCase;
import br.com.brew.brassia.production.application.port.outbound.BatchRepository;
import br.com.brew.brassia.production.application.port.outbound.MeasurementRepository;
import br.com.brew.brassia.production.application.service.CompleteBatchStepHandler;
import br.com.brew.brassia.production.application.service.GetBatchHandler;
import br.com.brew.brassia.production.application.service.ListBatchesHandler;
import br.com.brew.brassia.production.application.service.ListBrewCorrectionsHandler;
import br.com.brew.brassia.production.application.service.ListMeasurementsHandler;
import br.com.brew.brassia.production.application.service.OpenBatchHandler;
import br.com.brew.brassia.production.application.service.PreviewCorrectionHandler;
import br.com.brew.brassia.production.application.service.RecordMeasurementHandler;
import br.com.brew.brassia.recipe.RecipeLookup;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Bean
    CompleteBatchStepUseCase completeBatchStepUseCase(
            BatchRepository repository, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new CompleteBatchStepHandler(repository, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    RecordMeasurementUseCase recordMeasurementUseCase(
            BatchRepository batches, MeasurementRepository measurements, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new RecordMeasurementHandler(batches, measurements, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    ListMeasurementsUseCase listMeasurementsUseCase(BatchRepository batches, MeasurementRepository measurements) {
        return new ListMeasurementsHandler(batches, measurements);
    }

    @Bean
    ListBrewCorrectionsUseCase listBrewCorrectionsUseCase(CalculatorEngine engine) {
        return new ListBrewCorrectionsHandler(engine);
    }

    @Bean
    PreviewCorrectionUseCase previewCorrectionUseCase(BatchRepository batches, CalculatorEngine engine) {
        return new PreviewCorrectionHandler(batches, engine);
    }
}
