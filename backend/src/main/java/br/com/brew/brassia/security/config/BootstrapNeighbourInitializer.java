package br.com.brew.brassia.security.config;

import br.com.brew.brassia.brewery.BreweryDirectory;
import br.com.brew.brassia.brewery.BreweryRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Garante, de forma idempotente, uma pessoa que vive na <strong>outra</strong> cervejaria.
 *
 * <p>Sem ela, "o vizinho não enxerga isto" não tinha como ser encenado em ambiente local, e ficava para a
 * homologação todo release — apesar de o backend ter mais de vinte testes provando a regra pela API. O que
 * faltava não era a regra: era alguém para logar.
 *
 * <p><strong>A associação é escopada à cervejaria, e é isso que a torna prova.</strong> Associação global
 * — cervejaria nula, como a do admin — daria acesso a todas as casas, e a conta que deveria demonstrar o
 * isolamento passaria a demonstrar o oposto. Ver {@code SessionContextResolver}.
 *
 * <p>Roda por último, depois de {@code BreweryNeighbourInitializer} ter criado a cervejaria: sem ela, não
 * há id para escopar. Se a cervejaria não estiver lá, esta classe <strong>avisa e desiste</strong> em vez
 * de criar a conta com associação global — silenciosamente global seria o pior desfecho possível, porque
 * o teste de isolamento continuaria verde afirmando o contrário do que verifica.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@Component
class BootstrapNeighbourInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapNeighbourInitializer.class);

    private final BootstrapNeighbourProperties properties;
    private final BootstrapAccountSeeder seeder;
    private final BreweryDirectory breweries;

    BootstrapNeighbourInitializer(BootstrapNeighbourProperties properties, BootstrapAccountSeeder seeder,
            BreweryDirectory breweries) {
        this.properties = properties;
        this.seeder = seeder;
        this.breweries = breweries;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        if (properties.email() == null || properties.email().isBlank()
                || properties.password() == null || properties.password().isBlank()) {
            log.warn("bootstrap-neighbour habilitado sem email/senha; ignorando.");
            return;
        }
        var brewery = breweries.findAll().stream()
                .filter(b -> b.code().equals(properties.breweryCode()))
                .map(BreweryRef::id)
                .findFirst()
                .orElse(null);
        if (brewery == null) {
            log.warn("bootstrap-neighbour: cervejaria '{}' não existe; conta NÃO criada. "
                    + "Confira que brassia.security.bootstrap-neighbour.brewery-code e "
                    + "brassia.brewery.neighbour.code apontam para o mesmo código, e que a segunda está "
                    + "habilitada. Sem a cervejaria, a associação seria global e a conta que existe para "
                    + "demonstrar o isolamento enxergaria todas as casas.",
                    properties.breweryCode());
            return;
        }
        seeder.seed("neighbour", properties.email(), properties.password(), properties.name(),
                BootstrapAdminInitializer.ADMIN_GROUP, brewery);
    }
}
