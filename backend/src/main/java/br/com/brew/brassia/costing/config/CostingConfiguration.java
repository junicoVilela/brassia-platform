package br.com.brew.brassia.costing.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.catalog.IngredientPurchaseLookup;
import br.com.brew.brassia.costing.CostContributor;
import br.com.brew.brassia.costing.MaterialActualSource;
import br.com.brew.brassia.costing.application.port.inbound.CostCommands;
import br.com.brew.brassia.costing.application.port.inbound.CostQueries;
import br.com.brew.brassia.costing.application.port.inbound.VarianceQueries;
import br.com.brew.brassia.costing.application.port.outbound.BatchCostRepository;
import br.com.brew.brassia.costing.application.service.BatchCostAssembler;
import br.com.brew.brassia.costing.application.service.BatchVarianceAssembler;
import br.com.brew.brassia.costing.application.service.CostHandlers;
import br.com.brew.brassia.packaging.PackagingOutcomeLookup;
import br.com.brew.brassia.planning.OrderPlanLookup;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.production.BatchOutcomeLookup;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fechar o custo grava cabeçalho, parcelas e lacunas num commit só: um custo fechado sem as suas
 * linhas seria um total que ninguém consegue explicar, e sem as lacunas seria um total que parece
 * completo.
 */
@Configuration(proxyBeanMethods = false)
class CostingConfiguration {

    /** Recebe todos os contribuintes registrados — inclusive os que ainda não existem. */
    @Bean
    BatchCostAssembler batchCostAssembler(BatchLookup batches, List<CostContributor> contributors) {
        return new BatchCostAssembler(batches, contributors);
    }

    @Bean
    CostQueries costQueries(BatchCostRepository costs, BatchCostAssembler assembler) {
        return new CostHandlers.Queries(costs, assembler);
    }

    @Bean
    BatchVarianceAssembler batchVarianceAssembler(BatchLookup batches, BatchOutcomeLookup outcomes,
            OrderPlanLookup plans, MaterialActualSource actuals, PackagingOutcomeLookup packaging,
            IngredientPurchaseLookup ingredients) {
        return new BatchVarianceAssembler(batches, outcomes, plans, actuals, packaging, ingredients);
    }

    @Bean
    VarianceQueries varianceQueries(BatchVarianceAssembler assembler) {
        return assembler::assemble;
    }

    @Bean
    CostCommands.Close closeBatchCostUseCase(BatchCostRepository costs, BatchCostAssembler assembler,
            AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new CostHandlers.Close(costs, assembler, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return (actorId, breweryId, batchId, note) -> Objects.requireNonNull(
                transaction.execute(status -> handler.handle(actorId, breweryId, batchId, note)));
    }
}
