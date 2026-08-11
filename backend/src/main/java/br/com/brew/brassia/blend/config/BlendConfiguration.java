package br.com.brew.brassia.blend.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.blend.application.port.inbound.BlendCommands;
import br.com.brew.brassia.blend.application.port.inbound.BlendQueries;
import br.com.brew.brassia.blend.application.port.outbound.BlendRepository;
import br.com.brew.brassia.blend.application.service.BlendHandler;
import br.com.brew.brassia.blend.application.service.BlendQueryService;
import br.com.brew.brassia.blend.domain.BlendOperation;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.production.BlendResultCommands;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Composição do blend (BLD-001).
 *
 * <p>Transacional em tudo: a simulação escreve operação e movimentos em duas tabelas, e uma operação sem
 * os movimentos gravados seria um balanço sem parcelas. Nas transições, a transação é também o que dá
 * sentido ao {@code FOR UPDATE} — sem ela o lock morreria no fim da consulta e duas execuções simultâneas
 * moveriam a cerveja duas vezes.
 */
@Configuration(proxyBeanMethods = false)
class BlendConfiguration {

    @Bean
    BlendCommands blendCommands(BlendRepository operations, BatchLookup batches,
            BlendResultCommands production, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new BlendHandler(operations, batches, production, audit, Clock.systemUTC());
        return new TransactionalBlendCommands(handler, new TransactionTemplate(transactionManager));
    }

    @Bean
    BlendQueries blendQueries(BlendRepository operations) {
        return new BlendQueryService(operations);
    }

    private record TransactionalBlendCommands(BlendHandler handler, TransactionTemplate transaction)
            implements BlendCommands {

        @Override
        public BlendOperation simulate(SimulateCommand command) {
            return required(status -> handler.simulate(command));
        }

        @Override
        public BlendOperation approve(UUID breweryId, UUID operationId, UUID actor) {
            return required(status -> handler.approve(breweryId, operationId, actor));
        }

        @Override
        public BlendOperation execute(UUID breweryId, UUID operationId, UUID actor) {
            return required(status -> handler.execute(breweryId, operationId, actor));
        }

        @Override
        public BlendOperation discard(UUID breweryId, UUID operationId, UUID actor) {
            return required(status -> handler.discard(breweryId, operationId, actor));
        }

        private BlendOperation required(TransactionCallback<BlendOperation> work) {
            return Objects.requireNonNull(transaction.execute(work));
        }
    }
}
