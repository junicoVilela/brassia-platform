package br.com.brew.brassia.ai.config;

import br.com.brew.brassia.ai.ModelGateway;
import br.com.brew.brassia.ai.adapter.outbound.provider.ModelProviders;
import br.com.brew.brassia.ai.application.port.inbound.AnswerCommands;
import br.com.brew.brassia.ai.application.port.inbound.AssessmentCommands;
import br.com.brew.brassia.ai.application.port.inbound.BudgetCommands;
import br.com.brew.brassia.ai.application.port.inbound.GatewayCommands;
import br.com.brew.brassia.ai.application.port.inbound.GatewayQueries;
import br.com.brew.brassia.ai.application.port.inbound.ProposalCommands;
import br.com.brew.brassia.ai.application.port.inbound.ProposalQueries;
import br.com.brew.brassia.ai.application.port.outbound.AiBudgetRepository;
import br.com.brew.brassia.ai.application.port.outbound.CommandProposalRepository;
import br.com.brew.brassia.ai.application.port.outbound.ModelInvocationLedger;
import br.com.brew.brassia.ai.application.port.outbound.ModelProvider;
import br.com.brew.brassia.ai.application.port.outbound.StructuredResponseReader;
import br.com.brew.brassia.ai.application.service.BatchAssessmentHandler;
import br.com.brew.brassia.ai.application.service.BatchFactsAssembler;
import br.com.brew.brassia.ai.application.service.BudgetHandler;
import br.com.brew.brassia.ai.application.service.CommandProposalHandler;
import br.com.brew.brassia.ai.application.service.GatewayStatusService;
import br.com.brew.brassia.ai.application.service.GroundedAnswerHandler;
import br.com.brew.brassia.ai.application.service.ModelGatewayService;
import br.com.brew.brassia.ai.application.service.ProbeHandler;
import br.com.brew.brassia.ai.application.service.ProposalQueryService;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.costing.BatchCostLookup;
import br.com.brew.brassia.fermentation.FermentationLookup;
import br.com.brew.brassia.knowledge.KnowledgeRetrieval;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.production.BatchOutcomeLookup;
import br.com.brew.brassia.quality.BatchQualityLookup;
import br.com.brew.brassia.recipe.RecipeLookup;
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

    /**
     * Avaliar lote (AIA-002).
     *
     * <p>O montador recebe as consultas publicadas de seis módulos, e é assim de propósito: cada número da
     * avaliação vem de quem responde por ele. Recalcular qualquer um aqui criaria uma segunda opinião sobre o
     * mesmo fato. A fermentação entrou por último (DEB-AIA-001), e era a que faltava para o modelo enxergar
     * curva, agenda e levedura.
     */
    @Bean
    AssessmentCommands assessmentCommands(BatchLookup batches, BatchOutcomeLookup outcomes,
            BatchQualityLookup quality, BatchCostLookup costs, RecipeLookup recipes,
            FermentationLookup fermentation, ModelGateway gateway, AuditTrail audit) {
        var facts = new BatchFactsAssembler(batches, outcomes, quality, costs, recipes, fermentation);
        return new BatchAssessmentHandler(facts, gateway, audit, Clock.systemUTC());
    }

    /**
     * Propor comando (AIA-003).
     *
     * <p>Sem {@code TransactionTemplate} envolvendo a proposta: a chamada ao modelo é efeito externo já
     * consumado, e uma transação em volta dela só criaria a ilusão de que dá para desfazer o gasto. Cada
     * proposta válida é gravada por si — se a terceira for malformada, as duas primeiras continuam propostas
     * legítimas, e descartá-las por causa dela seria perder trabalho pago.
     *
     * <p>A decisão também não precisa de transação: é uma linha, atualizada condicionalmente ao estado
     * pendente. É a condição no {@code UPDATE}, e não o isolamento, que impede dois aceites da mesma proposta.
     */
    @Bean
    ProposalCommands proposalCommands(BatchLookup batches, BatchOutcomeLookup outcomes,
            BatchQualityLookup quality, BatchCostLookup costs, RecipeLookup recipes,
            FermentationLookup fermentation, ModelGateway gateway, CommandProposalRepository proposals,
            AuditTrail audit) {
        var facts = new BatchFactsAssembler(batches, outcomes, quality, costs, recipes, fermentation);
        return new CommandProposalHandler(facts, gateway, proposals, audit, Clock.systemUTC());
    }

    @Bean
    ProposalQueries proposalQueries(CommandProposalRepository proposals) {
        return new ProposalQueryService(proposals);
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
