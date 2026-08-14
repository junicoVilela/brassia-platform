package br.com.brew.brassia.quality.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.metrology.InstrumentStatusLookup;
import br.com.brew.brassia.production.BatchAlertPublisher;
import br.com.brew.brassia.quality.application.port.inbound.ControlPlanCommands;
import br.com.brew.brassia.quality.application.port.inbound.MeasurementCommands;
import br.com.brew.brassia.production.OpenBatchLookup;
import br.com.brew.brassia.quality.NonConformityOpening;
import br.com.brew.brassia.quality.application.port.outbound.FrequencySweepRepository;
import br.com.brew.brassia.quality.application.service.FrequencySweepService;
import br.com.brew.brassia.quality.application.port.inbound.NonConformityCommands;
import br.com.brew.brassia.quality.application.port.inbound.QualityQueries;
import br.com.brew.brassia.quality.application.port.outbound.CapaPolicyRepository;
import br.com.brew.brassia.quality.application.port.outbound.ControlPlanRepository;
import br.com.brew.brassia.quality.application.port.outbound.MeasurementRepository;
import br.com.brew.brassia.quality.application.port.outbound.NonConformityRepository;
import br.com.brew.brassia.quality.application.service.ControlPlanHandlers;
import br.com.brew.brassia.quality.application.service.MeasurementHandler;
import br.com.brew.brassia.quality.application.service.NonConformityHandlers;
import br.com.brew.brassia.quality.application.service.QualityQueriesHandler;
import br.com.brew.brassia.quality.application.port.inbound.CapaPolicyUseCase;
import br.com.brew.brassia.quality.application.service.CapaPolicyHandler;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cada comando de qualidade roda num commit só. A medição é o caso que exige: julgar, gravar e
 * abrir o desvio acontecem juntos — separar permitiria uma medição fora da faixa existir sem o
 * desvio correspondente, que é o estado que a história existe para impedir.
 *
 * <p>{@code @Transactional} em método {@code @Bean} não tem efeito, então a transação é explícita.
 */
@Configuration(proxyBeanMethods = false)
class QualityConfiguration {

    @Bean
    ControlPlanCommands.Create createControlPlanUseCase(ControlPlanRepository plans, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new ControlPlanHandlers.Create(plans, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    ControlPlanCommands.Amend amendControlPlanUseCase(ControlPlanRepository plans, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new ControlPlanHandlers.Amend(plans, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    ControlPlanCommands.AddPoint addControlPointUseCase(ControlPlanRepository plans, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new ControlPlanHandlers.AddPoint(plans, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    ControlPlanCommands.RemovePoint removeControlPointUseCase(ControlPlanRepository plans, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new ControlPlanHandlers.RemovePoint(plans, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    ControlPlanCommands.Publish publishControlPlanUseCase(ControlPlanRepository plans, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new ControlPlanHandlers.Publish(plans, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    ControlPlanCommands.NewVersion newControlPlanVersionUseCase(ControlPlanRepository plans, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new ControlPlanHandlers.NewVersion(plans, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    /**
     * O nome carrega {@code quality} porque nome de {@code @Bean} também é global: `production`
     * já registra um {@code recordMeasurementUseCase} (leitura de processo), e este é a medição
     * contra o plano de controle.
     */
    @Bean
    MeasurementCommands.Record recordQualityMeasurementUseCase(ControlPlanRepository plans,
            MeasurementRepository measurements, InstrumentStatusLookup instruments,
            BatchAlertPublisher alerts, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new MeasurementHandler(plans, measurements, instruments, alerts, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    // --- não conformidade e CAPA (QLT-002) ---

    /** Varredura de cadência: avisa controle atrasado na central do lote (QLT-001-A). */
    @Bean
    FrequencySweepService frequencySweepService(FrequencySweepRepository sweep, OpenBatchLookup batches,
            BatchAlertPublisher alerts) {
        return new FrequencySweepService(sweep, batches, alerts, java.time.Clock.systemUTC());
    }

    /**
     * Abertura publicada para o copiloto (DEB-AIA-003).
     *
     * <p>Passa pelo mesmo caso de uso da tela — mesma validação de lote, mesma numeração, mesma política
     * de prazos, mesma auditoria. Um caminho paralelo para a IA seria um segundo lugar onde as regras
     * precisariam ser mantidas iguais, e elas divergiriam na primeira mudança.
     */
    @Bean
    NonConformityOpening nonConformityOpening(NonConformityCommands.Open open) {
        // Origem OTHER, e não DEVIATION: a NC de origem DEVIATION exige apontar um desvio registrado
        // (CHECK da V77), e a avaliação de lote que gera a proposta não é um desvio da tela de qualidade.
        // Declarar DEVIATION sem desvio seria mentir para passar numa restrição.
        //
        // Código nulo pede numeração ao sistema; os três prazos nulos deixam a política da casa decidi-los.
        return (breweryId, actorId, batchId, title, severity, origin) -> open.handle(
                new NonConformityCommands.Open.Command(actorId, breweryId, null, title, origin,
                        "OTHER", null, batchId, severity, null, null, null));
    }

    @Bean
    NonConformityCommands.Open openNonConformityUseCase(NonConformityRepository nonConformities,
            MeasurementRepository measurements, CapaPolicyRepository policies,
            AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new NonConformityHandlers.Open(nonConformities, measurements, policies, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    NonConformityCommands.Contain containNonConformityUseCase(NonConformityRepository nonConformities,
            AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new NonConformityHandlers.Contain(nonConformities, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    NonConformityCommands.Investigate investigateNonConformityUseCase(
            NonConformityRepository nonConformities, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new NonConformityHandlers.Investigate(nonConformities, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    NonConformityCommands.PlanAction planCapaActionUseCase(NonConformityRepository nonConformities,
            AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new NonConformityHandlers.PlanAction(nonConformities, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    NonConformityCommands.CompleteAction completeCapaActionUseCase(NonConformityRepository nonConformities,
            AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new NonConformityHandlers.CompleteAction(nonConformities, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    NonConformityCommands.Verify verifyNonConformityUseCase(NonConformityRepository nonConformities,
            AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new NonConformityHandlers.Verify(nonConformities, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    /** Encerrar a NC e o desvio de origem vai num commit só: um sem o outro mente no painel. */
    @Bean
    NonConformityCommands.Close closeNonConformityUseCase(NonConformityRepository nonConformities,
            MeasurementRepository measurements, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new NonConformityHandlers.Close(nonConformities, measurements, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    QualityQueries qualityQueries(ControlPlanRepository plans, MeasurementRepository measurements,
            NonConformityRepository nonConformities) {
        return new QualityQueriesHandler(plans, measurements, nonConformities);
    }

    /** Política de CAPA (PRM-001): prazos por severidade. */
    @Bean
    CapaPolicyUseCase capaPolicyUseCase(CapaPolicyRepository policies, AuditTrail audit) {
        return new CapaPolicyHandler(policies, audit);
    }

}
