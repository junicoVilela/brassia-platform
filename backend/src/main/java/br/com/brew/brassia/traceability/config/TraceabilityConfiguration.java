package br.com.brew.brassia.traceability.config;

import br.com.brew.brassia.traceability.LineageSource;
import br.com.brew.brassia.traceability.application.port.inbound.TraceabilityQueries;
import br.com.brew.brassia.traceability.application.service.GenealogyQueryHandler;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A consulta é só leitura, então não há transação a abrir: cada fonte responde com a sua própria
 * consulta e o domínio junta. Sem escrita, não há o que tornar atômico.
 */
@Configuration(proxyBeanMethods = false)
class TraceabilityConfiguration {

    /** Recebe todas as fontes de linhagem registradas no contexto — inclusive as que ainda não existem. */
    @Bean
    TraceabilityQueries traceabilityQueries(List<LineageSource> sources) {
        return new GenealogyQueryHandler(sources);
    }
}
