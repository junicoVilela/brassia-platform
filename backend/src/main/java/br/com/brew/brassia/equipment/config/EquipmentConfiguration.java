package br.com.brew.brassia.equipment.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.equipment.EquipmentCapacityLookup;
import br.com.brew.brassia.equipment.application.port.inbound.CancelMaintenanceUseCase;
import br.com.brew.brassia.equipment.application.port.inbound.CheckEquipmentAvailabilityUseCase;
import br.com.brew.brassia.equipment.application.port.inbound.GetEquipmentRevisionUseCase;
import br.com.brew.brassia.equipment.application.port.inbound.ListEquipmentMaintenanceUseCase;
import br.com.brew.brassia.equipment.application.port.inbound.ListEquipmentUseCase;
import br.com.brew.brassia.equipment.application.port.inbound.RegisterEquipmentUseCase;
import br.com.brew.brassia.equipment.application.port.inbound.ScheduleMaintenanceUseCase;
import br.com.brew.brassia.equipment.application.port.inbound.UpdateEquipmentUseCase;
import br.com.brew.brassia.equipment.application.port.outbound.EquipmentRepository;
import br.com.brew.brassia.equipment.application.port.outbound.MaintenanceRepository;
import br.com.brew.brassia.equipment.application.service.CancelMaintenanceHandler;
import br.com.brew.brassia.equipment.application.service.CheckEquipmentAvailabilityHandler;
import br.com.brew.brassia.equipment.application.service.GetEquipmentRevisionHandler;
import br.com.brew.brassia.equipment.application.service.ListEquipmentHandler;
import br.com.brew.brassia.equipment.application.service.ListEquipmentMaintenanceHandler;
import br.com.brew.brassia.equipment.application.service.RegisterEquipmentHandler;
import br.com.brew.brassia.equipment.application.service.ScheduleMaintenanceHandler;
import br.com.brew.brassia.equipment.application.service.UpdateEquipmentHandler;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class EquipmentConfiguration {

    @Bean
    RegisterEquipmentUseCase registerEquipmentUseCase(
            EquipmentRepository repository, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new RegisterEquipmentHandler(repository, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    UpdateEquipmentUseCase updateEquipmentUseCase(
            EquipmentRepository repository, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new UpdateEquipmentHandler(repository, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    ListEquipmentUseCase listEquipmentUseCase(EquipmentRepository repository) {
        return new ListEquipmentHandler(repository);
    }

    /** Consulta publicada de capacidade, consumida por outros módulos (ex.: receitas). */
    @Bean
    EquipmentCapacityLookup equipmentCapacityLookup(EquipmentRepository repository) {
        return (breweryId, equipmentId) -> repository.findById(breweryId, equipmentId)
                .map(br.com.brew.brassia.equipment.domain.Equipment::capacityLiters);
    }

    @Bean
    GetEquipmentRevisionUseCase getEquipmentRevisionUseCase(EquipmentRepository repository) {
        return new GetEquipmentRevisionHandler(repository);
    }

    @Bean
    ScheduleMaintenanceUseCase scheduleMaintenanceUseCase(EquipmentRepository equipment,
            MaintenanceRepository maintenance, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new ScheduleMaintenanceHandler(equipment, maintenance, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    CancelMaintenanceUseCase cancelMaintenanceUseCase(MaintenanceRepository maintenance, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new CancelMaintenanceHandler(maintenance, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    ListEquipmentMaintenanceUseCase listEquipmentMaintenanceUseCase(MaintenanceRepository maintenance) {
        return new ListEquipmentMaintenanceHandler(maintenance);
    }

    @Bean
    CheckEquipmentAvailabilityUseCase checkEquipmentAvailabilityUseCase(MaintenanceRepository maintenance) {
        return new CheckEquipmentAvailabilityHandler(maintenance);
    }
}
