package br.com.brew.brassia.gas.adapter.inbound.web;

import br.com.brew.brassia.gas.domain.GasConnectionBlockedException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz a recusa de conexão de gás (GAS-001) em 409 Problem Details, com a lista completa de
 * impedimentos na extensão {@code blockers} — em rede de gás, descobrir um problema por tentativa
 * significa montar e desmontar a linha várias vezes.
 */
@RestControllerAdvice
class GasExceptionHandler {

    @ExceptionHandler(GasConnectionBlockedException.class)
    ProblemDetail handleBlocked(GasConnectionBlockedException ex) {
        return blocked(ex.blockers().stream()
                .map(b -> Map.of("code", b.code(), "message", b.message()))
                .toList());
    }

    /**
     * Corrida entre dois comandos de conexão: os índices parciais únicos (um cilindro por ponto,
     * um ponto por cilindro) barram o segundo no banco. Responde 409 como o caminho normal, em vez
     * de deixar vazar um erro interno.
     */
    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail handleConcurrentConnect(DuplicateKeyException ex) {
        return blocked(List.of(Map.of("code", "point_of_use_occupied",
                "message", "O cilindro ou o ponto de uso foi ocupado por outra operação; recarregue e tente de novo.")));
    }

    private static ProblemDetail blocked(List<Map<String, String>> blockers) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "gas_connection_blocked",
                "A linha de gás não pôde ser conectada; há impedimentos.");
        problem.setProperty("blockers", blockers);
        return problem;
    }
}
