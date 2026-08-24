package br.com.brew.brassia.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A conta de <strong>pouca alçada</strong> em desenvolvimento.
 *
 * <p>O admin e o conferente estão os dois em {@code ADMINISTRATORS}, deliberadamente — eles existem para
 * exercitar a regra de <em>pessoas diferentes</em>. Faltava alguém para exercitar a de <em>permissões
 * diferentes</em>: sem ninguém de pouca alçada para logar, a recusa por permissão só podia ser provada
 * pela API, e ninguém nunca a via numa tela.
 */
@ConfigurationProperties("brassia.security.bootstrap-operator")
public record BootstrapOperatorProperties(
        boolean enabled, String email, String password, String name, String groupCode) {
    public BootstrapOperatorProperties {
        name = (name == null || name.isBlank()) ? "Operador Local" : name;
        groupCode = (groupCode == null || groupCode.isBlank()) ? "OPERADORES_LOCAIS" : groupCode;
    }
}
