package br.com.brew.brassia.brewery.config;

import br.com.brew.brassia.brewery.BreweryDirectory;
import br.com.brew.brassia.brewery.application.port.inbound.RegisterBreweryUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Garante, de forma idempotente, uma cervejaria default ao subir (quando
 * habilitado por config). Sem cervejaria não há tenant ativo para o login.
 *
 * <p><strong>Roda antes de qualquer outro semeador</strong> porque o guarda desta classe é "não existe
 * cervejaria nenhuma": qualquer casa criada antes dela a faria desistir em silêncio, e o ambiente subiria
 * sem a cervejaria padrão. Ver {@link BreweryNeighbourInitializer}.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
class BreweryBootstrapInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BreweryBootstrapInitializer.class);

    private final BreweryBootstrapProperties properties;
    private final BreweryDirectory directory;
    private final RegisterBreweryUseCase registerBrewery;

    BreweryBootstrapInitializer(BreweryBootstrapProperties properties, BreweryDirectory directory,
            RegisterBreweryUseCase registerBrewery) {
        this.properties = properties;
        this.directory = directory;
        this.registerBrewery = registerBrewery;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled() || !directory.findAll().isEmpty()) {
            return;
        }
        registerBrewery.handle(new RegisterBreweryUseCase.Command(
                null, properties.code(), properties.name(), properties.timezone()));
        log.info("brewery-bootstrap: cervejaria default '{}' criada", properties.code());
    }
}
