package br.com.brew.brassia.community.adapter.inbound.web;

import br.com.brew.brassia.community.domain.AlreadyDecidedException;
import br.com.brew.brassia.community.domain.AlreadyPublishedException;
import br.com.brew.brassia.community.domain.AlreadyReportedException;
import br.com.brew.brassia.community.domain.NotDecidableException;
import br.com.brew.brassia.community.domain.SelfRatingException;
import br.com.brew.brassia.community.domain.ForkNotAllowedException;
import br.com.brew.brassia.community.domain.UnmappedIngredientsException;
import br.com.brew.brassia.community.domain.RecipeUnpublishedException;
import br.com.brew.brassia.community.domain.UnknownPublicationException;
import br.com.brew.brassia.community.domain.UnknownShareLinkException;
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

    /**
     * 404 para link inexistente, expirado, revogado ou de publicação fechada — sem distinguir.
     *
     * <p>Dizer "expirado" a quem tem um token inventado confirma que aquele token um dia existiu; dizer
     * "revogado" conta que houve um compartilhamento e que alguém se arrependeu. O autor, que é quem
     * precisa do motivo, vê o estado de cada link na própria lista.
     */
    @ExceptionHandler(UnknownShareLinkException.class)
    ProblemDetail handleLink(UnknownShareLinkException ex) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, "share_link_invalid", ex.getMessage());
    }

    /** 409: a publicação existe e é legível; o que ela não dá é permissão de copiar. */
    @ExceptionHandler(ForkNotAllowedException.class)
    ProblemDetail handleFork(ForkNotAllowedException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, "fork_not_allowed", ex.getMessage());
    }

    /**
     * 409 com a lista do que falta (COM-003).
     *
     * <p>Recusar inteiro é a decisão: uma receita a que faltam três de oito ingredientes não é
     * incompleta, é errada — e alguém a brassaria achando que é a do outro. A lista é o que torna a
     * recusa acionável.
     */
    @ExceptionHandler(UnmappedIngredientsException.class)
    ProblemDetail handleUnmapped(UnmappedIngredientsException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "unmapped_ingredients", ex.getMessage());
        problem.setProperty("missing", ex.missing());
        return problem;
    }

    /** 409: um comentário não propôs nada, então não há o que aceitar. */
    @ExceptionHandler(NotDecidableException.class)
    ProblemDetail handleNotDecidable(NotDecidableException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, "not_decidable", ex.getMessage());
    }

    /** 409: decidir duas vezes reescreveria quem decidiu e quando. */
    @ExceptionHandler(AlreadyDecidedException.class)
    ProblemDetail handleAlreadyDecided(AlreadyDecidedException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, "already_decided", ex.getMessage());
    }

    /** 409: a contagem de denúncias é sinal, e repetir mediria a insistência em vez da comunidade. */
    @ExceptionHandler(AlreadyReportedException.class)
    ProblemDetail handleAlreadyReported(AlreadyReportedException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, "already_reported", ex.getMessage());
    }

    /** 409: a nota do autor não informa ninguém, e denunciar-se é despublicar por outro caminho. */
    @ExceptionHandler(SelfRatingException.class)
    ProblemDetail handleSelfRating(SelfRatingException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, "self_rating", ex.getMessage());
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
