package br.com.brew.brassia.sanitation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CleaningPolicyTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final Instant LIBERACAO = Instant.parse("2026-08-03T08:00:00Z");

    @Test
    void semPrazoConfiguradoALiberacaoNaoExpira() {
        // É o comportamento anterior à PRM-001: ganhar o campo não faz a plataforma inventar número.
        var p = CleaningPolicy.none(BREWERY);

        assertThat(p.validityHours()).isEmpty();
        assertThat(p.covers(LIBERACAO, LIBERACAO.plusSeconds(3600L * 24 * 365))).isTrue();
    }

    @Test
    void comPrazoALiberacaoExpiraDepoisDeleEValeAteOLimite() {
        var p = CleaningPolicy.none(BREWERY);
        p.setValidityHours(24);

        assertThat(p.covers(LIBERACAO, LIBERACAO.plusSeconds(3600L * 23))).isTrue();
        // Exatamente no limite ainda cobre — o prazo é o último instante aceitável.
        assertThat(p.covers(LIBERACAO, LIBERACAO.plusSeconds(3600L * 24))).isTrue();
        assertThat(p.covers(LIBERACAO, LIBERACAO.plusSeconds(3600L * 24 + 1))).isFalse();
    }

    @Test
    void removerOPrazoVoltaANaoExpirar() {
        var p = CleaningPolicy.none(BREWERY);
        p.setValidityHours(8);
        assertThat(p.covers(LIBERACAO, LIBERACAO.plusSeconds(3600L * 9))).isFalse();

        p.setValidityHours(null);

        assertThat(p.covers(LIBERACAO, LIBERACAO.plusSeconds(3600L * 9))).isTrue();
    }

    @Test
    void recusaPrazoNaoPositivoOuAbsurdo() {
        var p = CleaningPolicy.none(BREWERY);

        assertThatThrownBy(() -> p.setValidityHours(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> p.setValidityHours(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> p.setValidityHours(24 * 366))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("acima de um ano");
    }
}
