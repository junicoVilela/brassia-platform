package br.com.brew.brassia.community.adapter.inbound.web;

import br.com.brew.brassia.community.domain.AlreadyPublishedException;
import br.com.brew.brassia.community.domain.RecipeUnpublishedException;
import br.com.brew.brassia.community.domain.UnknownPublicationException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduz as recusas da biblioteca em Problem Details (RFC 9457). */
@Order(0)
@RestControllerAdvice
class CommunityExceptionHandler {

    /**
     * 404 para "não existe" e para "não pode ver", sem distinguir.
     *
     * <p>Numa biblioteca isso vale mais que nos outros módulos: distinguir permitiria enumerar o que as
     * outras cervejarias têm sem ler nada — bastaria contar quais identificadores respondem diferente.
     */
    @ExceptionHandler(UnknownPublicationException.class)
    ProblemDetail handleUnknown(UnknownPublicationException ex) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, "publication_not_found", ex.getMessage());
    }

    @ExceptionHandler(AlreadyPublishedException.class)
    ProblemDetail handleAlready(AlreadyPublishedException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, "version_already_published", ex.getMessage());
    }

    @ExceptionHandler(RecipeUnpublishedException.class)
    ProblemDetail handleUnpublished(RecipeUnpublishedException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, "publication_not_live", ex.getMessage());
    }
}
