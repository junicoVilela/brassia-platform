package br.com.brew.brassia.recipe.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.recipe.application.port.inbound.CreateRecipeUseCase;
import br.com.brew.brassia.recipe.application.port.outbound.RecipeRepository;
import br.com.brew.brassia.recipe.application.service.CreateRecipeHandler;
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
            AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new CreateRecipeHandler(repository, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(
                transaction.execute(status -> handler.handle(command)));
    }
}
