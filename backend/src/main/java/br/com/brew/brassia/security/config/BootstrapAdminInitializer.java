package br.com.brew.brassia.security.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Garante, de forma idempotente, um administrador de bootstrap ao subir a app
 * (quando habilitado por config). Cria a conta ACTIVE + credencial se ausente e
 * assegura a associação ao grupo de sistema {@code ADMINISTRATORS}.
 *
 * <p>A associação é <strong>global</strong> (cervejaria nula): o admin de desenvolvimento enxerga todas
 * as cervejarias, que é o que faz dele o ponto de partida de um ambiente vazio.
 */
@Component
class BootstrapAdminInitializer implements ApplicationRunner {
    static final String ADMIN_GROUP = "ADMINISTRATORS";
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final BootstrapAdminProperties properties;
    private final BootstrapAccountSeeder seeder;

    BootstrapAdminInitializer(BootstrapAdminProperties properties, BootstrapAccountSeeder seeder) {
        this.properties = properties;
        this.seeder = seeder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        if (properties.email() == null || properties.email().isBlank()
                || properties.password() == null || properties.password().isBlank()) {
            log.warn("bootstrap-admin habilitado sem email/senha; ignorando.");
            return;
        }
        seeder.seed("admin", properties.email(), properties.password(), properties.name(),
                ADMIN_GROUP, null);
    }
}
