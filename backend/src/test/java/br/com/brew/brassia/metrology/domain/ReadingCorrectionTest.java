package br.com.brew.brassia.metrology.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReadingCorrectionTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID ATOR = UUID.randomUUID();
    private static final LocalDate HOJE = LocalDate.parse("2026-08-03");
    private static final Instant AGORA = Instant.parse("2026-08-03T12:00:00Z");
    private static final List<CorrectionStep> PASSOS =
            List.of(CorrectionStep.temperature("SG_corr = SG × f(T)", "1"));

    private static Instrument instrumento(CalibrationResult resultado, LocalDate vencimento,
            String restricao) {
        var i = Instrument.register(BREWERY, "DENS-01", "Densímetro", InstrumentType.HYDROMETER,
                new MeasurementRange(new BigDecimal("0.990"), new BigDecimal("1.120"),
                        new BigDecimal("0.001"), new BigDecimal("0.001"), "SG"),
                "Laboratório");
        if (resultado != null) {
            var padrao = CalibrationStandard.register(BREWERY, "PAD-01", "Densímetro padrão", "CERT-1",
                    "Lab", "RBC", LocalDate.parse("2028-01-01"));
            // A execução acompanha o vencimento para o caso vencido também ser construível.
            i.calibrate(padrao, vencimento.minusYears(1), vencimento, "Metrologista", "CERT-2026-1",
                    resultado, new BigDecimal("0.001"), restricao, null, null);
        }
        return i;
    }

    private static ReadingCorrection correcao(Instrument i) {
        return ReadingCorrection.record(BREWERY, i, null, new BigDecimal("1.050"), new BigDecimal("1.048"),
                "SG", new BigDecimal("25"), new BigDecimal("20"), PASSOS, AGORA, ATOR, HOJE);
    }

    @Test
    void preservaOValorBrutoAoLadoDoCorrigido() {
        var c = correcao(instrumento(CalibrationResult.APPROVED, LocalDate.parse("2027-08-03"), null));

        assertThat(c.rawValue()).isEqualByComparingTo("1.050");
        assertThat(c.correctedValue()).isEqualByComparingTo("1.048");
        assertThat(c.delta()).isEqualByComparingTo("-0.002");
    }

    @Test
    void registraOsPassosComFormulaEVersao() {
        var c = correcao(instrumento(CalibrationResult.APPROVED, LocalDate.parse("2027-08-03"), null));

        assertThat(c.steps()).hasSize(1);
        assertThat(c.steps().get(0).name()).isEqualTo("temperature");
        assertThat(c.steps().get(0).method()).contains("SG_corr");
        assertThat(c.steps().get(0).version()).isEqualTo("1");
    }

    @Test
    void correcaoComInstrumentoAptoEhConfiavel() {
        var c = correcao(instrumento(CalibrationResult.APPROVED, LocalDate.parse("2027-08-03"), null));

        assertThat(c.instrumentFitness()).isEqualTo(Fitness.FIT);
        assertThat(c.trustworthy()).isTrue();
        assertThat(c.caveats()).isEmpty();
    }

    @Test
    void instrumentoNaoAptoNaoImpedeCorrigirMasDerrubaAConfianca() {
        // Mesmo princípio de FSL-001: a evidência incompleta não muda o número, muda a confiança nele.
        var vencido = instrumento(CalibrationResult.APPROVED, HOJE.minusDays(1), null);

        var c = correcao(vencido);

        assertThat(c.correctedValue()).isEqualByComparingTo("1.048");
        assertThat(c.instrumentFitness()).isEqualTo(Fitness.EXPIRED);
        assertThat(c.trustworthy()).isFalse();
        assertThat(c.caveats()).singleElement().asString().contains("não estava apto");
    }

    @Test
    void semCalibracaoTambemCorrigeComRessalva() {
        var c = correcao(instrumento(null, null, null));

        assertThat(c.instrumentFitness()).isEqualTo(Fitness.UNCALIBRATED);
        assertThat(c.trustworthy()).isFalse();
    }

    @Test
    void restricaoDoCertificadoViajaComoRessalva() {
        var i = instrumento(CalibrationResult.APPROVED_WITH_RESTRICTION, LocalDate.parse("2027-08-03"),
                "faixa útil de 1,000 a 1,080 SG");

        var c = correcao(i);

        // Aprovado com restrição segue apto — mas a restrição precisa aparecer para quem lê o número.
        assertThat(c.instrumentFitness()).isEqualTo(Fitness.FIT);
        assertThat(c.trustworthy()).isFalse();
        assertThat(c.caveats()).singleElement().asString().contains("faixa útil de 1,000 a 1,080 SG");
    }

    @Test
    void correcaoSemNenhumPassoNaoEhCorrecao() {
        var i = instrumento(CalibrationResult.APPROVED, LocalDate.parse("2027-08-03"), null);

        assertThatThrownBy(() -> ReadingCorrection.record(BREWERY, i, null, BigDecimal.ONE, BigDecimal.ONE,
                "SG", null, null, List.of(), AGORA, ATOR, HOJE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sem nenhum passo");
    }

    @Test
    void certificadoReprovadoNaoFornecCurva() {
        var i = Instrument.register(BREWERY, "DENS-02", "Densímetro", InstrumentType.HYDROMETER,
                new MeasurementRange(new BigDecimal("0.990"), new BigDecimal("1.120"),
                        new BigDecimal("0.001"), new BigDecimal("0.001"), "SG"),
                "Laboratório");
        var padrao = CalibrationStandard.register(BREWERY, "PAD-01", "Padrão", "CERT-1", "Lab", "RBC",
                LocalDate.parse("2028-01-01"));
        var pontos = List.of(new CurvePoint(new BigDecimal("1.000"), new BigDecimal("1.002")),
                new CurvePoint(new BigDecimal("1.100"), new BigDecimal("1.103")));

        // A curva de um certificado reprovado descreve um instrumento que falhou: não corrige nada.
        assertThatThrownBy(() -> i.calibrate(padrao, HOJE, LocalDate.parse("2027-08-03"), "M", "CERT-X",
                CalibrationResult.REJECTED, new BigDecimal("0.01"), null, null, pontos))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reprovado não fornece curva");
    }
}
