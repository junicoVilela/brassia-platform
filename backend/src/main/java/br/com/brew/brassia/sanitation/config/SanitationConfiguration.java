package br.com.brew.brassia.sanitation.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.sanitation.application.port.inbound.CreateProcedureUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.GetProcedureUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.ListProceduresUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.PublishProcedureUseCase;
import br.com.brew.brassia.sanitation.application.port.inbound.UpdateProcedureUseCase;
import br.com.brew.brassia.sanitation.application.port.outbound.ProcedureRepository;
import br.com.brew.brassia.sanitation.application.service.CreateProcedureHandler;
import br.com.brew.brassia.sanitation.application.service.GetProcedureHandler;
import br.com.brew.brassia.sanitation.application.service.ListProceduresHandler;
import br.com.brew.brassia.sanitation.application.service.PublishProcedureHandler;
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
}
