package br.com.brew.brassia.experiment.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.experiment.application.port.inbound.ExperimentCommands;
import br.com.brew.brassia.experiment.application.port.inbound.ExperimentQueries;
import br.com.brew.brassia.experiment.application.port.outbound.ExperimentRepository;
import br.com.brew.brassia.experiment.application.service.ExperimentHandler;
import br.com.brew.brassia.experiment.application.service.ExperimentQueryService;
import br.com.brew.brassia.experiment.domain.ExperimentPlan;
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
 * Composição dos experimentos (EXP-001).
 *
 * <p>Tudo transacional, e o motivo é a escrita em três tabelas: plano, fatores e grandezas. Um plano
 * gravado sem os fatores seria um experimento sem variável isolada — exatamente o estado que o domínio
 * recusa a criar, entrando pela porta dos fundos de uma falha no meio da escrita.
 *
 * <p>Nas transições, a transação também é o que dá sentido ao {@code FOR UPDATE} do repositório: sem ela
 * o lock morreria no fim da consulta, e duas conclusões simultâneas voltariam a passar as duas.
 */
@Configuration(proxyBeanMethods = false)
class ExperimentConfiguration {

    @Bean
    ExperimentCommands experimentCommands(ExperimentRepository experiments, BatchLookup batches,
            AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new ExperimentHandler(experiments, batches, audit, Clock.systemUTC());
        var transaction = new TransactionTemplate(transactionManager);
        return new TransactionalExperimentCommands(handler, transaction);
    }

    @Bean
    ExperimentQueries experimentQueries(ExperimentRepository experiments) {
        return new ExperimentQueryService(experiments);
    }

    private record TransactionalExperimentCommands(ExperimentHandler handler,
            TransactionTemplate transaction) implements ExperimentCommands {

        @Override
        public ExperimentPlan plan(PlanCommand command) {
            return required(status -> handler.plan(command));
        }

        @Override
        public ExperimentPlan start(UUID breweryId, UUID experimentId, UUID actor) {
            return required(status -> handler.start(breweryId, experimentId, actor));
        }

        @Override
        public ExperimentPlan conclude(ConcludeCommand command) {
            return required(status -> handler.conclude(command));
        }

        @Override
        public ExperimentPlan abandon(UUID breweryId, UUID experimentId, UUID actor) {
            return required(status -> handler.abandon(breweryId, experimentId, actor));
        }

        private ExperimentPlan required(TransactionCallback<ExperimentPlan> work) {
            return Objects.requireNonNull(transaction.execute(work));
        }
    }
}
