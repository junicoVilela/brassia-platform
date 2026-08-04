package br.com.brew.brassia.metrology.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CalibrationPolicyTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final LocalDate HOJE = LocalDate.parse("2026-08-03");

    @Test
    void semPoliticaOVencimentoContinuaVindoDoCertificado() {
        assertThat(CalibrationPolicy.none(BREWERY).nextDueOn(InstrumentType.THERMOMETER, HOJE)).isEmpty();
    }

    @Test
    void aPeriodicidadeEhPorTipoDeInstrumento() {
        // O prazo de um termômetro não é o de uma balança.
        var p = CalibrationPolicy.none(BREWERY);
        p.set(InstrumentType.THERMOMETER, 12);
        p.set(InstrumentType.SCALE, 24);

        assertThat(p.nextDueOn(InstrumentType.THERMOMETER, HOJE)).contains(LocalDate.parse("2027-08-03"));
        assertThat(p.nextDueOn(InstrumentType.SCALE, HOJE)).contains(LocalDate.parse("2028-08-03"));
        // Tipo sem periodicidade continua sem derivação.
        assertThat(p.nextDueOn(InstrumentType.PH_METER, HOJE)).isEmpty();
    }

    @Test
    void removerAPeriodicidadeDeUmTipoNaoAfetaOsOutros() {
        var p = CalibrationPolicy.none(BREWERY);
        p.set(InstrumentType.THERMOMETER, 12);
        p.set(InstrumentType.SCALE, 24);

        p.set(InstrumentType.THERMOMETER, null);

        assertThat(p.nextDueOn(InstrumentType.THERMOMETER, HOJE)).isEmpty();
        assertThat(p.nextDueOn(InstrumentType.SCALE, HOJE)).isPresent();
        assertThat(p.monthsByType()).containsOnlyKeys(InstrumentType.SCALE);
    }

    @Test
    void recusaPeriodicidadeInvalida() {
        var p = CalibrationPolicy.none(BREWERY);

        assertThatThrownBy(() -> p.set(InstrumentType.THERMOMETER, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> p.set(InstrumentType.THERMOMETER, 121))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
