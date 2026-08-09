package br.com.brew.brassia.digitaltwin.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.digitaltwin.application.port.inbound.ControlChartQueries;
import br.com.brew.brassia.digitaltwin.application.port.inbound.ProfileCommands;
import br.com.brew.brassia.digitaltwin.application.port.inbound.ProfileQueries;
import br.com.brew.brassia.digitaltwin.application.port.outbound.LearnedProfileRepository;
import br.com.brew.brassia.digitaltwin.application.service.ComputeProfileHandler;
import br.com.brew.brassia.digitaltwin.application.service.ControlChartService;
import br.com.brew.brassia.digitaltwin.application.service.ProfileQueryService;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.production.BatchMeasurementLookup;
import br.com.brew.brassia.production.BatchOutcomeLookup;
import java.time.Clock;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Composição do perfil aprendido (DTW-001).
 *
 * <p>O cálculo é transacional porque grava três tabelas — perfil, estimativas e amostra — e as três só
 * fazem sentido juntas. Um perfil sem a amostra gravada seria um número sem contra o que conferir; um
 * perfil sem estimativas seria uma linha vazia.
 */
@Configuration(proxyBeanMethods = false)
class DigitalTwinConfiguration {

    @Bean
    ProfileCommands twinProfileCommands(LearnedProfileRepository profiles, BatchLookup batches,
            BatchOutcomeLookup outcomes, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new ComputeProfileHandler(profiles, batches, outcomes, audit, Clock.systemUTC());
        var transaction = new TransactionTemplate(transactionManager);
        return request -> Objects.requireNonNull(transaction.execute(status -> handler.compute(request)));
    }

    /**
     * A carta de controle não é transacional porque não grava nada.
     *
     * <p>Uma carta é uma leitura da série que já existe — as medições são o registro, e elas estão na
     * produção. Persistir a carta criaria uma cópia que envelhece: uma medição corrigida amanhã deixaria a
     * carta de hoje afirmando um limite que os dados não sustentam mais.
     */
    @Bean
    ControlChartQueries twinControlChartQueries(BatchLookup batches,
            BatchMeasurementLookup measurements) {
        return new ControlChartService(batches, measurements);
    }

    @Bean
    ProfileQueries twinProfileQueries(LearnedProfileRepository profiles) {
        return new ProfileQueryService(profiles);
    }
}
