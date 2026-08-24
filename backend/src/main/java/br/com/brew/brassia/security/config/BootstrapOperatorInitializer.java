package br.com.brew.brassia.security.config;

import br.com.brew.brassia.security.application.port.outbound.SecurityGroupRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Garante, de forma idempotente, uma pessoa de <strong>pouca alçada</strong> em desenvolvimento, e o
 * grupo estreito a que ela pertence.
 *
 * <p><strong>Cada conta de bootstrap varia um eixo só, e isso é o que as torna provas.</strong> O admin e
 * o conferente têm as mesmas permissões e são pessoas diferentes; o operador é a mesma cervejaria com
 * <em>permissões</em> diferentes; a vizinha é a mesma alçada em <em>cervejaria</em> diferente. Uma conta
 * que variasse dois eixos ao mesmo tempo provaria menos: quando ela levasse 403, não se saberia se foi
 * por permissão ou por casa errada — e são recusas diferentes, com correções diferentes.
 *
 * <p>Por isso a associação do operador é <strong>global</strong>, como a do admin: o eixo dele é
 * permissão, e escopá-lo a uma cervejaria embaralharia os dois.
 *
 * <p><strong>Ele lê, e não fecha.</strong> As permissões abaixo foram escolhidas para que a tela
 * <em>funcione</em> antes de recusar: com um grupo vazio, a tela de custo viria em branco, e uma tela em
 * branco não distingue "não tenho alçada" de "a tela quebrou". É a leitura que faz a recusa significar
 * alguma coisa — o contraponto sem o qual o teste não separa recusa de defeito.
 *
 * <p>O grupo é criado aqui, e não por migration, de propósito: uma migration o levaria para produção, e
 * nenhuma casa pediu um grupo chamado {@code OPERADORES_LOCAIS}. Ele precisa de {@code brewery_id} nulo
 * porque é assim que {@code GroupMembershipRepository#groupIdByCode} o encontra.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@Component
class BootstrapOperatorInitializer implements ApplicationRunner {
    /**
     * Ler lote e ler custo — e <strong>não</strong> {@code costing.cost.close}, que é a recusa que se quer
     * ver na tela. O par não é aleatório: a própria mensagem da interface diz que fechar o custo é alçada
     * separada da de consultar, e é essa frase que o E2E cobra.
     */
    private static final List<String> PERMISSOES = List.of("production.batch.read", "costing.cost.read");

    private static final Logger log = LoggerFactory.getLogger(BootstrapOperatorInitializer.class);

    private final BootstrapOperatorProperties properties;
    private final BootstrapAccountSeeder seeder;
    private final SecurityGroupRepository groups;
    private final TransactionTemplate transaction;

    BootstrapOperatorInitializer(
            BootstrapOperatorProperties properties,
            BootstrapAccountSeeder seeder,
            SecurityGroupRepository groups,
            PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.seeder = seeder;
        this.groups = groups;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        if (properties.email() == null || properties.email().isBlank()
                || properties.password() == null || properties.password().isBlank()) {
            log.warn("bootstrap-operator habilitado sem email/senha; ignorando.");
            return;
        }
        transaction.executeWithoutResult(status -> ensureGroup());
        seeder.seed("operator", properties.email(), properties.password(), properties.name(),
                properties.groupCode(), null);
    }

    private void ensureGroup() {
        if (groups.existsByCode(null, properties.groupCode())) {
            return;
        }
        var groupId = groups.insert(new SecurityGroupRepository.NewGroup(
                null, properties.groupCode(), "Operadores (desenvolvimento)",
                "Grupo estreito de desenvolvimento: lê lote e custo, não fecha nada."));
        groups.replacePermissions(groupId, groups.resolveActivePermissionIds(PERMISSOES));
        log.info("bootstrap-operator: grupo {} criado com {}", properties.groupCode(), PERMISSOES);
    }
}
