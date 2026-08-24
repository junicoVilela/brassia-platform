package br.com.brew.brassia.security.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Garante, de forma idempotente, uma <strong>segunda</strong> pessoa em desenvolvimento.
 *
 * <p><strong>Ela não é um administrador a mais: é a outra pessoa.</strong> Os fluxos com separação de
 * deveres — a carga planejada por um e liberada por outro (LOG-001) — não têm caminho feliz com um
 * usuário só, e é justamente o caminho depois da recusa que ninguém conseguia exercitar de ponta a ponta.
 *
 * <p>Ela entra no mesmo grupo {@code ADMINISTRATORS} do admin de bootstrap, e isso é deliberado: a regra
 * que se quer exercitar é a de <strong>pessoas diferentes</strong>, e não a de permissões diferentes. O
 * agregado recusa a mesma pessoa nos dois papéis mesmo quando ela tem as duas alçadas — e é essa recusa,
 * a que sobrevive a quem tem tudo, que precisa de prova.
 *
 * <p>Quem exercita <em>permissões</em> diferentes é o {@link BootstrapOperatorInitializer}, e quem
 * exercita <em>cervejarias</em> diferentes é o {@link BootstrapNeighbourInitializer}. As três contas
 * respondem a perguntas distintas, e confundi-las faz um teste provar menos do que parece.
 */
@Component
class BootstrapCheckerInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapCheckerInitializer.class);

    private final BootstrapCheckerProperties properties;
    private final BootstrapAccountSeeder seeder;

    BootstrapCheckerInitializer(BootstrapCheckerProperties properties, BootstrapAccountSeeder seeder) {
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
            log.warn("bootstrap-checker habilitado sem email/senha; ignorando.");
            return;
        }
        seeder.seed("checker", properties.email(), properties.password(), properties.name(),
                BootstrapAdminInitializer.ADMIN_GROUP, null);
    }
}
