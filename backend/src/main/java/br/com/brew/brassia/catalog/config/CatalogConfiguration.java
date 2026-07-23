package br.com.brew.brassia.catalog.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.catalog.IngredientSpecLookup;
import br.com.brew.brassia.catalog.application.port.inbound.ListIngredientsUseCase;
import br.com.brew.brassia.catalog.application.port.inbound.RegisterIngredientUseCase;
import br.com.brew.brassia.catalog.application.port.inbound.UpdateIngredientUseCase;
import br.com.brew.brassia.catalog.application.port.outbound.IngredientRepository;
import br.com.brew.brassia.catalog.application.service.ListIngredientsHandler;
import br.com.brew.brassia.catalog.application.service.RegisterIngredientHandler;
import br.com.brew.brassia.catalog.application.service.UpdateIngredientHandler;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class CatalogConfiguration {

    @Bean
    RegisterIngredientUseCase registerIngredientUseCase(
            IngredientRepository repository, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new RegisterIngredientHandler(repository, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    UpdateIngredientUseCase updateIngredientUseCase(
            IngredientRepository repository, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new UpdateIngredientHandler(repository, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    ListIngredientsUseCase listIngredientsUseCase(IngredientRepository repository) {
        return new ListIngredientsHandler(repository);
    }

    /** Especificação publicada do ingrediente, consumida por outros módulos (ex.: metas de receita). */
    @Bean
    IngredientSpecLookup ingredientSpecLookup(IngredientRepository repository) {
        return (breweryId, ingredientId) -> repository.findById(breweryId, ingredientId).map(ingredient -> {
            var attributes = ingredient.attributes();
            return new IngredientSpecLookup.Spec(
                    ingredient.type().name(),
                    number(attributes.get("potentialSg")),
                    number(attributes.get("colorEbc")),
                    number(attributes.get("alphaAcid")),
                    number(attributes.get("attenuation")));
        });
    }

    private static java.math.BigDecimal number(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new java.math.BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

