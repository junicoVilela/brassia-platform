package br.com.brew.brassia.traceability.adapter.inbound.web;

import br.com.brew.brassia.shared.web.ProblemDetails;
import br.com.brew.brassia.traceability.domain.AlreadyQuarantinedException;
import br.com.brew.brassia.traceability.domain.DepthExceededException;
import br.com.brew.brassia.traceability.domain.PendingNotificationsException;
import br.com.brew.brassia.traceability.domain.UnknownDrillException;
import br.com.brew.brassia.traceability.domain.UnknownNodeException;
import br.com.brew.brassia.traceability.domain.UnknownQuarantineException;
import br.com.brew.brassia.traceability.domain.UnknownRecallException;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Recusas da rastreabilidade (TRC-001).
 *
 * <p>O {@code @Order} não é decoração. O advice global tem um {@code @ExceptionHandler(Exception)}
 * que pega tudo, e advices sem ordem declarada empatam — o desempate acaba sendo a ordem de
 * descoberta dos beans, isto é, alfabética por pacote. Os módulos anteriores a "shared" venciam por
 * acidente; este perdia, e as recusas viravam 500. Agora todo advice de módulo declara precedência
 * sobre o catch-all, que declara a menor de todas.
 */
@Order(0)
@RestControllerAdvice
class TraceabilityExceptionHandler {

    /**
     * 404, não 200 com grafo vazio: "não há elo" e "não há nó" são respostas opostas, e confundi-las
     * faria um id digitado errado parecer um lote sem rastreabilidade.
     */
    @ExceptionHandler(UnknownNodeException.class)
    ProblemDetail handleUnknownNode(UnknownNodeException ex) {
        var problem = ProblemDetails.of(HttpStatus.NOT_FOUND, "unknown_node", ex.getMessage());
        problem.setProperty("node", Map.of("type", ex.type().name(), "id", ex.id().toString()));
        return problem;
    }

    /** 409 e não 400: o pedido está correto, o estado é que já tem uma investigação em pé. */
    @ExceptionHandler(AlreadyQuarantinedException.class)
    ProblemDetail handleAlreadyQuarantined(AlreadyQuarantinedException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "already_quarantined",
                "Este item já está em quarentena.");
        problem.setProperty("quarantineId", ex.quarantineId().toString());
        return problem;
    }

    @ExceptionHandler(UnknownQuarantineException.class)
    ProblemDetail handleUnknownQuarantine(UnknownQuarantineException ex) {
        var problem = ProblemDetails.of(HttpStatus.NOT_FOUND, "unknown_quarantine",
                "Esta quarentena não existe nesta cervejaria.");
        problem.setProperty("quarantineId", ex.id().toString());
        return problem;
    }

    @ExceptionHandler(UnknownRecallException.class)
    ProblemDetail handleUnknownRecall(UnknownRecallException ex) {
        var problem = ProblemDetails.of(HttpStatus.NOT_FOUND, "unknown_recall",
                "Este recall não existe nesta cervejaria.");
        problem.setProperty("recallId", ex.id().toString());
        return problem;
    }

    /**
     * Encerrar com destino sem comunicação registrada é recusado: o dossiê passaria a declarar
     * terminada uma operação que deixou cerveja na prateleira de quem não foi avisado.
     */
    @ExceptionHandler(PendingNotificationsException.class)
    ProblemDetail handlePendingNotifications(PendingNotificationsException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "recall_has_pending_notifications",
                "Há destinos sem comunicação registrada; o recall não pode ser encerrado.");
        problem.setProperty("pending", ex.pending());
        return problem;
    }

    @ExceptionHandler(UnknownDrillException.class)
    ProblemDetail handleUnknownDrill(UnknownDrillException ex) {
        var problem = ProblemDetails.of(HttpStatus.NOT_FOUND, "unknown_drill",
                "Este simulado não existe nesta cervejaria.");
        problem.setProperty("drillId", ex.id().toString());
        return problem;
    }

    @ExceptionHandler(DepthExceededException.class)
    ProblemDetail handleDepth(DepthExceededException ex) {
        var problem = ProblemDetails.of(HttpStatus.BAD_REQUEST, "depth_exceeded", ex.getMessage());
        problem.setProperty("depth", Map.of("requested", ex.requested(), "maximum", ex.maximum()));
        return problem;
    }
}
