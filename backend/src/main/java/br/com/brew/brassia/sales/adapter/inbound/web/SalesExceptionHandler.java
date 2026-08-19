package br.com.brew.brassia.sales.adapter.inbound.web;

import br.com.brew.brassia.sales.domain.CreditLimitCurrencyMismatchException;
import br.com.brew.brassia.sales.domain.CreditLimitExceededException;
import br.com.brew.brassia.shared.money.CurrencyMismatchException;
import br.com.brew.brassia.sales.domain.DuplicateSkuException;
import br.com.brew.brassia.sales.domain.InsufficientLotStockException;
import br.com.brew.brassia.sales.domain.NoPriceForProductException;
import br.com.brew.brassia.sales.domain.AlreadyReversedException;
import br.com.brew.brassia.sales.domain.OrderNotChangeableException;
import br.com.brew.brassia.sales.domain.PaymentExceedsBalanceException;
import br.com.brew.brassia.sales.domain.PromiseAfterShelfLifeException;
import br.com.brew.brassia.sales.domain.UnreservedQuantityException;
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

    /**
     * O teto de compromisso em aberto foi ultrapassado (SAL-003).
     *
     * <p>Os três números vão na resposta porque um sozinho não resolve: saber que "passou do limite"
     * sem saber de quanto é o teto, quanto já está comprometido e quanto este pedido pede deixa quem
     * comprou sem ação — e no portal não há um vendedor por perto para explicar.
     */
    @ExceptionHandler(CreditLimitExceededException.class)
    ProblemDetail handleCredit(CreditLimitExceededException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "credit_limit_exceeded", ex.getMessage());
        problem.setProperty("ceiling", ex.ceiling());
        problem.setProperty("committed", ex.committed());
        problem.setProperty("requested", ex.requested());
        problem.setProperty("currency", ex.currency());
        return problem;
    }

    /**
     * O teto está numa moeda e o pedido em outra (SAL-004).
     *
     * <p>Tem código próprio porque o conserto é de <strong>cadastro</strong>, não de pedido: sem isto a
     * soma estourava dentro do domínio de dinheiro e devolvia um `sales_currency_mismatch` genérico,
     * que mandava o vendedor procurar o erro no lugar errado.
     */
    @ExceptionHandler(CreditLimitCurrencyMismatchException.class)
    ProblemDetail handleCreditCurrency(CreditLimitCurrencyMismatchException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "credit_limit_currency_mismatch",
                ex.getMessage());
        problem.setProperty("ceilingCurrency", ex.ceilingCurrency());
        problem.setProperty("orderCurrency", ex.orderCurrency());
        return problem;
    }

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

    /**
     * A promessa incompatível com a validade (SAL-002).
     *
     * <p>As duas datas e o lote vão na resposta porque é o que resolve: quem prometeu precisa saber
     * até quando pode prometer, e qual lote limita — sem isso, sobra tentativa e erro.
     */
    @ExceptionHandler(PromiseAfterShelfLifeException.class)
    ProblemDetail handlePromise(PromiseAfterShelfLifeException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "promise_after_shelf_life", ex.getMessage());
        problem.setProperty("promisedFor", ex.promisedFor().toString());
        problem.setProperty("earliestBestBefore", ex.earliestBestBefore().toString());
        problem.setProperty("lotCode", ex.lotCode());
        return problem;
    }

    /**
     * Estoque insuficiente — inclusive quando a causa foi perder a corrida por ele (SAL-002).
     *
     * <p>Do ponto de vista de quem chamou os dois casos são o mesmo: o estoque acabou entre olhar e
     * pedir. Por isso a resposta fala de unidades, e não de concorrência.
     */
    @ExceptionHandler(InsufficientLotStockException.class)
    ProblemDetail handleStock(InsufficientLotStockException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "insufficient_lot_stock", ex.getMessage());
        problem.setProperty("requested", ex.requested());
        problem.setProperty("available", ex.available());
        return problem;
    }

    @ExceptionHandler(UnreservedQuantityException.class)
    ProblemDetail handleUnreserved(UnreservedQuantityException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "unreserved_quantity", ex.getMessage());
        problem.setProperty("requested", ex.requested());
        problem.setProperty("reserved", ex.reserved());
        return problem;
    }

    /** 409: o produto existe e o canal existe; o que falta é alguém ter precificado. */
    @ExceptionHandler(NoPriceForProductException.class)
    ProblemDetail handleNoPrice(NoPriceForProductException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, "no_price_for_product", ex.getMessage());
    }

    @ExceptionHandler(OrderNotChangeableException.class)
    ProblemDetail handleNotChangeable(OrderNotChangeableException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, "order_not_changeable", ex.getMessage());
    }

    /** 409 e não 400: a requisição está bem formada; é a linha do tempo que já tem outra moeda. */
    @ExceptionHandler(CurrencyMismatchException.class)
    ProblemDetail handleCurrency(CurrencyMismatchException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, "sales_currency_mismatch", ex.getMessage());
    }

    /**
     * O recebimento passa do que o pedido deve (DEB-SAL-002).
     *
     * <p>O saldo vai na resposta porque é o que resolve: quem digitou um zero a mais precisa ver o
     * número certo, e quem lançou no pedido errado descobre isso ao ler que aquele pedido deve outra
     * coisa.
     */
    @ExceptionHandler(PaymentExceedsBalanceException.class)
    ProblemDetail handleExceeds(PaymentExceedsBalanceException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "payment_exceeds_balance", ex.getMessage());
        problem.setProperty("outstanding", ex.outstanding());
        problem.setProperty("requested", ex.requested());
        problem.setProperty("currency", ex.currency());
        return problem;
    }

    /** 409: o recebimento existe, e já foi estornado. Estornar de novo tiraria dinheiro duas vezes. */
    @ExceptionHandler(AlreadyReversedException.class)
    ProblemDetail handleAlreadyReversed(AlreadyReversedException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, "payment_already_reversed", ex.getMessage());
    }
}
