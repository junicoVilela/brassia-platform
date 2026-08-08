package br.com.brew.brassia.ai.config;

import br.com.brew.brassia.ai.ModelGateway;
import br.com.brew.brassia.ai.adapter.outbound.provider.ModelProviders;
import br.com.brew.brassia.ai.application.port.inbound.AnswerCommands;
import br.com.brew.brassia.ai.application.port.inbound.BudgetCommands;
import br.com.brew.brassia.ai.application.port.inbound.GatewayCommands;
import br.com.brew.brassia.ai.application.port.inbound.GatewayQueries;
import br.com.brew.brassia.ai.application.port.outbound.AiBudgetRepository;
import br.com.brew.brassia.ai.application.port.outbound.ModelInvocationLedger;
import br.com.brew.brassia.ai.application.port.outbound.ModelProvider;
import br.com.brew.brassia.ai.application.port.outbound.StructuredResponseReader;
import br.com.brew.brassia.ai.application.service.BudgetHandler;
import br.com.brew.brassia.ai.application.service.GatewayStatusService;
import br.com.brew.brassia.ai.application.service.GroundedAnswerHandler;
import br.com.brew.brassia.ai.application.service.ModelGatewayService;
import br.com.brew.brassia.ai.application.service.ProbeHandler;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.knowledge.KnowledgeRetrieval;
import java.time.Clock;
import java.util.Objects;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Composição do módulo de IA (AIA-001).
 *
 * <p><strong>Há sempre um provedor.</strong> Desligado é uma implementação, não a ausência de uma. É o
 * que faz "provedor desabilitado não quebra fluxo" valer por construção em vez de por disciplina: nenhum
 * caso de uso recebe nulo, e nenhum precisa de {@code if} para descobrir isso.
 *
 * <p><strong>Redefinir o teto é transação; chamar o modelo não é.</strong> O teto é escrita em banco com
 * verificação de versão e merece transação. A chamada ao modelo é efeito externo já consumado quando
 * termina — envolvê-la numa transação só criaria a ilusão de que dá para desfazer.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
class AiConfiguration {

    @Bean
    ModelProvider modelProvider(AiProperties properties) {
        return ModelProviders.from(properties);
    }

    @Bean
    ModelGateway modelGateway(ModelProvider provider, AiBudgetRepository budgets,
            ModelInvocationLedger ledger, StructuredResponseReader reader, AuditTrail audit) {
        return new ModelGatewayService(provider, budgets, ledger, reader, audit, Clock.systemUTC());
    }

    @Bean
    GatewayCommands gatewayCommands(ModelGateway gateway) {
        return new ProbeHandler(gateway);
    }

    /**
     * Responder com fonte (RAG-002).
     *
     * <p>Sem transação: a pergunta não escreve nada de negócio. O que ela grava — a linha do ledger e o
     * evento de auditoria — já é gravado em transação própria pelo gateway, porque o gasto aconteceu de
     * verdade e não pode ser desfeito por rollback de quem perguntou.
     */
    @Bean
    AnswerCommands answerCommands(KnowledgeRetrieval retrieval, ModelGateway gateway, AuditTrail audit) {
        return new GroundedAnswerHandler(retrieval, gateway, audit, Clock.systemUTC());
    }

    @Bean
    GatewayQueries gatewayQueries(ModelProvider provider, AiBudgetRepository budgets,
            ModelInvocationLedger ledger) {
        return new GatewayStatusService(provider, budgets, ledger);
    }

    @Bean
    BudgetCommands budgetCommands(AiBudgetRepository budgets, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new BudgetHandler(budgets, audit, Clock.systemUTC());
        var transaction = new TransactionTemplate(transactionManager);
        return (actorId, breweryId, limit, expectedVersion) -> Objects.requireNonNull(
                transaction.execute(status -> handler.redefine(actorId, breweryId, limit, expectedVersion)));
    }
}
