package br.com.brew.brassia.foodsafety.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.catalog.IngredientPurchaseLookup;
import br.com.brew.brassia.equipment.EquipmentProfileLookup;
import br.com.brew.brassia.foodsafety.application.port.inbound.AllergenCommands;
import br.com.brew.brassia.foodsafety.application.port.inbound.AllergenQueries;
import br.com.brew.brassia.foodsafety.application.port.outbound.AllergenRepository;
import br.com.brew.brassia.foodsafety.application.service.AllergenHandlers;
import br.com.brew.brassia.foodsafety.application.service.AllergenQueryHandler;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.recipe.RecipeLookup;
import br.com.brew.brassia.sanitation.CleaningReleaseLookup;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Declarar alergênico é uma escrita em duas tabelas — a declaração e o conjunto —, e a segunda
 * regrava o conjunto inteiro. Sem um commit só, uma falha no meio deixaria o ingrediente marcado
 * como declarado e sem alergênico nenhum: exatamente a afirmação de isenção que a história existe
 * para não deixar acontecer por acidente.
 */
@Configuration(proxyBeanMethods = false)
class FoodSafetyConfiguration {

    @Bean
    AllergenQueries allergenQueries(AllergenRepository allergens, BatchLookup batches, RecipeLookup recipes,
            IngredientPurchaseLookup ingredients, CleaningReleaseLookup cleanings) {
        return new AllergenQueryHandler(allergens, batches, recipes, ingredients, cleanings);
    }

    @Bean
    AllergenCommands.RegisterAllergen registerAllergenUseCase(AllergenRepository allergens, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new AllergenHandlers.RegisterAllergen(allergens, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return (actorId, breweryId, code, name) -> Objects.requireNonNull(
                transaction.execute(status -> handler.handle(actorId, breweryId, code, name)));
    }

    @Bean
    AllergenCommands.DeclareIngredient declareIngredientAllergensUseCase(AllergenRepository allergens,
            IngredientPurchaseLookup ingredients, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new AllergenHandlers.DeclareIngredient(allergens, ingredients, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return (actorId, breweryId, ingredientId, codes) -> transaction.executeWithoutResult(
                status -> handler.handle(actorId, breweryId, ingredientId, codes));
    }

    @Bean
    AllergenCommands.DeclareDedication declareEquipmentDedicationUseCase(AllergenRepository allergens,
            EquipmentProfileLookup equipment, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new AllergenHandlers.DeclareDedication(allergens, equipment, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return (actorId, breweryId, equipmentId, codes) -> transaction.executeWithoutResult(
                status -> handler.handle(actorId, breweryId, equipmentId, codes));
    }

    @Bean
    AllergenCommands.DeclareProcedureEffectiveness declareProcedureEffectivenessUseCase(
            AllergenRepository allergens, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new AllergenHandlers.DeclareProcedureEffectiveness(allergens, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return (actorId, breweryId, procedureCode, codes) -> transaction.executeWithoutResult(
                status -> handler.handle(actorId, breweryId, procedureCode, codes));
    }
}
