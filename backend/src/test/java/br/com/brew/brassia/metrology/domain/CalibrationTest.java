package br.com.brew.brassia.metrology.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CalibrationTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID INSTRUMENT = UUID.randomUUID();
    private static final LocalDate EXECUCAO = LocalDate.parse("2026-08-03");
    private static final LocalDate VENCIMENTO = LocalDate.parse("2027-08-03");

    private static CalibrationStandard padrao(String validUntil) {
        return CalibrationStandard.register(BREWERY, "PAD-01", "Banho térmico de referência",
                "CERT-9001", "Laboratório X", "RBC", LocalDate.parse(validUntil));
    }

    private static Calibration calibracao(CalibrationStandard padrao, CalibrationResult resultado,
            String restricao) {
        return Calibration.record(BREWERY, INSTRUMENT, padrao, EXECUCAO, VENCIMENTO, "Metrologista",
                "CERT-2026-1", resultado, new BigDecimal("0.2"), restricao, null);
    }

    @Test
    void registraCertificadoComOsDadosDoPadrao() {
        var c = calibracao(padrao("2027-01-01"), CalibrationResult.APPROVED, null);

        assertThat(c.standardCode()).isEqualTo("PAD-01");
        assertThat(c.result().approves()).isTrue();
        assertThat(c.maxDeviation()).isEqualByComparingTo("0.2");
    }

    @Test
    void recusaCalibracaoComPadraoVencido() {
        // O padrão venceu em 02/08 e a calibração é de 03/08.
        assertThatThrownBy(() -> calibracao(padrao("2026-08-02"), CalibrationResult.APPROVED, null))
                .isInstanceOf(ExpiredStandardException.class)
                .satisfies(e -> {
                    var ex = (ExpiredStandardException) e;
                    assertThat(ex.standardCode()).isEqualTo("PAD-01");
                    assertThat(ex.standardValidUntil()).isEqualTo(LocalDate.parse("2026-08-02"));
                    assertThat(ex.performedOn()).isEqualTo(EXECUCAO);
                });
    }

    @Test
    void padraoQueVenceNoDiaAindaCalibraNesseDia() {
        // Validade cobre o dia inteiro do vencimento — mesma convenção do cilindro em GAS-001.
        assertThat(calibracao(padrao("2026-08-03"), CalibrationResult.APPROVED, null)).isNotNull();
    }

    @Test
    void exigeVencimentoPosteriorAExecucao() {
        var padrao = padrao("2027-01-01");
        assertThatThrownBy(() -> Calibration.record(BREWERY, INSTRUMENT, padrao, EXECUCAO, EXECUCAO,
                "Metrologista", "CERT-1", CalibrationResult.APPROVED, BigDecimal.ZERO, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("posterior à execução");
    }

    @Test
    void aprovacaoComRestricaoExigeDescreverARestricao() {
        assertThatThrownBy(() -> calibracao(padrao("2027-01-01"),
                CalibrationResult.APPROVED_WITH_RESTRICTION, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exige descrever a restrição");
    }

    @Test
    void somenteAprovacaoComRestricaoAceitaRestricao() {
        assertThatThrownBy(() -> calibracao(padrao("2027-01-01"), CalibrationResult.APPROVED, "faixa útil menor"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("só aprovação com restrição");
    }

    @Test
    void recusaDesvioNegativo() {
        var padrao = padrao("2027-01-01");
        assertThatThrownBy(() -> Calibration.record(BREWERY, INSTRUMENT, padrao, EXECUCAO, VENCIMENTO,
                "Metrologista", "CERT-1", CalibrationResult.APPROVED, new BigDecimal("-0.1"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void vencimentoCobreODiaInformado() {
        var c = calibracao(padrao("2027-01-01"), CalibrationResult.APPROVED, null);

        assertThat(c.expiredOn(VENCIMENTO)).isFalse();
        assertThat(c.expiredOn(VENCIMENTO.plusDays(1))).isTrue();
        assertThat(c.expiredOn(VENCIMENTO.minusDays(1))).isFalse();
    }

    @Test
    void reprovadaTambemEhCertificadoValido() {
        // Reprovar é evidência e precisa ser registrável: é o que explica por que o instrumento saiu de uso.
        var c = calibracao(padrao("2027-01-01"), CalibrationResult.REJECTED, null);

        assertThat(c.result()).isEqualTo(CalibrationResult.REJECTED);
        assertThat(c.result().approves()).isFalse();
    }
}
