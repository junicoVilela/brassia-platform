package br.com.brew.brassia.reporting.config;

import br.com.brew.brassia.costing.BatchCostLookup;
import br.com.brew.brassia.packaging.PackagingOutcomeLookup;
import br.com.brew.brassia.planning.OrderPlanLookup;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.production.BatchOutcomeLookup;
import br.com.brew.brassia.quality.BatchQualityLookup;
import br.com.brew.brassia.reporting.application.port.inbound.BatchReportQueries;
import br.com.brew.brassia.reporting.application.port.inbound.DashboardQueries;
import br.com.brew.brassia.reporting.application.service.BatchReportAssembler;
import br.com.brew.brassia.reporting.application.service.DashboardQueryHandler;
import br.com.brew.brassia.shared.reporting.IndicatorSource;
import br.com.brew.brassia.traceability.BatchLineageLookup;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * O relatório só consome. Não há repositório aqui, e não é omissão: o dossiê é montado a cada
 * pedido a partir do que cada módulo responde, porque guardá-lo faria a versão salva discordar da
 * produção no dia seguinte.
 */
@Configuration(proxyBeanMethods = false)
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
}
