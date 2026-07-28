package br.com.brew.brassia.sanitation.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.equipment.EquipmentProfileLookup;
import br.com.brew.brassia.sanitation.application.port.inbound.CompleteCycleUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.CreateProcedureUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.CreateRuleUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.GetCycleUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.GetProcedureUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.InterruptCycleUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.ListCyclesUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.ListProceduresUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.ListRulesUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.PublishProcedureUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.RecommendUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.RecordStepUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.ResumeCycleUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.StartCycleUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.UpdateProcedureUseCase;
import br.com.brew.brassia.sanitation.application.port.outbound.CleaningCycleRepository;
import br.com.brew.brassia.sanitation.application.port.outbound.CompatibilityRuleRepository;
import br.com.brew.brassia.sanitation.application.port.outbound.ProcedureRepository;
import br.com.brew.brassia.sanitation.application.service.CompleteCycleHandler;
import br.com.brew.brassia.sanitation.application.service.CreateProcedureHandler;
import br.com.brew.brassia.sanitation.application.service.CreateRuleHandler;
import br.com.brew.brassia.sanitation.application.service.GetCycleHandler;
import br.com.brew.brassia.sanitation.application.service.GetProcedureHandler;
import br.com.brew.brassia.sanitation.application.service.InterruptCycleHandler;
import br.com.brew.brassia.sanitation.application.service.ListCyclesHandler;
import br.com.brew.brassia.sanitation.application.service.ListProceduresHandler;
import br.com.brew.brassia.sanitation.application.service.ListRulesHandler;
import br.com.brew.brassia.sanitation.application.service.PublishProcedureHandler;
import br.com.brew.brassia.sanitation.application.service.RecommendHandler;
import br.com.brew.brassia.sanitation.application.service.RecordStepHandler;
import br.com.brew.brassia.sanitation.application.service.ResumeCycleHandler;
import br.com.brew.brassia.sanitation.application.service.StartCycleHandler;
import br.com.brew.brassia.sanitation.application.service.UpdateProcedureHandler;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class SanitationConfiguration {

    @Bean
    CreateProcedureUseCase createProcedureUseCase(
            ProcedureRepository repository, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new CreateProcedureHandler(repository, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    UpdateProcedureUseCase updateProcedureUseCase(
            ProcedureRepository repository, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new UpdateProcedureHandler(repository, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    PublishProcedureUseCase publishProcedureUseCase(
            ProcedureRepository repository, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new PublishProcedureHandler(repository, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    ListProceduresUseCase listProceduresUseCase(ProcedureRepository repository) {
        return new ListProceduresHandler(repository);
    }

    @Bean
    GetProcedureUseCase getProcedureUseCase(ProcedureRepository repository) {
        return new GetProcedureHandler(repository);
    }

    @Bean
    CreateRuleUseCase createRuleUseCase(CompatibilityRuleRepository rules, ProcedureRepository procedures,
            AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new CreateRuleHandler(rules, procedures, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    ListRulesUseCase listRulesUseCase(CompatibilityRuleRepository rules) {
        return new ListRulesHandler(rules);
    }

    @Bean
    RecommendUseCase recommendUseCase(CompatibilityRuleRepository rules) {
        return new RecommendHandler(rules);
    }

    @Bean
    StartCycleUseCase startCycleUseCase(CleaningCycleRepository cycles, ProcedureRepository procedures,
            EquipmentProfileLookup equipment, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new StartCycleHandler(cycles, procedures, equipment, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    RecordStepUseCase recordStepUseCase(
            CleaningCycleRepository cycles, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new RecordStepHandler(cycles, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    InterruptCycleUseCase interruptCycleUseCase(
            CleaningCycleRepository cycles, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new InterruptCycleHandler(cycles, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    ResumeCycleUseCase resumeCycleUseCase(
            CleaningCycleRepository cycles, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new ResumeCycleHandler(cycles, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    CompleteCycleUseCase completeCycleUseCase(
            CleaningCycleRepository cycles, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new CompleteCycleHandler(cycles, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    GetCycleUseCase getCycleUseCase(CleaningCycleRepository cycles) {
        return new GetCycleHandler(cycles);
    }

    @Bean
    ListCyclesUseCase listCyclesUseCase(CleaningCycleRepository cycles) {
        return new ListCyclesHandler(cycles);
    }
}
