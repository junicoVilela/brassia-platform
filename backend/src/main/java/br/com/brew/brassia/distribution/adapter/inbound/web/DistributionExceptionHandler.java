package br.com.brew.brassia.distribution.adapter.inbound.web;

import br.com.brew.brassia.distribution.domain.ContainerNotShippableException;
import br.com.brew.brassia.distribution.domain.IllegalLoadTransitionException;
import br.com.brew.brassia.distribution.domain.LoadCapacityExceededException;
import br.com.brew.brassia.distribution.domain.SeparationOfDutiesException;
import br.com.brew.brassia.distribution.domain.UnknownLoadException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduz as recusas da distribuição em Problem Details (RFC 9457). */
@Order(0)
@RestControllerAdvice
class DistributionExceptionHandler {

    @ExceptionHandler(UnknownLoadException.class)
    ProblemDetail handleUnknown(UnknownLoadException ex) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, "load_not_found", ex.getMessage());
    }

    /** 409, e não 403: não é falta de permissão, é a mesma pessoa nos dois papéis. */
    @ExceptionHandler(SeparationOfDutiesException.class)
    ProblemDetail handleSegregation(SeparationOfDutiesException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, "separation_of_duties", ex.getMessage());
    }

    /** 409 com quanto passou — "excedeu" manda tirar itens no chute até caber. */
    @ExceptionHandler(LoadCapacityExceededException.class)
    ProblemDetail handleCapacity(LoadCapacityExceededException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "load_capacity_exceeded", ex.getMessage());
        problem.setProperty("excessLiters", ex.excessLiters());
        return problem;
    }

    /** 409 com o motivo: keg vazio se enche, lote não liberado se cobra, quarentena não se resolve hoje. */
    @ExceptionHandler(ContainerNotShippableException.class)
    ProblemDetail handleNotShippable(ContainerNotShippableException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "container_not_shippable",
                ex.getMessage());
        problem.setProperty("reasonCode", ex.reasonCode());
        return problem;
    }

    @ExceptionHandler(IllegalLoadTransitionException.class)
    ProblemDetail handleTransition(IllegalLoadTransitionException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, "illegal_load_transition", ex.getMessage());
    }
}
