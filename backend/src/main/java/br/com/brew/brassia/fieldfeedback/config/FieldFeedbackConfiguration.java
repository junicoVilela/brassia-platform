package br.com.brew.brassia.fieldfeedback.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.fieldfeedback.application.port.inbound.ComplaintCommands;
import br.com.brew.brassia.fieldfeedback.application.port.inbound.ComplaintQueries;
import br.com.brew.brassia.fieldfeedback.application.port.outbound.ComplaintRepository;
import br.com.brew.brassia.fieldfeedback.application.service.ComplaintHandler;
import br.com.brew.brassia.fieldfeedback.application.service.ComplaintQueryService;
import br.com.brew.brassia.fieldfeedback.domain.FieldComplaint;
import br.com.brew.brassia.fieldfeedback.domain.RequiredAction;
import br.com.brew.brassia.production.BatchLookup;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Composição do feedback de campo (FLD-001).
 *
 * <p>O registro é transacional porque escreve reclamação e contato em tabelas diferentes. Se a reclamação
 * gravasse e o contato falhasse, ficaria uma reclamação que <em>parece</em> anônima — e ninguém saberia
 * que havia alguém esperando retorno.
 */
@Configuration(proxyBeanMethods = false)
class FieldFeedbackConfiguration {

    @Bean
    ComplaintCommands complaintCommands(ComplaintRepository complaints, BatchLookup batches,
            AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new ComplaintHandler(complaints, batches, audit, Clock.systemUTC());
        return new TransactionalComplaintCommands(handler, new TransactionTemplate(transactionManager));
    }

    @Bean
    ComplaintQueries complaintQueries(ComplaintRepository complaints, AuditTrail audit) {
        return new ComplaintQueryService(complaints, audit);
    }

    private record TransactionalComplaintCommands(ComplaintHandler handler,
            TransactionTemplate transaction) implements ComplaintCommands {

        @Override
        public FieldComplaint register(RegisterCommand command) {
            return required(status -> handler.register(command));
        }

        @Override
        public FieldComplaint startAnalysis(UUID breweryId, UUID complaintId, UUID actor) {
            return required(status -> handler.startAnalysis(breweryId, complaintId, actor));
        }

        @Override
        public FieldComplaint fulfill(UUID breweryId, UUID complaintId, RequiredAction action,
                UUID referenceId, UUID actor) {
            return required(status -> handler.fulfill(breweryId, complaintId, action, referenceId, actor));
        }

        @Override
        public FieldComplaint waive(UUID breweryId, UUID complaintId, RequiredAction action,
                String justification, UUID actor) {
            return required(status -> handler.waive(breweryId, complaintId, action, justification, actor));
        }

        @Override
        public FieldComplaint close(UUID breweryId, UUID complaintId, String note, UUID actor) {
            return required(status -> handler.close(breweryId, complaintId, note, actor));
        }

        private FieldComplaint required(TransactionCallback<FieldComplaint> work) {
            return Objects.requireNonNull(transaction.execute(work));
        }
    }
}
