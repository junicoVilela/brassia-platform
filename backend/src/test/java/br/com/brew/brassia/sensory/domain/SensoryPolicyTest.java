package br.com.brew.brassia.sensory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SensoryPolicyTest {

    private static final UUID BREWERY = UUID.randomUUID();

    @Test
    void aEscalaPadraoEhADeAntesDaParametrizacao() {
        assertThat(SensoryPolicy.defaults(BREWERY).maxScore()).isEqualTo(10);
    }

    @Test
    void aceitaEscalaBjcp() {
        var p = SensoryPolicy.defaults(BREWERY);
        p.setMaxScore(50);

        assertThat(p.maxScore()).isEqualTo(50);
    }

    @Test
    void recusaEscalaQueNaoDiscrimina() {
        var p = SensoryPolicy.defaults(BREWERY);

        assertThatThrownBy(() -> p.setMaxScore(2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não discrimina");
        assertThatThrownBy(() -> p.setMaxScore(101)).isInstanceOf(IllegalArgumentException.class);
    }
}
