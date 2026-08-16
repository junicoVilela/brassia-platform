package br.com.brew.brassia.container.adapter.inbound.web;

import br.com.brew.brassia.container.domain.ContainerNotFillableException;
import br.com.brew.brassia.container.domain.ContainerRetiredException;
import br.com.brew.brassia.container.domain.DuplicateIdentifierException;
import br.com.brew.brassia.container.domain.IllegalContainerTransitionException;
import br.com.brew.brassia.container.domain.UnknownContainerException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduz as recusas do vasilhame em Problem Details (RFC 9457). */
@Order(0)
@RestControllerAdvice
class ContainerExceptionHandler {

    @ExceptionHandler(UnknownContainerException.class)
    ProblemDetail handleUnknown(UnknownContainerException ex) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, "container_not_found", ex.getMessage());
    }

    /**
     * 409 com o motivo dentro do corpo.
     *
     * <p>Recusar sem dizer por quê faria o operador tentar de novo com outro keg até um passar, sem nunca
     * saber o que havia de errado com o primeiro.
     */
    @ExceptionHandler(ContainerNotFillableException.class)
    ProblemDetail handleNotFillable(ContainerNotFillableException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "container_not_fillable", ex.getMessage());
        problem.setProperty("reasonCode", ex.reasonCode());
        return problem;
    }

    /** 409, e não 500: baixado é um estado legítimo do mundo, e a tela precisa poder dizer isso. */
    @ExceptionHandler(ContainerRetiredException.class)
    ProblemDetail handleRetired(ContainerRetiredException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, "container_retired", ex.getMessage());
    }

    @ExceptionHandler(IllegalContainerTransitionException.class)
    ProblemDetail handleTransition(IllegalContainerTransitionException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, "illegal_container_transition", ex.getMessage());
    }

    /** 409: a garantia é o índice único parcial, e isto é a tradução dele. */
    @ExceptionHandler(DuplicateIdentifierException.class)
    ProblemDetail handleDuplicate(DuplicateIdentifierException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, "identifier_in_use", ex.getMessage());
    }
}
