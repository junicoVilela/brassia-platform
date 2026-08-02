package br.com.brew.brassia.packaging.application.port.inbound;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Compromete o plano de envase (PKG-001): verifica disponibilidade e limpeza da linha e
 * reserva o lote de embalagem. Tudo num commit — falha em qualquer verificação não deixa
 * reserva nem transição pela metade.
 */
public interface ReservePackagingPlanUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID planId) {}

    record Result(UUID planId, BigDecimal reservedUnits, String unit) {}
}
