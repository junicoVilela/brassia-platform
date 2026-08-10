package br.com.brew.brassia.optimization.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.catalog.IngredientPurchaseLookup;
import br.com.brew.brassia.catalog.IngredientSpecLookup;
import br.com.brew.brassia.optimization.application.port.inbound.OptimizationCommands;
import br.com.brew.brassia.optimization.application.port.inbound.OptimizationQueries;
import br.com.brew.brassia.optimization.application.port.outbound.OptimizationRunRepository;
import br.com.brew.brassia.optimization.application.service.OptimizationHandler;
import br.com.brew.brassia.optimization.application.service.OptimizationQueryService;
import br.com.brew.brassia.optimization.domain.OptimizationRun;
import br.com.brew.brassia.purchasing.IngredientSourcingLookup;
import br.com.brew.brassia.purchasing.StockOnHandLookup;
import br.com.brew.brassia.recipe.RecipeLookup;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Composição da otimização (OPT-001).
 *
 * <p>Transacional nas anotações porque o {@code FOR UPDATE} só tem sentido dentro de transação: sem ela o
 * lock morre no fim da consulta, e duas aplicações simultâneas registrariam duas versões de receita
 * diferentes como "a" aplicada.
 */
@Configuration(proxyBeanMethods = false)
class OptimizationConfiguration {

    @Bean
    OptimizationCommands optimizationCommands(OptimizationRunRepository runs, RecipeLookup recipes,
            IngredientPurchaseLookup catalog, IngredientSpecLookup specs,
            IngredientSourcingLookup sourcing, StockOnHandLookup stock, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new OptimizationHandler(runs, recipes, catalog, specs, sourcing, stock, audit,
                Clock.systemUTC());
        return new TransactionalOptimizationCommands(handler,
                new TransactionTemplate(transactionManager));
    }

    @Bean
    OptimizationQueries optimizationQueries(OptimizationRunRepository runs) {
        return new OptimizationQueryService(runs);
    }

    private record TransactionalOptimizationCommands(OptimizationHandler handler,
            TransactionTemplate transaction) implements OptimizationCommands {

        @Override
        public OptimizationRun optimize(OptimizeCommand command) {
            return required(status -> handler.optimize(command));
        }

        @Override
        public OptimizationRun explain(UUID breweryId, UUID runId, String explanation, UUID actor) {
            return required(status -> handler.explain(breweryId, runId, explanation, actor));
        }

        @Override
        public OptimizationRun markApplied(UUID breweryId, UUID runId, UUID recipeVersionId,
                UUID actor) {
            return required(status -> handler.markApplied(breweryId, runId, recipeVersionId, actor));
        }

        private OptimizationRun required(TransactionCallback<OptimizationRun> work) {
            return Objects.requireNonNull(transaction.execute(work));
        }
    }
}
