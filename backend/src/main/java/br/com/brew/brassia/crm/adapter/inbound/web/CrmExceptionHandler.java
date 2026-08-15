package br.com.brew.brassia.crm.adapter.inbound.web;

import br.com.brew.brassia.crm.domain.ContactAnonymizedException;
import br.com.brew.brassia.crm.domain.DuplicateTaxIdException;
import br.com.brew.brassia.crm.domain.UnknownCustomerException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduz as recusas da CRM-001 em Problem Details (RFC 9457). */
@Order(0)
@RestControllerAdvice
class CrmExceptionHandler {

    /**
     * 404 tanto para "não existe" quanto para "é de outra cervejaria".
     *
     * <p>Um 403 no segundo caso confirmaria que o identificador existe em algum lugar — um oráculo de
     * existência atravessando a fronteira entre cervejarias.
     */
    @ExceptionHandler(UnknownCustomerException.class)
    ProblemDetail handleUnknown(UnknownCustomerException ex) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, "crm_not_found", ex.getMessage());
    }

    @ExceptionHandler(DuplicateTaxIdException.class)
    ProblemDetail handleDuplicate(DuplicateTaxIdException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "crm_duplicate_tax_id", ex.getMessage());
        problem.setProperty("taxId", ex.taxId());
        return problem;
    }

    /**
     * 409 e não 404: o contato existe, e é justamente por existir que a recusa faz sentido.
     *
     * <p>Devolver 404 esconderia que houve um apagamento — e quem chamou concluiria que o identificador
     * está errado, quando o que aconteceu foi alguém exercer o direito de ser esquecido.
     */
    @ExceptionHandler(ContactAnonymizedException.class)
    ProblemDetail handleAnonymized(ContactAnonymizedException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, "crm_contact_anonymized", ex.getMessage());
    }
}
