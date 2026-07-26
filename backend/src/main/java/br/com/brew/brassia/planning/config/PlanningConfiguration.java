package br.com.brew.brassia.planning.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.equipment.EquipmentCapacityLookup;
import br.com.brew.brassia.planning.application.port.inbound.CreateScheduleEntryUseCase;
import br.com.brew.brassia.planning.application.port.inbound.ListScheduleEntriesUseCase;
import br.com.brew.brassia.planning.application.port.inbound.SimulateScheduleUseCase;
import br.com.brew.brassia.planning.application.port.outbound.ScheduleEntryRepository;
import br.com.brew.brassia.planning.application.service.CreateScheduleEntryHandler;
import br.com.brew.brassia.planning.application.service.ListScheduleEntriesHandler;
import br.com.brew.brassia.planning.application.service.SimulateScheduleHandler;
import br.com.brew.brassia.planning.domain.ScheduleConflictException;
import br.com.brew.brassia.recipe.RecipeLookup;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class PlanningConfiguration {

    @Bean
    CreateScheduleEntryUseCase createScheduleEntryUseCase(
            ScheduleEntryRepository repository,
            RecipeLookup recipes,
            EquipmentCapacityLookup equipment,
            AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new CreateScheduleEntryHandler(repository, recipes, equipment, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> {
            try {
                return Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
            } catch (DataIntegrityViolationException ex) {
                // Backstop de concorrência: duas transações passaram no pré-check e colidiram na
                // exclusion constraint. Traduz para o mesmo conflito 409 (transação já revertida).
                if (isEquipmentOverlap(ex)) {
                    throw new ScheduleConflictException("conflito de equipamento na janela selecionada", ex);
                }
                throw ex;
            }
        };
    }

    private static boolean isEquipmentOverlap(DataIntegrityViolationException ex) {
        var cause = ex.getMostSpecificCause().getMessage();
        return cause != null && cause.contains("ex_planning_schedule_no_overlap");
    }

    @Bean
    SimulateScheduleUseCase simulateScheduleUseCase(
            ScheduleEntryRepository repository, EquipmentCapacityLookup equipment) {
        return new SimulateScheduleHandler(repository, equipment);
    }

    @Bean
    ListScheduleEntriesUseCase listScheduleEntriesUseCase(ScheduleEntryRepository repository) {
        return new ListScheduleEntriesHandler(repository);
    }
}
