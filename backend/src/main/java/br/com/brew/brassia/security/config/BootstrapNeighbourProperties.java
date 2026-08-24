package br.com.brew.brassia.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A conta da <strong>outra cervejaria</strong> em desenvolvimento.
 *
 * <p>Ela tem a mesma alçada do admin, e isso é deliberado: o que se quer provar é que a cervejaria a
 * separa, e não a permissão. Uma conta que também tivesse pouca alçada levaria 403 por permissão e o
 * teste passaria sem nunca ter exercitado o isolamento — provando o contrário do que promete.
 *
 * @param breweryCode código da cervejaria a que a associação fica <strong>escopada</strong>. Escopada, e
 *     não global: associação global dá acesso a todas as casas, que é justamente o que aqui não pode
 *     acontecer.
 */
@ConfigurationProperties("brassia.security.bootstrap-neighbour")
public record BootstrapNeighbourProperties(
        boolean enabled, String email, String password, String name, String breweryCode) {
    public BootstrapNeighbourProperties {
        name = (name == null || name.isBlank()) ? "Vizinha Local" : name;
        breweryCode = (breweryCode == null || breweryCode.isBlank()) ? "VIZINHA" : breweryCode;
    }
}
