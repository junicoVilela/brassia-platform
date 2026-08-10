package br.com.brew.brassia.sensory.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.sensory.application.port.inbound.SensoryQueries;
import br.com.brew.brassia.sensory.application.port.inbound.SessionCommands;
import br.com.brew.brassia.sensory.application.port.outbound.SensoryPolicyRepository;
import br.com.brew.brassia.sensory.application.port.outbound.SensorySessionRepository;
import br.com.brew.brassia.sensory.application.service.SensoryQueriesHandler;
import br.com.brew.brassia.sensory.application.service.SessionHandlers;
import br.com.brew.brassia.sensory.application.port.inbound.SensoryPolicyUseCase;
import br.com.brew.brassia.sensory.application.service.SensoryPolicyHandler;
import java.util.Objects;
import br.com.brew.brassia.sensory.application.port.outbound.DescriptorRepository;
import br.com.brew.brassia.sensory.application.service.DescriptorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cada comando da sessão sensorial roda num commit só. Acrescentar amostra é o caso que exige:
 * o código cego é sorteado contra os já usados na sessão, então gravar a amostra sem gravar a
 * sessão abriria espaço para dois sorteios enxergarem o mesmo conjunto e colidirem.
 */
@Configuration(proxyBeanMethods = false)
class SensoryConfiguration {

    @Bean
    SessionCommands.Create createSensorySessionUseCase(SensorySessionRepository sessions,
            SensoryPolicyRepository policies, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new SessionHandlers.Create(sessions, policies, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    SessionCommands.Amend amendSensorySessionUseCase(SensorySessionRepository sessions, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new SessionHandlers.Amend(sessions, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    SessionCommands.AddSample addSensorySampleUseCase(SensorySessionRepository sessions,
            BatchLookup batches, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new SessionHandlers.AddSample(sessions, batches, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    SessionCommands.RemoveSample removeSensorySampleUseCase(SensorySessionRepository sessions,
            AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new SessionHandlers.RemoveSample(sessions, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    SessionCommands.Open openSensorySessionUseCase(SensorySessionRepository sessions, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new SessionHandlers.Open(sessions, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    SessionCommands.Close closeSensorySessionUseCase(SensorySessionRepository sessions, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new SessionHandlers.Close(sessions, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    SessionCommands.SubmitEvaluation submitSensoryEvaluationUseCase(SensorySessionRepository sessions,
            AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new SessionHandlers.SubmitEvaluation(sessions, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    SensoryQueries sensoryQueries(SensorySessionRepository sessions) {
        return new SensoryQueriesHandler(sessions);
    }

    /** Política sensorial (PRM-001): a escala da ficha, congelada em cada sessão. */
    @Bean
    SensoryPolicyUseCase sensoryPolicyUseCase(SensoryPolicyRepository policies, AuditTrail audit) {
        return new SensoryPolicyHandler(policies, audit);
    }


    /**
     * A biblioteca de descritores (SEN-002).
     *
     * <p>Um bean só implementando comando e consulta: a leitura não tem regra própria — é a mesma
     * biblioteca vista de dois ângulos —, e separar em dois criaria dois lugares para manter em sincronia
     * sem nenhum ganho.
     */
    @Bean
    DescriptorHandler descriptorHandler(DescriptorRepository descriptors, AuditTrail audit) {
        return new DescriptorHandler(descriptors, audit);
    }
}