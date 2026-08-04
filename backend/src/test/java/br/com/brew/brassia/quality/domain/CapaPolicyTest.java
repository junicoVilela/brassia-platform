package br.com.brew.brassia.quality.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CapaPolicyTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final LocalDate HOJE = LocalDate.parse("2026-08-03");

    @Test
    void semPoliticaOsPrazosContinuamInformados() {
        assertThat(CapaPolicy.none(BREWERY).datesFor(Severity.MAJOR, HOJE)).isEmpty();
    }

    @Test
    void osPrazosSaoPorSeveridade() {
        // O tempo aceitável para conter um problema crítico não é o de um leve.
        var p = CapaPolicy.none(BREWERY);
        p.set(Severity.CRITICAL, new CapaPolicy.Deadlines(1, 3, 15));
        p.set(Severity.MINOR, new CapaPolicy.Deadlines(5, 15, 60));

        var critica = p.datesFor(Severity.CRITICAL, HOJE).orElseThrow();
        assertThat(critica.containmentDueOn()).isEqualTo(LocalDate.parse("2026-08-04"));
        assertThat(critica.verificationDueOn()).isEqualTo(LocalDate.parse("2026-08-18"));

        var leve = p.datesFor(Severity.MINOR, HOJE).orElseThrow();
        assertThat(leve.containmentDueOn()).isEqualTo(LocalDate.parse("2026-08-08"));

        // Severidade sem política continua exigindo prazo informado.
        assertThat(p.datesFor(Severity.MAJOR, HOJE)).isEmpty();
    }

    @Test
    void osPrazosSeguemAOrdemDasFases() {
        // A mesma ordem que o agregado impõe: conter, investigar, verificar.
        assertThatThrownBy(() -> new CapaPolicy.Deadlines(10, 5, 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ordem das fases");
        assertThatThrownBy(() -> new CapaPolicy.Deadlines(1, 30, 15))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recusaPrazoNaoPositivo() {
        assertThatThrownBy(() -> new CapaPolicy.Deadlines(0, 3, 15))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contenção");
    }

    @Test
    void removerAPoliticaDeUmaSeveridadeNaoAfetaAsOutras() {
        var p = CapaPolicy.none(BREWERY);
        p.set(Severity.CRITICAL, new CapaPolicy.Deadlines(1, 3, 15));
        p.set(Severity.MINOR, new CapaPolicy.Deadlines(5, 15, 60));

        p.set(Severity.CRITICAL, null);

        assertThat(p.datesFor(Severity.CRITICAL, HOJE)).isEmpty();
        assertThat(p.datesFor(Severity.MINOR, HOJE)).isPresent();
    }
}
