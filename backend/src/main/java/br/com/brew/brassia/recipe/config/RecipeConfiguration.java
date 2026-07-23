package br.com.brew.brassia.recipe.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.equipment.EquipmentCapacityLookup;
import br.com.brew.brassia.recipe.application.port.inbound.CreateRecipeUseCase;
import br.com.brew.brassia.recipe.application.port.inbound.GetRecipeUseCase;
import br.com.brew.brassia.recipe.application.port.inbound.ListRecipesUseCase;
import br.com.brew.brassia.recipe.application.port.outbound.RecipeRepository;
import br.com.brew.brassia.recipe.application.service.CreateRecipeHandler;
import br.com.brew.brassia.recipe.application.service.GetRecipeHandler;
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
    GetRecipeUseCase getRecipeUseCase(RecipeRepository repository) {
        return new GetRecipeHandler(repository);
    }
}
