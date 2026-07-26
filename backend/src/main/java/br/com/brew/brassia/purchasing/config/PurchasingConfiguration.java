package br.com.brew.brassia.purchasing.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.catalog.IngredientPurchaseLookup;
import br.com.brew.brassia.planning.OrderDemandLookup;
import br.com.brew.brassia.purchasing.IngredientSourcingLookup;
import br.com.brew.brassia.purchasing.StockOnHandLookup;
import br.com.brew.brassia.purchasing.application.port.inbound.ListSuppliersUseCase;
import br.com.brew.brassia.purchasing.application.port.inbound.PurchaseNeedUseCase;
import br.com.brew.brassia.purchasing.application.port.inbound.RegisterSupplierUseCase;
import br.com.brew.brassia.purchasing.application.port.inbound.ShoppingListUseCase;
import br.com.brew.brassia.purchasing.application.port.outbound.SupplierRepository;
import br.com.brew.brassia.purchasing.application.service.ListSuppliersHandler;
import br.com.brew.brassia.purchasing.application.service.PurchaseNeedHandler;
import br.com.brew.brassia.purchasing.application.service.RegisterSupplierHandler;
import br.com.brew.brassia.purchasing.application.service.ShoppingListHandler;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class PurchasingConfiguration {

    @Bean
    RegisterSupplierUseCase registerSupplierUseCase(
            SupplierRepository repository, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new RegisterSupplierHandler(repository, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    ListSuppliersUseCase listSuppliersUseCase(SupplierRepository repository) {
        return new ListSuppliersHandler(repository);
    }

    @Bean
    PurchaseNeedUseCase purchaseNeedUseCase(OrderDemandLookup demandLookup, StockOnHandLookup stockLookup) {
        return new PurchaseNeedHandler(demandLookup, stockLookup);
    }

    @Bean
    ShoppingListUseCase shoppingListUseCase(PurchaseNeedUseCase purchaseNeed, IngredientSourcingLookup sourcing,
            IngredientPurchaseLookup catalog, SupplierRepository suppliers) {
        return new ShoppingListHandler(purchaseNeed, sourcing, catalog, suppliers);
    }
}
