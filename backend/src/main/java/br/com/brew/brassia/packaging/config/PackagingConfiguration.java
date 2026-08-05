package br.com.brew.brassia.packaging.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.calculator.CalculatorEngine;
import br.com.brew.brassia.catalog.IngredientSpecLookup;
import br.com.brew.brassia.equipment.EquipmentAvailabilityLookup;
import br.com.brew.brassia.foodsafety.AllergenProfileLookup;
import br.com.brew.brassia.foodsafety.ChangeoverCheck;
import br.com.brew.brassia.packaging.PackagingStockGateway;
import br.com.brew.brassia.packaging.application.port.inbound.CancelPackagingPlanUseCase;
import br.com.brew.brassia.packaging.application.port.inbound.CarbonationCommands;
import br.com.brew.brassia.packaging.application.port.inbound.ConfirmChecklistItemUseCase;
import br.com.brew.brassia.packaging.application.port.inbound.ExecutePackagingUseCase;
import br.com.brew.brassia.packaging.application.port.inbound.FreshnessCommands;
import br.com.brew.brassia.packaging.application.port.inbound.GetPackagingRunUseCase;
import br.com.brew.brassia.packaging.application.port.inbound.LabelCommands;
import br.com.brew.brassia.packaging.application.port.inbound.PackagingPlanQueries;
import br.com.brew.brassia.packaging.application.port.inbound.PlanPackagingUseCase;
import br.com.brew.brassia.packaging.application.port.inbound.ReservePackagingPlanUseCase;
import br.com.brew.brassia.packaging.application.port.outbound.CarbonationRepository;
import br.com.brew.brassia.packaging.application.port.outbound.FreshnessRepository;
import br.com.brew.brassia.packaging.application.port.outbound.LabelRepository;
import br.com.brew.brassia.packaging.application.port.outbound.PackagingPlanRepository;
import br.com.brew.brassia.packaging.application.port.inbound.FinishedLotQueries;
import br.com.brew.brassia.packaging.application.port.outbound.FinishedLotRepository;
import br.com.brew.brassia.packaging.application.port.outbound.PackagingRunRepository;
import br.com.brew.brassia.packaging.application.service.CarbonationHandlers;
import br.com.brew.brassia.packaging.application.service.ExecutePackagingHandler;
import br.com.brew.brassia.packaging.application.service.FinishedLotQueriesHandler;
import br.com.brew.brassia.packaging.application.service.FreshnessHandlers;
import br.com.brew.brassia.packaging.application.service.LabelHandlers;
import br.com.brew.brassia.packaging.application.service.PackagingPlanHandlers;
import br.com.brew.brassia.packaging.application.service.PlanPackagingHandler;
import br.com.brew.brassia.packaging.application.service.ReservePackagingPlanHandler;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.recipe.RecipeLookup;
import br.com.brew.brassia.sanitation.CleaningPolicyLookup;
import br.com.brew.brassia.sanitation.CleaningReleaseLookup;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class PackagingConfiguration {

    @Bean
    PlanPackagingUseCase planPackagingUseCase(PackagingPlanRepository plans, BatchLookup batches,
            IngredientSpecLookup ingredients, EquipmentAvailabilityLookup lines, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new PlanPackagingHandler(plans, batches, ingredients, lines, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    ConfirmChecklistItemUseCase confirmChecklistItemUseCase(PackagingPlanRepository plans, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new PackagingPlanHandlers.ConfirmItem(plans, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    /**
     * A reserva verifica linha, agenda, limpeza e estoque no mesmo commit: qualquer recusa
     * reverte tudo, então não sobra reserva sem plano reservado nem o contrário.
     */
    @Bean
    ReservePackagingPlanUseCase reservePackagingPlanUseCase(PackagingPlanRepository plans,
            EquipmentAvailabilityLookup lines, CleaningReleaseLookup cleanings,
            CleaningPolicyLookup cleaningPolicy, ChangeoverCheck changeover,
            IngredientSpecLookup ingredients, PackagingStockGateway stock, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new ReservePackagingPlanHandler(plans, lines, cleanings, cleaningPolicy, changeover,
                ingredients, stock, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    CancelPackagingPlanUseCase cancelPackagingPlanUseCase(PackagingPlanRepository plans,
            PackagingStockGateway stock, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new PackagingPlanHandlers.Cancel(plans, stock, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    /**
     * Balanço, consumo de embalagem e transição do plano no mesmo commit: não sobra embalagem
     * consumida sem envase registrado nem envase registrado sem embalagem baixada.
     */
    @Bean
    ExecutePackagingUseCase executePackagingUseCase(PackagingPlanRepository plans, PackagingRunRepository runs,
            FinishedLotRepository finishedLots, BatchLookup batches, IngredientSpecLookup ingredients,
            PackagingStockGateway stock, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new ExecutePackagingHandler(plans, runs, finishedLots, batches, ingredients, stock, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    /** Consulta pura: o lote de produto acabado nasce do envase, então não há transação a abrir. */
    @Bean
    FinishedLotQueries finishedLotQueries(FinishedLotRepository finishedLots) {
        return new FinishedLotQueriesHandler(finishedLots);
    }

    @Bean
    GetPackagingRunUseCase getPackagingRunUseCase(PackagingRunRepository runs) {
        return runs::findByPlan;
    }

    @Bean
    PackagingPlanQueries packagingPlanQueries(PackagingPlanRepository plans) {
        return new PackagingPlanHandlers.Queries(plans);
    }

    /** Prévia de carbonatação: calcula e explica, sem gravar nada. */
    @Bean
    CarbonationCommands.Preview previewCarbonationUseCase(PackagingPlanRepository plans,
            CalculatorEngine calculator) {
        return new CarbonationHandlers.Preview(plans, calculator);
    }

    @Bean
    CarbonationCommands.Record recordCarbonationUseCase(PackagingPlanRepository plans,
            CarbonationRepository carbonations, CalculatorEngine calculator, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new CarbonationHandlers.Record(plans, carbonations, calculator, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    CarbonationCommands.Get getCarbonationUseCase(CarbonationRepository carbonations) {
        return new CarbonationHandlers.Get(carbonations);
    }

    @Bean
    FreshnessCommands.Record recordFreshnessUseCase(FreshnessRepository freshness, PackagingRunRepository runs,
            AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new FreshnessHandlers.Record(freshness, runs, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    FreshnessCommands.OverrideShelfLife overrideShelfLifeUseCase(FreshnessRepository freshness, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new FreshnessHandlers.OverrideShelfLife(freshness, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    FreshnessCommands.Get getFreshnessUseCase(FreshnessRepository freshness) {
        return new FreshnessHandlers.Get(freshness);
    }

    @Bean
    FreshnessCommands.Policy shelfLifePolicyUseCase(FreshnessRepository freshness, AuditTrail audit) {
        return new FreshnessHandlers.Policy(freshness, audit);
    }

    @Bean
    LabelCommands.SaveTemplate saveLabelTemplateUseCase(LabelRepository labels, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new LabelHandlers.SaveTemplate(labels, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    LabelCommands.SaveRule saveLabelRuleUseCase(LabelRepository labels, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new LabelHandlers.SaveRule(labels, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return (actorId, breweryId, rule) ->
                transaction.executeWithoutResult(status -> handler.handle(actorId, breweryId, rule));
    }

    /** Prévia é consulta: monta os campos das fontes e não grava nada. */
    @Bean
    LabelCommands.Preview previewLabelUseCase(LabelRepository labels, PackagingPlanRepository plans,
            FreshnessRepository freshness, BatchLookup batches, RecipeLookup recipes,
            AllergenProfileLookup allergenProfiles) {
        return new LabelHandlers.Preview(labels, plans, freshness, batches, recipes, allergenProfiles);
    }

    @Bean
    LabelCommands.Print printLabelUseCase(LabelRepository labels, PackagingPlanRepository plans,
            FreshnessRepository freshness, BatchLookup batches, RecipeLookup recipes,
            AllergenProfileLookup allergenProfiles, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new LabelHandlers.Print(labels, plans, freshness, batches, recipes, allergenProfiles,
                audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    LabelCommands.Queries labelQueries(LabelRepository labels) {
        return new LabelHandlers.Queries(labels);
    }
}
