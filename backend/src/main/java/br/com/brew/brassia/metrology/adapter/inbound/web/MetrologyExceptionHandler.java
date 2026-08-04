package br.com.brew.brassia.metrology.adapter.inbound.web;

import br.com.brew.brassia.metrology.domain.ExpiredStandardException;
import br.com.brew.brassia.metrology.domain.InstrumentNotFitException;
import br.com.brew.brassia.metrology.domain.OutsideCurveRangeException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Recusas de metrologia (MTR-001) em Problem Details. As duas dizem <em>por que</em> não dá, em
 * vez de só negar: sem a aptidão e o vencimento, quem recebe o erro não sabe se recalibra, se
 * desbloqueia ou se troca de instrumento.
 */
@Order(0)
@RestControllerAdvice
class MetrologyExceptionHandler {

    @ExceptionHandler(InstrumentNotFitException.class)
    ProblemDetail handleNotFit(InstrumentNotFitException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "instrument_not_fit", ex.getMessage());
        problem.setProperty("instrument", java.util.Map.of(
                "code", ex.instrumentCode(),
                "fitness", ex.fitness().name(),
                "calibrationDueOn", ex.calibrationDueOn() == null ? "" : ex.calibrationDueOn().toString()));
        return problem;
    }

    /**
     * Leitura fora da faixa que o certificado verificou. Devolvemos os limites junto: sem eles a
     * pessoa não sabe se o problema é a leitura, o instrumento ou a curva cadastrada.
     */
    @ExceptionHandler(OutsideCurveRangeException.class)
    ProblemDetail handleOutsideCurve(OutsideCurveRangeException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "outside_curve_range", ex.getMessage());
        problem.setProperty("curve", java.util.Map.of(
                "value", ex.value().toPlainString(),
                "min", ex.curveMin().toPlainString(),
                "max", ex.curveMax().toPlainString()));
        return problem;
    }

    @ExceptionHandler(ExpiredStandardException.class)
    ProblemDetail handleExpiredStandard(ExpiredStandardException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "standard_expired", ex.getMessage());
        problem.setProperty("standard", java.util.Map.of(
                "code", ex.standardCode(),
                "validUntil", ex.standardValidUntil().toString(),
                "performedOn", ex.performedOn().toString()));
        return problem;
    }
}
