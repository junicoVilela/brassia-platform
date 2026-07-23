package br.com.brew.brassia.recipe.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.catalog.IngredientSpecLookup;
import br.com.brew.brassia.equipment.EquipmentCapacityLookup;
import br.com.brew.brassia.equipment.EquipmentProfileLookup;
import br.com.brew.brassia.recipe.application.port.inbound.CalculateRecipeMetricsUseCase;
import br.com.brew.brassia.recipe.application.port.inbound.CalculateRecipeVolumesUseCase;
import br.com.brew.brassia.recipe.application.port.inbound.CreateRecipeUseCase;
import br.com.brew.brassia.recipe.application.port.inbound.RecipeMetricsUseCase;
import br.com.brew.brassia.recipe.application.port.inbound.RecipeUseCase;
import br.com.brew.brassia.recipe.application.port.inbound.ListRecipesUseCase;
import br.com.brew.brassia.recipe.application.port.outbound.RecipeMetricsRepository;
import br.com.brew.brassia.recipe.application.port.outbound.RecipeRepository;
import br.com.brew.brassia.recipe.application.service.CalculateRecipeMetricsHandler;
import br.com.brew.brassia.recipe.application.service.CalculateRecipeVolumesHandler;
import br.com.brew.brassia.recipe.application.service.CreateRecipeHandler;
import br.com.brew.brassia.recipe.application.service.RecipeHandler;
import br.com.brew.brassia.recipe.application.service.RecipeMetricsHandler;
import br.com.brew.brassia.recipe.application.service.ListRecipesHandler;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class RecipeConfiguration {

    @Bean
    CreateRecipeUseCase createRecipeUseCase(
            RecipeRepository repository,
            EquipmentCapacityLookup equipment,
            AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new CreateRecipeHandler(repository, equipment, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    ListRecipesUseCase listRecipesUseCase(RecipeRepository repository) {
        return new ListRecipesHandler(repository);
    }

    @Bean
    RecipeUseCase getRecipeUseCase(RecipeRepository repository) {
        return new RecipeHandler(repository);
    }

    @Bean
    CalculateRecipeVolumesUseCase calculateRecipeVolumesUseCase(
            RecipeRepository repository, EquipmentProfileLookup equipment) {
        return new CalculateRecipeVolumesHandler(repository, equipment);
    }

    @Bean
    CalculateRecipeMetricsUseCase calculateRecipeMetricsUseCase(
            RecipeRepository repository, EquipmentProfileLookup equipment, IngredientSpecLookup ingredients,
            RecipeMetricsRepository metrics, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new CalculateRecipeMetricsHandler(repository, equipment, ingredients, metrics, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    RecipeMetricsUseCase getRecipeMetricsUseCase(RecipeMetricsRepository repository) {
        return new RecipeMetricsHandler(repository);
    }
}
