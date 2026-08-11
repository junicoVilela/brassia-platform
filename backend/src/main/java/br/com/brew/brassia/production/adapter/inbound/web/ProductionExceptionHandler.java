package br.com.brew.brassia.production.adapter.inbound.web;

import br.com.brew.brassia.production.domain.BrewConsumptionException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import br.com.brew.brassia.production.domain.DirtyEquipmentException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Recusas da produção.
 *
 * <p>O {@code @Order} declara precedência sobre o catch-all do advice global, que sem isso venceria
 * por ordem de descoberta dos beans e transformaria a recusa em 500.
 */
@Order(0)
@RestControllerAdvice
class ProductionExceptionHandler {

    /**
     * Tanque sem limpeza liberada (CLN-004-A).
     *
     * <p>409 e não 422: o pedido está correto, o mundo é que não permite — o tanque está sujo. A resposta
     * diz <strong>desde quando</strong>, porque a ação muda com isso: um tanque que esvaziou hoje de manhã
     * precisa de um CIP; um parado sujo há três semanas precisa de uma conversa antes do CIP.
     */
    @ExceptionHandler(DirtyEquipmentException.class)
    ProblemDetail handleDirtyEquipment(DirtyEquipmentException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "equipment_not_clean",
                "O equipamento de destino não tem limpeza liberada. Execute e libere um ciclo de limpeza "
                        + "antes de transferir.");
        problem.setProperty("equipmentId", ex.equipmentId());
        ex.soiledSince().ifPresent(since -> problem.setProperty("soiledSince", since));
        return problem;
    }

    /**
     * Consumo declarado acima do que o lote tem (TRC-001-C). A falta vai lote a lote: ou a
     * quantidade foi digitada errada, ou o lote usado foi outro, e adivinhar qual seria inventar
     * produção.
     */
    @ExceptionHandler(BrewConsumptionException.class)
    ProblemDetail handleShortfall(BrewConsumptionException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "brew_consumption_shortfall",
                "O consumo declarado é maior do que o estoque do lote.");
        List<Map<String, Object>> shortfalls = ex.shortfalls().stream()
                .map(shortfall -> Map.<String, Object>of(
                        "lotId", shortfall.lotId().toString(),
                        "requested", shortfall.requested(),
                        "available", shortfall.available(),
                        "unit", shortfall.unit()))
                .toList();
        problem.setProperty("shortfalls", shortfalls);
        return problem;
    }
}
