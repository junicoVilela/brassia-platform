package br.com.brew.brassia.brewery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BreweryDomainTest {

    @Test
    void codeNormalizesToUpperCase() {
        assertThat(new BreweryCode("  sb40-lab ").value()).isEqualTo("SB40-LAB");
    }

    @Test
    void codeRejectsBlankTooLongOrInvalidChars() {
        assertThatThrownBy(() -> new BreweryCode(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BreweryCode("com espaço")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BreweryCode("A".repeat(41))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nameTrimsAndBounds() {
        assertThat(new BreweryName("  Casa Brew  ").value()).isEqualTo("Casa Brew");
        assertThatThrownBy(() -> new BreweryName("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void timezoneAcceptsValidZoneAndRejectsInvalid() {
        assertThat(new Timezone("America/Sao_Paulo").value()).isEqualTo("America/Sao_Paulo");
        assertThatThrownBy(() -> new Timezone("Marte/Olympus")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registerBuildsAggregate() {
        var brewery = Brewery.register(new BreweryCode("sb40"), new BreweryName("Casa Brew"),
                new Timezone("America/Sao_Paulo"));

        assertThat(brewery.id()).isNotNull();
        assertThat(brewery.code().value()).isEqualTo("SB40");
        assertThat(brewery.name().value()).isEqualTo("Casa Brew");
        assertThat(brewery.version()).isZero();
    }
}
