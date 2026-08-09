package br.com.brew.brassia.sensor.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.sensor.application.port.inbound.DeviceCommands;
import br.com.brew.brassia.sensor.application.port.inbound.DeviceStatusCommands;
import br.com.brew.brassia.sensor.application.port.inbound.ReadingCommands;
import br.com.brew.brassia.sensor.application.port.inbound.SensorQueries;
import br.com.brew.brassia.sensor.application.port.outbound.DeviceRepository;
import br.com.brew.brassia.sensor.application.port.outbound.ReadingRepository;
import br.com.brew.brassia.sensor.application.service.DeviceHandlers;
import br.com.brew.brassia.sensor.application.service.IngestionHandler;
import java.time.Clock;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Composição do módulo de sensores (INT-001).
 *
 * <p><strong>A ingestão é transacional apesar de gravar uma linha só.</strong> Parece supérfluo e não é: o
 * caminho da duplicata faz duas operações — o {@code INSERT} que não insere e a busca da leitura original
 * — e sem transação elas veriam estados diferentes do banco. A leitura original poderia estar num commit
 * ainda não visível, e a resposta seria "duplicada, mas não sei qual".
 */
@Configuration(proxyBeanMethods = false)
class SensorConfiguration {

    @Bean
    DeviceCommands sensorDeviceCommands(DeviceRepository devices, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new DeviceHandlers.Register(devices, audit, Clock.systemUTC());
        var transaction = new TransactionTemplate(transactionManager);
        return request -> Objects.requireNonNull(transaction.execute(status -> handler.register(request)));
    }

    @Bean
    DeviceStatusCommands sensorDeviceStatusCommands(DeviceRepository devices, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new DeviceHandlers.ChangeStatus(devices, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return request -> Objects.requireNonNull(
                transaction.execute(status -> handler.changeStatus(request)));
    }

    @Bean
    ReadingCommands sensorReadingCommands(DeviceRepository devices, ReadingRepository readings,
            PlatformTransactionManager transactionManager) {
        var handler = new IngestionHandler(devices, readings, Clock.systemUTC());
        var transaction = new TransactionTemplate(transactionManager);
        return request -> Objects.requireNonNull(transaction.execute(status -> handler.ingest(request)));
    }

    @Bean
    SensorQueries sensorQueries(DeviceRepository devices, ReadingRepository readings) {
        return new DeviceHandlers.Queries(devices, readings);
    }
}
