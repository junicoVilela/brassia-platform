package br.com.brew.brassia.reporting.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.costing.BatchCostLookup;
import br.com.brew.brassia.packaging.PackagingOutcomeLookup;
import br.com.brew.brassia.planning.OrderPlanLookup;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.production.BatchOutcomeLookup;
import br.com.brew.brassia.quality.BatchQualityLookup;
import br.com.brew.brassia.reporting.application.port.inbound.BatchReportQueries;
import br.com.brew.brassia.reporting.application.port.inbound.DashboardQueries;
import br.com.brew.brassia.reporting.application.service.BatchReportAssembler;
import br.com.brew.brassia.reporting.application.port.inbound.SavedReportUseCases;
import br.com.brew.brassia.reporting.application.port.outbound.SavedReportRepository;
import br.com.brew.brassia.reporting.application.service.DashboardQueryHandler;
import br.com.brew.brassia.reporting.application.service.ReportExecutionService;
import br.com.brew.brassia.reporting.application.service.SavedReportHandlers;
import br.com.brew.brassia.security.EffectivePermissionLookup;
import br.com.brew.brassia.shared.reporting.IndicatorSource;
import br.com.brew.brassia.traceability.BatchLineageLookup;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O relatório só consome. Não há repositório aqui, e não é omissão: o dossiê é montado a cada
 * pedido a partir do que cada módulo responde, porque guardá-lo faria a versão salva discordar da
 * produção no dia seguinte.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class ReportingConfiguration {

    @Bean
    BatchReportQueries batchReportQueries(BatchLookup batches, BatchOutcomeLookup outcomes,
            OrderPlanLookup plans, PackagingOutcomeLookup packaging, BatchQualityLookup quality,
            BatchCostLookup costs, BatchLineageLookup lineage) {
        // Relógio injetado, e não `Instant.now()` no meio do código: o teste precisa fixar a data
        // do documento para poder afirmar que ela sai nele.
        return new BatchReportAssembler(batches, outcomes, plans, packaging, quality, costs, lineage,
                Clock.systemUTC());
    }

    /** Recebe todas as fontes de indicador registradas — inclusive as que ainda não existem. */
    @Bean
    DashboardQueries dashboardQueries(List<IndicatorSource> sources) {
        return new DashboardQueryHandler(sources);
    }

    @Bean
    ReportExecutionService reportExecutionService(SavedReportRepository reports,
            DashboardQueries dashboard, BatchReportQueries batchReports,
            EffectivePermissionLookup permissions) {
        // Com os módulos de tempo: o conteúdo do relatório tem Instant, e sem eles a serialização
        // falharia só na hora de executar — o pior momento para descobrir.
        var json = JsonMapper.builder().findAndAddModules().build();
        return new ReportExecutionService(reports, dashboard, batchReports, permissions, json);
    }

    @Bean
    SavedReportUseCases.Queries savedReportQueries(SavedReportRepository reports) {
        return new SavedReportHandlers.Queries(reports);
    }

    /**
     * Definir grava cabeçalho e destinatários num commit só: uma definição sem a lista de quem
     * recebe seria uma programação que não entrega a ninguém.
     */
    @Bean
    SavedReportUseCases.Define defineSavedReportUseCase(SavedReportRepository reports,
            AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new SavedReportHandlers.Define(reports, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return (actorId, breweryId, command) -> java.util.Objects.requireNonNull(
                transaction.execute(status -> handler.handle(actorId, breweryId, command)));
    }

    @Bean
    SavedReportUseCases.Redefine redefineSavedReportUseCase(SavedReportRepository reports,
            AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new SavedReportHandlers.Redefine(reports, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return (actorId, breweryId, reportId, command) -> java.util.Objects.requireNonNull(
                transaction.execute(status -> handler.handle(actorId, breweryId, reportId, command)));
    }

    @Bean
    SavedReportUseCases.Activate activateSavedReportUseCase(SavedReportRepository reports,
            AuditTrail audit) {
        return new SavedReportHandlers.Activate(reports, audit);
    }

    /** Execução e entregas iniciais num commit só: artefato sem lista de entrega não se cobra. */
    @Bean
    SavedReportUseCases.Run runSavedReportUseCase(SavedReportRepository reports,
            ReportExecutionService execution, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new SavedReportHandlers.Run(reports, execution, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return (actorId, breweryId, reportId) -> java.util.Objects.requireNonNull(
                transaction.execute(status -> handler.handle(actorId, breweryId, reportId)));
    }

    @Bean
    SavedReportUseCases.Deliver deliverSavedReportUseCase(SavedReportRepository reports,
            AuditTrail audit) {
        return new SavedReportHandlers.Deliver(reports, audit);
    }

    @Bean
    SavedReportUseCases.Download downloadSavedReportUseCase(SavedReportRepository reports,
            AuditTrail audit) {
        return new SavedReportHandlers.Download(reports, audit);
    }
}
