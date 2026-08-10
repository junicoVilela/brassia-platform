package br.com.brew.brassia.fieldfeedback.adapter.inbound.web;

import br.com.brew.brassia.fieldfeedback.domain.FieldComplaint;
import br.com.brew.brassia.fieldfeedback.domain.PendingActionsException;
import br.com.brew.brassia.fieldfeedback.domain.UnknownComplaintBatchException;
import br.com.brew.brassia.fieldfeedback.domain.UnknownComplaintException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Recusas do feedback de campo (FLD-001). */
@Order(0)
@RestControllerAdvice
class FieldFeedbackExceptionHandler {

    /**
     * A reclamação exige ações que ninguém atendeu nem dispensou.
     *
     * <p>422 e não 400: o pedido está bem formado. O que falta é <em>trabalho</em>, e a resposta lista
     * exatamente qual — porque as duas saídas legítimas são opostas e a pessoa precisa escolher: abrir a
     * quarentena, ou dispensá-la por escrito e assinar.
     */
    @ExceptionHandler(PendingActionsException.class)
    ProblemDetail handlePending(PendingActionsException ex) {
        var problem = ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "pending_required_actions",
                "Esta reclamação não pode ser encerrada enquanto houver ação exigida sem destino. "
                        + "Atenda cada uma, ou dispense-a com justificativa.");
        problem.setProperty("pendingActions", ex.pending().stream().map(Enum::name).toList());
        return problem;
    }

    @ExceptionHandler(UnknownComplaintBatchException.class)
    ProblemDetail handleUnknownBatch(UnknownComplaintBatchException ex) {
        return ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "unknown_complaint_batch",
                ex.getMessage());
    }

    @ExceptionHandler(FieldComplaint.IllegalComplaintTransitionException.class)
    ProblemDetail handleTransition(FieldComplaint.IllegalComplaintTransitionException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "illegal_complaint_transition",
                "A reclamação está em " + ex.current() + " e não pode ir para " + ex.attempted() + ".");
        problem.setProperty("currentStatus", ex.current().name());
        return problem;
    }

    /**
     * Reclamação inexistente — ou contato inexistente.
     *
     * <p>A mensagem é a mesma nos dois casos de propósito: distinguir "reclamação não existe" de
     * "reclamação existe mas não tem contato" diria, a quem não tem permissão para o dado pessoal, se
     * existe dado pessoal ali.
     */
    @ExceptionHandler(UnknownComplaintException.class)
    ProblemDetail handleUnknown(UnknownComplaintException ex) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, "unknown_complaint",
                "Reclamação não encontrada nesta cervejaria.");
    }
}
