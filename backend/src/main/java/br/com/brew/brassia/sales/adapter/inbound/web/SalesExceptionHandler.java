package br.com.brew.brassia.sales.adapter.inbound.web;

import br.com.brew.brassia.sales.domain.CurrencyMismatchException;
import br.com.brew.brassia.sales.domain.DuplicateSkuException;
import br.com.brew.brassia.sales.domain.OverlappingPriceException;
import br.com.brew.brassia.sales.domain.UnknownProductException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduz as recusas da SAL-001 em Problem Details (RFC 9457). */
@Order(0)
@RestControllerAdvice
class SalesExceptionHandler {

    @ExceptionHandler(UnknownProductException.class)
    ProblemDetail handleUnknown(UnknownProductException ex) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, "sales_not_found", ex.getMessage());
    }

    /**
     * A extensão se chama {@code conflictingCode}, e não {@code code}.
     *
     * <p>{@code code} é o campo que o próprio Problem Details usa para identificar o tipo do erro, e
     * reaproveitar o nome sobrescreve o identificador: o corpo passa a dizer que o erro é "PILS-350".
     * Descobri isso porque o teste comparou o campo errado — sem ele, o cliente é que descobriria.
     */
    @ExceptionHandler(DuplicateSkuException.class)
    ProblemDetail handleDuplicate(DuplicateSkuException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "sales_duplicate_code", ex.getMessage());
        problem.setProperty("conflictingCode", ex.code());
        return problem;
    }

    /**
     * A data vai na extensão porque é a informação que resolve o problema.
     *
     * <p>Quem recebeu a recusa precisa saber a partir de quando já existe preço para escolher outra
     * data ou encerrar o período antigo — sem isso, sobra tentativa e erro.
     */
    @ExceptionHandler(OverlappingPriceException.class)
    ProblemDetail handleOverlap(OverlappingPriceException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "sales_price_overlap", ex.getMessage());
        problem.setProperty("from", ex.from().toString());
        return problem;
    }

    /** 409 e não 400: a requisição está bem formada; é a linha do tempo que já tem outra moeda. */
    @ExceptionHandler(CurrencyMismatchException.class)
    ProblemDetail handleCurrency(CurrencyMismatchException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, "sales_currency_mismatch", ex.getMessage());
    }
}
