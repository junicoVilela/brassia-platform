package br.com.brew.brassia.gas.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.equipment.EquipmentProfileLookup;
import br.com.brew.brassia.gas.application.port.inbound.ComponentCommands;
import br.com.brew.brassia.gas.application.port.inbound.ConnectionCommands;
import br.com.brew.brassia.gas.application.port.inbound.CylinderCommands;
import br.com.brew.brassia.gas.application.port.inbound.GasQueries;
import br.com.brew.brassia.gas.application.port.outbound.GasConnectionRepository;
import br.com.brew.brassia.gas.application.port.outbound.GasCylinderRepository;
import br.com.brew.brassia.gas.application.port.outbound.GasNetworkComponentRepository;
import br.com.brew.brassia.gas.application.service.ComponentHandlers;
import br.com.brew.brassia.gas.application.service.ConnectionHandlers;
import br.com.brew.brassia.gas.application.service.CylinderHandlers;
import br.com.brew.brassia.gas.application.service.GasQueriesHandler;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cada comando de gás roda num commit só: conectar, testar, medir e consumir tocam cilindro e
 * conexão juntos, então uma recusa não deixa cilindro ocupado sem linha nem linha servindo sem
 * cilindro.
 */
@Configuration(proxyBeanMethods = false)
class GasConfiguration {

    @Bean
    CylinderCommands.Register registerCylinderUseCase(GasCylinderRepository cylinders, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new CylinderHandlers.Register(cylinders, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    CylinderCommands.SetBlock setCylinderBlockUseCase(GasCylinderRepository cylinders, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new CylinderHandlers.SetBlock(cylinders, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    CylinderCommands.Requalify requalifyCylinderUseCase(GasCylinderRepository cylinders, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new CylinderHandlers.Requalify(cylinders, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    CylinderCommands.Refill refillCylinderUseCase(GasCylinderRepository cylinders, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new CylinderHandlers.Refill(cylinders, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    ComponentCommands.Register registerComponentUseCase(GasNetworkComponentRepository components, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new ComponentHandlers.Register(components, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    ComponentCommands.Update updateComponentUseCase(GasNetworkComponentRepository components, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new ComponentHandlers.Update(components, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    ComponentCommands.SetActive setComponentActiveUseCase(GasNetworkComponentRepository components,
            AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new ComponentHandlers.SetActive(components, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    ConnectionCommands.Connect connectGasUseCase(GasConnectionRepository connections, GasCylinderRepository cylinders,
            GasNetworkComponentRepository components, EquipmentProfileLookup equipment, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new ConnectionHandlers.Connect(connections, cylinders, components, equipment, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    ConnectionCommands.RecordLeakTest recordLeakTestUseCase(GasConnectionRepository connections, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new ConnectionHandlers.RecordLeakTest(connections, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    ConnectionCommands.RecordPressure recordGasPressureUseCase(GasConnectionRepository connections, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new ConnectionHandlers.RecordPressure(connections, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    ConnectionCommands.RecordConsumption recordGasConsumptionUseCase(GasConnectionRepository connections,
            GasCylinderRepository cylinders, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new ConnectionHandlers.RecordConsumption(connections, cylinders, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    ConnectionCommands.Disconnect disconnectGasUseCase(GasConnectionRepository connections,
            GasCylinderRepository cylinders, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new ConnectionHandlers.Disconnect(connections, cylinders, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    GasQueries gasQueries(GasCylinderRepository cylinders, GasNetworkComponentRepository components,
            GasConnectionRepository connections) {
        return new GasQueriesHandler(cylinders, components, connections);
    }
}
