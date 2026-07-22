package br.com.brew.brassia.equipment.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.equipment.application.port.inbound.GetEquipmentRevisionUseCase;
import br.com.brew.brassia.equipment.application.port.inbound.ListEquipmentUseCase;
import br.com.brew.brassia.equipment.application.port.inbound.RegisterEquipmentUseCase;
import br.com.brew.brassia.equipment.application.port.inbound.UpdateEquipmentUseCase;
import br.com.brew.brassia.equipment.application.port.outbound.EquipmentRepository;
import br.com.brew.brassia.equipment.application.service.GetEquipmentRevisionHandler;
import br.com.brew.brassia.equipment.application.service.ListEquipmentHandler;
import br.com.brew.brassia.equipment.application.service.RegisterEquipmentHandler;
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

    @Bean
    GetEquipmentRevisionUseCase getEquipmentRevisionUseCase(EquipmentRepository repository) {
        return new GetEquipmentRevisionHandler(repository);
    }
}
