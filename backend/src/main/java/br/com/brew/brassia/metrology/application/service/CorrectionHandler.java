package br.com.brew.brassia.metrology.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.calculator.CalculatorEngine;
import br.com.brew.brassia.metrology.application.port.inbound.InstrumentCommands;
import br.com.brew.brassia.metrology.application.port.outbound.InstrumentRepository;
import br.com.brew.brassia.metrology.domain.Calibration;
import br.com.brew.brassia.metrology.domain.CorrectionCurve;
import br.com.brew.brassia.metrology.domain.CorrectionStep;
import br.com.brew.brassia.metrology.domain.ReadingCorrection;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Correção metrológica de leitura (MTR-002).
 *
 * <p>Dois passos independentes, nesta ordem: <strong>temperatura</strong> pelo hub
 * {@code calculator} — fórmula compartilhada e versionada, a mesma do resto da plataforma — e
 * depois a <strong>curva do certificado</strong>, que é dado do instrumento e por isso vive no
 * domínio de metrologia. A ordem importa: a curva foi levantada comparando o instrumento ao
 * padrão em condição de referência, então ela se aplica ao valor já compensado por temperatura.
 *
 * <p>O bruto nunca é tocado. Instrumento fora de aptidão não impede a correção — vira ressalva.
 */
public final class CorrectionHandler implements InstrumentCommands.CorrectReading {

    private final InstrumentRepository instruments;
    private final CalculatorEngine calculator;
    private final AuditTrail audit;

    public CorrectionHandler(InstrumentRepository instruments, CalculatorEngine calculator, AuditTrail audit) {
        this.instruments = Objects.requireNonNull(instruments);
        this.calculator = Objects.requireNonNull(calculator);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public UUID handle(Command command) {
        var instrument = instruments.findById(command.breweryId(), command.instrumentId())
                .orElseThrow(() -> new IllegalArgumentException("instrumento inexistente"));

        var steps = new ArrayList<CorrectionStep>();
        var value = command.rawValue();

        if (command.sampleTempC() != null && command.calibrationTempC() != null) {
            var computation = calculator.compute("hydrometer-temp-correction", Map.of(
                    "measuredSg", value,
                    "sampleTempC", command.sampleTempC(),
                    "calibrationTempC", command.calibrationTempC()));
            value = computation.value();
            steps.add(CorrectionStep.temperature(computation.method(), computation.version()));
        }

        if (command.applyCurve()) {
            var calibration = instrument.lastCalibration()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "instrumento sem calibração não tem curva para corrigir"));
            var curve = calibration.curve()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "o certificado não traz os pontos conferidos; não há curva para aplicar"));
            value = applyCurve(curve, value);
            steps.add(CorrectionStep.curve(curve.method(), calibration.certificateNumber()));
        }

        if (steps.isEmpty()) {
            throw new IllegalArgumentException(
                    "informe a temperatura da amostra e a de calibração, ou peça a curva: sem passo não há correção");
        }

        var correction = ReadingCorrection.record(command.breweryId(), instrument, command.sourceReadingId(),
                command.rawValue(), value, command.unit(), command.sampleTempC(), command.calibrationTempC(),
                steps, Instant.now(), command.actorId(), LocalDate.now(ZoneOffset.UTC));
        instruments.insertCorrection(correction);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "metrology.reading.correct",
                "metrology.correction", correction.id().toString(),
                Map.of("instrument", instrument.code(),
                        "raw", correction.rawValue().toPlainString(),
                        "corrected", correction.correctedValue().toPlainString(),
                        "fitness", correction.instrumentFitness().name(),
                        "trustworthy", String.valueOf(correction.trustworthy()))));
        return correction.id();
    }

    private static BigDecimal applyCurve(CorrectionCurve curve, BigDecimal value) {
        return curve.correct(value);
    }

    /** Certificado sem curva não corrige: declarar desvio máximo diz a incerteza, não a correção. */
    static boolean hasCurve(Calibration calibration) {
        return calibration.curve().isPresent();
    }
}
