package br.com.brew.brassia.metrology.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InstrumentTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final LocalDate HOJE = LocalDate.parse("2026-08-03");
    private static final LocalDate VENCIMENTO = LocalDate.parse("2027-08-03");

    private static MeasurementRange faixa() {
        return new MeasurementRange(new BigDecimal("-10"), new BigDecimal("110"), new BigDecimal("0.1"),
                new BigDecimal("0.5"), "°C");
    }

    private static Instrument instrumento() {
        return Instrument.register(BREWERY, "TERM-01", "Termômetro do fermentador",
                InstrumentType.THERMOMETER, faixa(), "Sala de fermentação");
    }

    private static CalibrationStandard padrao() {
        return CalibrationStandard.register(BREWERY, "PAD-01", "Banho térmico", "CERT-9001",
                "Laboratório X", "RBC", LocalDate.parse("2028-01-01"));
    }

    private static Instrument calibrado(CalibrationResult resultado, LocalDate vencimento) {
        var i = instrumento();
        i.calibrate(padrao(), HOJE.minusDays(1), vencimento, "Metrologista", "CERT-2026-1", resultado,
                new BigDecimal("0.2"), resultado == CalibrationResult.APPROVED_WITH_RESTRICTION
                        ? "faixa útil de 0 a 60 °C" : null, null, null);
        return i;
    }

    // --- aptidão derivada ---

    @Test
    void nasceAtivoESemCalibracao() {
        var i = instrumento();

        assertThat(i.state()).isEqualTo(InstrumentState.ACTIVE);
        assertThat(i.fitness(HOJE)).isEqualTo(Fitness.UNCALIBRATED);
        assertThat(i.criticalUse()).isFalse();
        assertThat(i.lastCalibration()).isEmpty();
    }

    @Test
    void ficaAptoComCalibracaoAprovadaNoPrazo() {
        assertThat(calibrado(CalibrationResult.APPROVED, VENCIMENTO).fitness(HOJE)).isEqualTo(Fitness.FIT);
    }

    @Test
    void aprovadoComRestricaoTambemEhApto() {
        var i = calibrado(CalibrationResult.APPROVED_WITH_RESTRICTION, VENCIMENTO);

        assertThat(i.fitness(HOJE)).isEqualTo(Fitness.FIT);
        assertThat(i.lastCalibration().orElseThrow().restriction()).isEqualTo("faixa útil de 0 a 60 °C");
    }

    @Test
    void venceNoDiaSeguinteAoVencimento() {
        var i = calibrado(CalibrationResult.APPROVED, VENCIMENTO);

        assertThat(i.fitness(VENCIMENTO)).isEqualTo(Fitness.FIT);
        assertThat(i.fitness(VENCIMENTO.plusDays(1))).isEqualTo(Fitness.EXPIRED);
    }

    @Test
    void calibracaoReprovadaDerrubaAAptidaoMesmoNoPrazo() {
        // A aprovação anterior ainda estava válida; a reprovação é mais recente e é ela que decide.
        var i = calibrado(CalibrationResult.APPROVED, VENCIMENTO);
        assertThat(i.fitness(HOJE)).isEqualTo(Fitness.FIT);

        i.calibrate(padrao(), HOJE, VENCIMENTO, "Metrologista", "CERT-2026-2", CalibrationResult.REJECTED,
                new BigDecimal("3.0"), null, "fora da tolerância em três pontos", null);

        assertThat(i.fitness(HOJE)).isEqualTo(Fitness.REJECTED);
    }

    @Test
    void bloqueioEBaixaPrecedemOVencimento() {
        // Instrumento bloqueado não vira "vencido" com o tempo: continua bloqueado, e a tela precisa dizer isso.
        var bloqueado = calibrado(CalibrationResult.APPROVED, VENCIMENTO);
        bloqueado.block("queda no chão");
        assertThat(bloqueado.fitness(VENCIMENTO.plusYears(1))).isEqualTo(Fitness.BLOCKED);

        var baixado = calibrado(CalibrationResult.APPROVED, VENCIMENTO);
        baixado.retire("substituído");
        assertThat(baixado.fitness(VENCIMENTO.plusYears(1))).isEqualTo(Fitness.RETIRED);
    }

    // --- ponto crítico ---

    @Test
    void naoDesignaParaPontoCriticoInstrumentoNaoApto() {
        var semCalibracao = instrumento();

        assertThatThrownBy(() -> semCalibracao.designateForCriticalUse(true, HOJE))
                .isInstanceOf(InstrumentNotFitException.class)
                .satisfies(e -> assertThat(((InstrumentNotFitException) e).fitness())
                        .isEqualTo(Fitness.UNCALIBRATED));

        var vencido = calibrado(CalibrationResult.APPROVED, VENCIMENTO);
        assertThatThrownBy(() -> vencido.designateForCriticalUse(true, VENCIMENTO.plusDays(1)))
                .isInstanceOf(InstrumentNotFitException.class)
                .satisfies(e -> assertThat(((InstrumentNotFitException) e).fitness()).isEqualTo(Fitness.EXPIRED));
    }

    @Test
    void instrumentoVencidoDeixaDeServirAoPontoCriticoSemNinguemMexerNoCadastro() {
        var i = calibrado(CalibrationResult.APPROVED, VENCIMENTO);
        i.designateForCriticalUse(true, HOJE);

        assertThat(i.fitForCriticalUse(HOJE)).isTrue();
        assertThat(i.fitForCriticalUse(VENCIMENTO)).isTrue();
        // No dia seguinte ao vencimento ele para de servir sozinho — é o tempo que decide.
        assertThat(i.fitForCriticalUse(VENCIMENTO.plusDays(1))).isFalse();
        // Mas a designação permanece: o cadastro não se reescreve às escondidas.
        assertThat(i.criticalUse()).isTrue();
    }

    @Test
    void removerDesignacaoDePontoCriticoEhSemprePermitido() {
        var i = calibrado(CalibrationResult.APPROVED, VENCIMENTO);
        i.designateForCriticalUse(true, HOJE);

        i.designateForCriticalUse(false, VENCIMENTO.plusYears(5));

        assertThat(i.criticalUse()).isFalse();
    }

    @Test
    void certificadoPermaneceDepoisDeVencer() {
        var i = calibrado(CalibrationResult.APPROVED, VENCIMENTO);

        var depois = VENCIMENTO.plusYears(2);
        assertThat(i.fitness(depois)).isEqualTo(Fitness.EXPIRED);
        assertThat(i.lastCalibration()).isPresent();
        assertThat(i.lastCalibration().orElseThrow().certificateNumber()).isEqualTo("CERT-2026-1");
        assertThat(i.calibrationDueOn()).contains(VENCIMENTO);
    }

    // --- estados e cadastro ---

    @Test
    void bloqueioExigeMotivoENaoSeRepete() {
        var i = instrumento();
        assertThatThrownBy(() -> i.block(" ")).isInstanceOf(IllegalArgumentException.class);

        i.block("aguardando calibração");
        assertThat(i.blockReason()).isEqualTo("aguardando calibração");
        assertThatThrownBy(() -> i.block("de novo")).isInstanceOf(IllegalStateException.class);

        i.unblock();
        assertThat(i.state()).isEqualTo(InstrumentState.ACTIVE);
        assertThat(i.blockReason()).isNull();
        assertThatThrownBy(i::unblock).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void baixaEhTerminalEDerrubaADesignacaoCritica() {
        var i = calibrado(CalibrationResult.APPROVED, VENCIMENTO);
        i.designateForCriticalUse(true, HOJE);

        i.retire("substituído por modelo digital");

        assertThat(i.criticalUse()).isFalse();
        assertThatThrownBy(() -> i.block("x")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> i.designateForCriticalUse(true, HOJE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> i.calibrate(padrao(), HOJE, VENCIMENTO, "M", "C", CalibrationResult.APPROVED,
                BigDecimal.ZERO, null, null, null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void mudarFaixaDeInstrumentoCriticoExigeRemoverADesignacao() {
        var i = calibrado(CalibrationResult.APPROVED, VENCIMENTO);
        i.designateForCriticalUse(true, HOJE);
        var outraFaixa = new MeasurementRange(new BigDecimal("0"), new BigDecimal("200"),
                new BigDecimal("0.1"), new BigDecimal("0.5"), "°C");

        assertThatThrownBy(() -> i.amend("Termômetro", outraFaixa, "Sala", HOJE))
                .isInstanceOf(InstrumentNotFitException.class)
                .hasMessageContaining("recalibrar");

        // Sem mexer na faixa, corrigir nome e local é livre.
        i.amend("Termômetro do FV-01", i.range(), "Sala 2", HOJE);
        assertThat(i.name()).isEqualTo("Termômetro do FV-01");
        assertThat(i.location()).isEqualTo("Sala 2");
    }

    @Test
    void recusaCalibracaoComPadraoDeOutraCervejaria() {
        var i = instrumento();
        var alheio = CalibrationStandard.register(UUID.randomUUID(), "PAD-X", "Padrão de outra casa",
                "CERT-1", "Lab", "RBC", LocalDate.parse("2028-01-01"));

        assertThatThrownBy(() -> i.calibrate(alheio, HOJE, VENCIMENTO, "M", "C", CalibrationResult.APPROVED,
                BigDecimal.ZERO, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outra cervejaria");
    }
}
