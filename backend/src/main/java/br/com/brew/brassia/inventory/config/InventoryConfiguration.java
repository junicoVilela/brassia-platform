package br.com.brew.brassia.inventory.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.catalog.IngredientSpecLookup;
import br.com.brew.brassia.inventory.application.port.inbound.GetStockBalanceUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.ListLotPropertiesUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.ListStockLotsUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.ListStockMovementsUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.ReceiveStockLotUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.RecordLotPropertiesUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.RecordStockMovementUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.ApprovePhysicalCountUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.CreatePhysicalCountUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.PhysicalCountQueries;
import br.com.brew.brassia.inventory.application.port.inbound.ReleaseStockUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.ReserveStockUseCase;
import br.com.brew.brassia.inventory.application.port.outbound.PhysicalCountRepository;
import br.com.brew.brassia.inventory.application.port.outbound.StockEventPublisher;
import br.com.brew.brassia.inventory.application.port.outbound.StockLedgerRepository;
import br.com.brew.brassia.inventory.application.port.outbound.StockLotPropertyRepository;
import br.com.brew.brassia.inventory.application.port.outbound.StockLotRepository;
import br.com.brew.brassia.inventory.application.service.ApprovePhysicalCountHandler;
import br.com.brew.brassia.inventory.application.service.CreatePhysicalCountHandler;
import br.com.brew.brassia.inventory.application.service.GetStockBalanceHandler;
import br.com.brew.brassia.inventory.application.service.ListLotPropertiesHandler;
import br.com.brew.brassia.inventory.application.service.ListStockLotsHandler;
import br.com.brew.brassia.inventory.application.service.ListStockMovementsHandler;
import br.com.brew.brassia.inventory.application.service.PhysicalCountQueriesHandler;
import br.com.brew.brassia.inventory.application.service.ReceiveStockLotHandler;
import br.com.brew.brassia.inventory.application.service.RecordLotPropertiesHandler;
import br.com.brew.brassia.inventory.application.service.RecordStockMovementHandler;
import br.com.brew.brassia.inventory.application.service.ReleaseStockHandler;
import br.com.brew.brassia.inventory.application.service.ReserveStockHandler;
import br.com.brew.brassia.purchasing.SupplierLookup;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class InventoryConfiguration {

    @Bean
    ReceiveStockLotUseCase receiveStockLotUseCase(
            StockLotRepository repository,
            StockLedgerRepository ledger,
            StockLotPropertyRepository lotProperties,
            IngredientSpecLookup ingredients,
            SupplierLookup suppliers,
            AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new ReceiveStockLotHandler(repository, ledger, lotProperties, ingredients, suppliers, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    RecordLotPropertiesUseCase recordLotPropertiesUseCase(
            StockLotRepository lots, StockLotPropertyRepository properties, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new RecordLotPropertiesHandler(lots, properties, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    ListLotPropertiesUseCase listLotPropertiesUseCase(
            StockLotRepository lots, StockLotPropertyRepository properties) {
        return new ListLotPropertiesHandler(lots, properties);
    }

    @Bean
    ListStockLotsUseCase listStockLotsUseCase(StockLotRepository repository) {
        return new ListStockLotsHandler(repository);
    }

    @Bean
    RecordStockMovementUseCase recordStockMovementUseCase(
            StockLotRepository lots, StockLedgerRepository ledger, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new RecordStockMovementHandler(lots, ledger, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    GetStockBalanceUseCase getStockBalanceUseCase(StockLotRepository lots, StockLedgerRepository ledger) {
        return new GetStockBalanceHandler(lots, ledger);
    }

    @Bean
    ListStockMovementsUseCase listStockMovementsUseCase(StockLedgerRepository ledger) {
        return new ListStockMovementsHandler(ledger);
    }

    @Bean
    ReserveStockUseCase reserveStockUseCase(
            StockLotRepository lots, StockLedgerRepository ledger, StockEventPublisher events, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new ReserveStockHandler(lots, ledger, events, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    ReleaseStockUseCase releaseStockUseCase(
            StockLotRepository lots, StockLedgerRepository ledger, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new ReleaseStockHandler(lots, ledger, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    CreatePhysicalCountUseCase createPhysicalCountUseCase(
            PhysicalCountRepository counts, StockLotRepository lots, StockLedgerRepository ledger, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new CreatePhysicalCountHandler(counts, lots, ledger, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    ApprovePhysicalCountUseCase approvePhysicalCountUseCase(
            PhysicalCountRepository counts, StockLotRepository lots, StockLedgerRepository ledger, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new ApprovePhysicalCountHandler(counts, lots, ledger, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    PhysicalCountQueries physicalCountQueries(PhysicalCountRepository counts) {
        return new PhysicalCountQueriesHandler(counts);
    }
}
