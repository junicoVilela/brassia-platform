package br.com.brew.brassia.brewery.config;

import br.com.brew.brassia.brewery.BreweryDirectory;
import br.com.brew.brassia.brewery.BreweryRef;
import br.com.brew.brassia.brewery.application.port.inbound.RegisterBreweryUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Garante, de forma idempotente, a segunda cervejaria de desenvolvimento (quando habilitada por config).
 *
 * <p>A idempotência é <strong>pelo código</strong>, e não por "não existe cervejaria nenhuma" como no
 * {@code BreweryBootstrapInitializer}: aqui já existe a padrão, e o teste de vazio nunca passaria.
 *
 * <p><strong>A ordem entre os três é obrigatória, e cada elo quebra de um jeito diferente.</strong>
 * A cervejaria padrão vem primeiro porque o guarda dela é "não existe cervejaria nenhuma" — se a vizinha
 * nascesse antes, num banco vazio, a {@code MATRIZ} nunca seria criada e o ambiente subiria com a casa
 * errada como padrão. A vizinha vem em seguida porque a conta dela, semeada no módulo de segurança,
 * precisa do id desta cervejaria para receber uma associação <em>escopada</em>. E {@code ApplicationRunner}
 * sem ordem declarada roda em ordem indefinida, então nada disso pode ficar por conta da sorte.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Component
class BreweryNeighbourInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BreweryNeighbourInitializer.class);

    private final BreweryNeighbourProperties properties;
    private final BreweryDirectory directory;
    private final RegisterBreweryUseCase registerBrewery;

    BreweryNeighbourInitializer(BreweryNeighbourProperties properties, BreweryDirectory directory,
            RegisterBreweryUseCase registerBrewery) {
        this.properties = properties;
        this.directory = directory;
        this.registerBrewery = registerBrewery;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled() || exists()) {
            return;
        }
        registerBrewery.handle(new RegisterBreweryUseCase.Command(
                null, properties.code(), properties.name(), properties.timezone()));
        log.info("brewery-neighbour: segunda cervejaria '{}' criada", properties.code());
    }

    private boolean exists() {
        return directory.findAll().stream().map(BreweryRef::code).anyMatch(properties.code()::equals);
    }
}
