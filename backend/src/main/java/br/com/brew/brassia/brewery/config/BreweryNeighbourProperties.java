package br.com.brew.brassia.brewery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A <strong>segunda</strong> cervejaria de desenvolvimento.
 *
 * <p>Existe para que o isolamento entre cervejarias possa ser exercitado pela tela. Com uma casa só, a
 * afirmação "o vizinho não enxerga isto" não tinha como ser encenada em ambiente local — e era por isso
 * que ela ficava para a homologação, todo release.
 *
 * <p><strong>O código precisa ordenar depois do da cervejaria padrão, e isso não é detalhe de gosto.</strong>
 * {@code SessionContextResolver} escolhe como ativa a <em>primeira por código</em> entre as acessíveis, e
 * o admin de bootstrap tem associação global — ou seja, alcança as duas. Um código que ordenasse antes de
 * {@code MATRIZ} trocaria a cervejaria ativa de toda sessão de desenvolvimento e de toda a suíte E2E, que
 * passaria a semear numa casa e a ler na outra. {@code VIZINHA} vem depois de {@code MATRIZ}; se este
 * padrão for trocado, o novo valor tem de continuar vindo depois.
 */
@ConfigurationProperties("brassia.brewery.neighbour")
public record BreweryNeighbourProperties(boolean enabled, String code, String name, String timezone) {
    public BreweryNeighbourProperties {
        code = (code == null || code.isBlank()) ? "VIZINHA" : code;
        name = (name == null || name.isBlank()) ? "Cervejaria Vizinha" : name;
        timezone = (timezone == null || timezone.isBlank()) ? "America/Sao_Paulo" : timezone;
    }
}
