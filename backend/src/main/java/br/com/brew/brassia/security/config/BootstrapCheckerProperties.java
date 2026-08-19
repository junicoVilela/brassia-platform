package br.com.brew.brassia.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bootstrap de uma <strong>segunda</strong> pessoa, para os fluxos que exigem duas.
 *
 * <p>Existe por uma razão específica e verificável: a carga de distribuição é planejada por alguém e
 * <strong>liberada por outra pessoa</strong> (LOG-001), e a regra é do agregado, da alçada e de um
 * {@code CHECK} no banco. Com um único usuário de bootstrap não há como exercitar a separação de deveres
 * de ponta a ponta — o E2E só alcançava a recusa, e nunca o caminho feliz que vem depois dela.
 *
 * <p>Não há caminho público para criar essa conta: o convite entrega a senha por token que só sai pela
 * notificação, e o SCIM exige credencial de serviço e não define senha. Ambos estão certos; é o ambiente
 * de desenvolvimento que precisa de uma porta própria.
 *
 * <p>Desligado por padrão, e habilitado apenas no perfil {@code local} — como o
 * {@link BootstrapAdminProperties}, e com a mesma advertência: credenciais descartáveis, que nunca valem
 * fora da máquina de desenvolvimento.
 */
@ConfigurationProperties("brassia.security.bootstrap-checker")
public record BootstrapCheckerProperties(boolean enabled, String email, String password, String name) {
    public BootstrapCheckerProperties {
        name = (name == null || name.isBlank()) ? "Conferente" : name;
    }
}
