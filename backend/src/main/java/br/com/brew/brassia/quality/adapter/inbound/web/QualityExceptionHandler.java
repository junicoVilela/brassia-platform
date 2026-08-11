package br.com.brew.brassia.quality.adapter.inbound.web;

import br.com.brew.brassia.quality.domain.CriticalPointInstrumentException;
import br.com.brew.brassia.quality.domain.PhaseOutOfOrderException;
import br.com.brew.brassia.quality.domain.PlanNotPublishedException;
import br.com.brew.brassia.quality.domain.VerificationRequiredException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Recusas de qualidade (QLT-001), sempre dizendo o que falta para a medição valer. */
@Order(0)
@RestControllerAdvice
class QualityExceptionHandler {

    /**
     * Ponto crítico com instrumento não apto. Devolvemos a aptidão junto: sem ela a pessoa não
     * sabe se recalibra, se desbloqueia ou se troca de instrumento.
     */
    /**
     * Lote inexistente na NC (DEB-AIA-003).
     *
     * <p>Quem recusa é a chave estrangeira, e não uma checagem prévia: duas requisições simultâneas
     * passariam as duas por ela, e um lote cancelado entre a checagem e o INSERT deixaria a NC apontando
     * para o nada. Aqui só se traduz o que o banco decidiu.
     *
     * <p>O `field` é a única informação acionável — sem ele, "referência inválida" num corpo com meia
     * dúzia de campos deixa quem opera adivinhando qual conserta.
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ProblemDetail handleIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
        var message = ex.getMostSpecificCause().getMessage();
        if (message != null && message.contains("fk_quality_nc_batch")) {
            var problem = ProblemDetails.of(HttpStatus.BAD_REQUEST, "unknown_batch",
                    "O lote informado não existe nesta cervejaria.");
            problem.setProperty("field", "batchId");
            return problem;
        }
        throw ex;
    }

    @ExceptionHandler(CriticalPointInstrumentException.class)
    ProblemDetail handleCriticalPoint(CriticalPointInstrumentException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "instrument_not_fit", ex.getMessage());
        problem.setProperty("controlPoint", Map.of(
                "parameter", ex.parameter(),
                "instrument", ex.instrumentCode(),
                "fitness", ex.fitness()));
        return problem;
    }

    /**
     * Tentativa de pular fase do CAPA. Devolvemos a fase atual: sem ela a pessoa não sabe se falta
     * conter, investigar ou concluir uma ação.
     */
    @ExceptionHandler(PhaseOutOfOrderException.class)
    ProblemDetail handlePhaseOutOfOrder(PhaseOutOfOrderException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "nc_phase_out_of_order", ex.getMessage());
        problem.setProperty("nonConformity", Map.of(
                "code", ex.code(),
                "status", ex.current().name(),
                "attempted", ex.attempted()));
        return problem;
    }

    /** Encerrar sem verificação eficaz — o critério central da história. */
    @ExceptionHandler(VerificationRequiredException.class)
    ProblemDetail handleVerificationRequired(VerificationRequiredException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "verification_required", ex.getMessage());
        problem.setProperty("nonConformity", Map.of("code", ex.code()));
        return problem;
    }

    @ExceptionHandler(PlanNotPublishedException.class)
    ProblemDetail handleDraftPlan(PlanNotPublishedException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "plan_not_published", ex.getMessage());
        problem.setProperty("plan", Map.of("code", ex.planCode()));
        return problem;
    }
}
