package br.com.brew.brassia.gas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GasPolicyTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final LocalDate HOJE = LocalDate.parse("2026-08-03");

    @Test
    void semPoliticaNaoDerivaVencimento() {
        // Sem parâmetro, quem requalifica informa a data — comportamento anterior à PRM-001.
        assertThat(GasPolicy.none(BREWERY).nextDueOn(HOJE)).isEmpty();
    }

    @Test
    void comPoliticaDerivaOProximoVencimento() {
        var p = GasPolicy.none(BREWERY);
        p.setRequalificationMonths(60);

        assertThat(p.nextDueOn(HOJE)).contains(LocalDate.parse("2031-08-03"));
    }

    @Test
    void removerAPoliticaVoltaAExigirDataInformada() {
        var p = GasPolicy.none(BREWERY);
        p.setRequalificationMonths(60);
        p.setRequalificationMonths(null);

        assertThat(p.nextDueOn(HOJE)).isEmpty();
    }

    @Test
    void recusaPeriodicidadeInvalida() {
        var p = GasPolicy.none(BREWERY);

        assertThatThrownBy(() -> p.setRequalificationMonths(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> p.setRequalificationMonths(241))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
