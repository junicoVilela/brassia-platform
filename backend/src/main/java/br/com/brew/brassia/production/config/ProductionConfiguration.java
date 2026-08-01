package br.com.brew.brassia.production.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.calculator.CalculatorEngine;
import br.com.brew.brassia.equipment.EquipmentCapacityLookup;
import br.com.brew.brassia.production.BatchAlertPublisher;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.production.application.port.inbound.ApplyCorrectionUseCase;
import br.com.brew.brassia.production.application.port.inbound.CompleteBatchStepUseCase;
import br.com.brew.brassia.production.application.port.inbound.ConfirmAlertUseCase;
import br.com.brew.brassia.production.application.port.inbound.CreateAlertUseCase;
import br.com.brew.brassia.production.application.port.inbound.GetBatchTransferUseCase;
import br.com.brew.brassia.production.application.port.inbound.ListAlertsUseCase;
import br.com.brew.brassia.production.application.port.inbound.ListAppliedCorrectionsUseCase;
import br.com.brew.brassia.production.application.port.inbound.ListBrewCorrectionsUseCase;
import br.com.brew.brassia.production.application.port.inbound.PreviewCorrectionUseCase;
import br.com.brew.brassia.production.application.port.inbound.TransferBatchUseCase;
import br.com.brew.brassia.production.application.port.inbound.GetBatchUseCase;
import br.com.brew.brassia.production.application.port.inbound.ListBatchesUseCase;
import br.com.brew.brassia.production.application.port.inbound.ListMeasurementsUseCase;
import br.com.brew.brassia.production.application.port.inbound.OpenBatchUseCase;
import br.com.brew.brassia.production.application.port.inbound.RecordMeasurementUseCase;
import br.com.brew.brassia.production.application.port.outbound.AlertRepository;
import br.com.brew.brassia.production.application.port.outbound.AppliedCorrectionRepository;
import br.com.brew.brassia.production.application.port.outbound.BatchRepository;
import br.com.brew.brassia.production.application.port.outbound.MeasurementRepository;
import br.com.brew.brassia.production.application.port.outbound.ProductionEventPublisher;
import br.com.brew.brassia.production.application.port.outbound.TransferRepository;
import br.com.brew.brassia.production.application.service.ApplyCorrectionHandler;
import br.com.brew.brassia.production.application.service.CompleteBatchStepHandler;
import br.com.brew.brassia.production.application.service.ConfirmAlertHandler;
import br.com.brew.brassia.production.application.service.ListAppliedCorrectionsHandler;
import br.com.brew.brassia.production.application.service.CreateAlertHandler;
import br.com.brew.brassia.production.application.service.GetBatchTransferHandler;
import br.com.brew.brassia.production.application.service.ListAlertsHandler;
import br.com.brew.brassia.production.application.service.GetBatchHandler;
import br.com.brew.brassia.production.application.service.ListBatchesHandler;
import br.com.brew.brassia.production.application.service.ListBrewCorrectionsHandler;
import br.com.brew.brassia.production.application.service.ListMeasurementsHandler;
import br.com.brew.brassia.production.application.service.OpenBatchHandler;
import br.com.brew.brassia.production.application.service.PreviewCorrectionHandler;
import br.com.brew.brassia.production.application.service.RecordMeasurementHandler;
import br.com.brew.brassia.production.application.service.TransferBatchHandler;
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

    @Bean
    ApplyCorrectionUseCase applyCorrectionUseCase(
            BatchRepository batches, MeasurementRepository measurements, AppliedCorrectionRepository corrections,
            CalculatorEngine engine, ProductionEventPublisher events, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new ApplyCorrectionHandler(batches, measurements, corrections, engine, events, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    ListAppliedCorrectionsUseCase listAppliedCorrectionsUseCase(
            BatchRepository batches, AppliedCorrectionRepository corrections) {
        return new ListAppliedCorrectionsHandler(batches, corrections);
    }

    @Bean
    TransferBatchUseCase transferBatchUseCase(
            BatchRepository batches, TransferRepository transfers, EquipmentCapacityLookup equipment,
            AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new TransferBatchHandler(batches, transfers, equipment, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    GetBatchTransferUseCase getBatchTransferUseCase(BatchRepository batches, TransferRepository transfers) {
        return new GetBatchTransferHandler(batches, transfers);
    }

    @Bean
    CreateAlertUseCase createAlertUseCase(BatchRepository batches, AlertRepository alerts, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new CreateAlertHandler(batches, alerts, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    ListAlertsUseCase listAlertsUseCase(BatchRepository batches, AlertRepository alerts) {
        return new ListAlertsHandler(batches, alerts);
    }

    @Bean
    ConfirmAlertUseCase confirmAlertUseCase(AlertRepository alerts, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new ConfirmAlertHandler(alerts, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    /** Existência de lote publicada para outros módulos (ex.: leituras de fermentação, FER-002). */
    @Bean
    BatchLookup batchLookup(BatchRepository batches) {
        return (breweryId, batchId) -> batches.findById(breweryId, batchId).isPresent();
    }

    /**
     * Abertura de alerta publicada para outros módulos (ex.: agenda de fermentação, FER-004).
     * Entra como STEP na central existente; segue sendo aviso, sem efeito no lote.
     */
    @Bean
    BatchAlertPublisher batchAlertPublisher(CreateAlertUseCase createAlert) {
        return (breweryId, actorId, batchId, message, plannedAt, occurredAt) -> createAlert.handle(
                new CreateAlertUseCase.Command(actorId, breweryId, batchId, "STEP", message, plannedAt, occurredAt))
                .id();
    }
}
