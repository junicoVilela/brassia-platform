package br.com.brew.brassia.metrology.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.metrology.InstrumentStatusLookup;
import br.com.brew.brassia.metrology.application.port.inbound.InstrumentCommands;
import br.com.brew.brassia.metrology.application.port.inbound.MetrologyQueries;
import br.com.brew.brassia.metrology.application.port.inbound.StandardCommands;
import br.com.brew.brassia.metrology.application.port.outbound.CalibrationStandardRepository;
import br.com.brew.brassia.metrology.application.port.outbound.InstrumentRepository;
import br.com.brew.brassia.metrology.application.service.InstrumentHandlers;
import br.com.brew.brassia.metrology.application.service.MetrologyQueriesHandler;
import br.com.brew.brassia.metrology.application.service.StandardHandlers;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cada comando de metrologia roda num commit só. Calibrar é o caso que exige: insere o
 * certificado e reaponta a última calibração do instrumento — se um dos dois falhar sozinho, ou o
 * histórico ganha um certificado órfão, ou o instrumento passa a apontar para nada.
 *
 * <p>{@code @Transactional} em método {@code @Bean} não tem efeito (o proxy não envolve a lambda),
 * então a transação é explícita via {@link TransactionTemplate}.
 */
@Configuration(proxyBeanMethods = false)
class MetrologyConfiguration {

    @Bean
    InstrumentCommands.Register registerInstrumentUseCase(InstrumentRepository instruments, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new InstrumentHandlers.Register(instruments, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    InstrumentCommands.Amend amendInstrumentUseCase(InstrumentRepository instruments, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new InstrumentHandlers.Amend(instruments, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    InstrumentCommands.SetBlock setInstrumentBlockUseCase(InstrumentRepository instruments, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new InstrumentHandlers.SetBlock(instruments, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    InstrumentCommands.Retire retireInstrumentUseCase(InstrumentRepository instruments, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new InstrumentHandlers.Retire(instruments, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    InstrumentCommands.DesignateCriticalUse designateCriticalUseUseCase(InstrumentRepository instruments,
            AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new InstrumentHandlers.DesignateCriticalUse(instruments, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    InstrumentCommands.Calibrate calibrateInstrumentUseCase(InstrumentRepository instruments,
            CalibrationStandardRepository standards, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new InstrumentHandlers.Calibrate(instruments, standards, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    StandardCommands.Register registerStandardUseCase(CalibrationStandardRepository standards, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new StandardHandlers.Register(standards, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    StandardCommands.Renew renewStandardUseCase(CalibrationStandardRepository standards, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new StandardHandlers.Renew(standards, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    /**
     * Um bean só serve as duas portas: {@link MetrologyQueries} para a camada web do próprio
     * módulo e {@link InstrumentStatusLookup} — publicada — para quem for consultar a aptidão de
     * fora, como QLT-001 fará. Declará-las em beans separados criaria dois candidatos para o mesmo
     * tipo e quebraria a injeção.
     */
    @Bean
    MetrologyQueriesHandler metrologyQueriesHandler(InstrumentRepository instruments,
            CalibrationStandardRepository standards) {
        return new MetrologyQueriesHandler(instruments, standards);
    }
}
