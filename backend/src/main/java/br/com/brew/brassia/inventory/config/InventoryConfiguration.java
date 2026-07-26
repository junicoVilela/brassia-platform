package br.com.brew.brassia.inventory.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.catalog.IngredientSpecLookup;
import br.com.brew.brassia.inventory.application.port.inbound.GetStockBalanceUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.ListStockLotsUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.ListStockMovementsUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.ReceiveStockLotUseCase;
import br.com.brew.brassia.inventory.application.port.inbound.RecordStockMovementUseCase;
import br.com.brew.brassia.inventory.application.port.outbound.StockLedgerRepository;
import br.com.brew.brassia.inventory.application.port.outbound.StockLotRepository;
import br.com.brew.brassia.inventory.application.service.GetStockBalanceHandler;
import br.com.brew.brassia.inventory.application.service.ListStockLotsHandler;
import br.com.brew.brassia.inventory.application.service.ListStockMovementsHandler;
import br.com.brew.brassia.inventory.application.service.ReceiveStockLotHandler;
import br.com.brew.brassia.inventory.application.service.RecordStockMovementHandler;
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
            IngredientSpecLookup ingredients,
            SupplierLookup suppliers,
            AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new ReceiveStockLotHandler(repository, ledger, ingredients, suppliers, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
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
}
