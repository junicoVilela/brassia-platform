package br.com.brew.brassia.utilities.config;

import br.com.brew.brassia.utilities.PackagedVolumeSource;
import br.com.brew.brassia.utilities.UtilityReadingSource;
import br.com.brew.brassia.utilities.application.port.inbound.UtilityQueries;
import br.com.brew.brassia.utilities.application.service.UtilityQueryHandler;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * O indicador é leitura pura: não há nada a gravar, porque o que se guarda é a medição, e ela já
 * está guardada nos módulos que medem.
 */
@Configuration(proxyBeanMethods = false)
class UtilitiesConfiguration {

    /** Recebe todas as fontes de medição registradas — inclusive as que ainda não existem. */
    @Bean
    UtilityQueries utilityQueries(List<UtilityReadingSource> sources, PackagedVolumeSource packaged) {
        return new UtilityQueryHandler(sources, packaged);
    }
}
