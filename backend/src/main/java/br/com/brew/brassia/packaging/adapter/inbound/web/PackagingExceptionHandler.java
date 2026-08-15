package br.com.brew.brassia.packaging.adapter.inbound.web;

import br.com.brew.brassia.packaging.domain.BatchVolumeExceededException;
import br.com.brew.brassia.packaging.domain.LotAlreadyReleasedException;
import br.com.brew.brassia.packaging.domain.UnknownFinishedLotException;
import br.com.brew.brassia.packaging.domain.LabelNotPrintableException;
import br.com.brew.brassia.packaging.domain.ContainerPressureExceededException;
import br.com.brew.brassia.packaging.domain.OverCarbonationException;
import br.com.brew.brassia.packaging.domain.PackagingBlockedException;
import br.com.brew.brassia.packaging.domain.PackagingStockShortfallException;
import br.com.brew.brassia.packaging.domain.ShipmentExceedsLotException;
import br.com.brew.brassia.packaging.domain.VolumeBalanceException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz as recusas de reserva de envase (PKG-001) em 409 Problem Details: a lista completa
 * de bloqueios vai na extensão {@code blockers} e a falta de embalagem na extensão
 * {@code shortfall}, para o operador resolver tudo sem tentativa e erro.
 */
@Order(0)
@RestControllerAdvice
class PackagingExceptionHandler {

    @ExceptionHandler(UnknownFinishedLotException.class)
    ProblemDetail handleUnknownLot(UnknownFinishedLotException ex) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, "finished_lot_not_found", ex.getMessage());
    }

    /**
     * 409 com quem liberou e quando (SAL-001-B).
     *
     * <p>Sobrescrever a liberação trocaria o responsável e a data, e a auditoria deixaria de saber quem
     * respondeu pelo lote. Quem recebeu a recusa precisa saber que já está liberado — e por quem —, não
     * só que a operação falhou.
     */
    @ExceptionHandler(LotAlreadyReleasedException.class)
    ProblemDetail handleAlreadyReleased(LotAlreadyReleasedException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "lot_already_released", ex.getMessage());
        problem.setProperty("releasedBy", ex.releasedBy().toString());
        problem.setProperty("releasedAt", ex.releasedAt().toString());
        return problem;
    }

    @ExceptionHandler(PackagingBlockedException.class)
    ProblemDetail handleBlocked(PackagingBlockedException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "packaging_blocked",
                "O envase não pôde ser reservado; há bloqueios.");
        List<Map<String, String>> blockers = ex.blockers().stream()
                .map(b -> Map.of("code", b.code(), "message", b.message()))
                .toList();
        problem.setProperty("blockers", blockers);
        return problem;
    }

    /**
     * Expedição acima do que o lote tem (TRC-001-D). Os três números acompanham a recusa: num
     * recall, a soma das expedições é o que diz quanto está na rua, e um destino com unidades
     * inventadas faria procurar caixas que nunca saíram.
     */
    @ExceptionHandler(ShipmentExceedsLotException.class)
    ProblemDetail handleShipmentExceedsLot(ShipmentExceedsLotException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "shipment_exceeds_lot",
                "A expedição sairia com mais unidades do que o lote tem.");
        problem.setProperty("shipment", Map.of(
                "lotUnits", ex.lotUnits(),
                "alreadyShipped", ex.alreadyShipped(),
                "requested", ex.requested(),
                "available", ex.available()));
        return problem;
    }

    /**
     * Priming sobre CO₂ que já atinge o alvo (PKG-002): o alvo e o residual acompanham o erro para
     * o cervejeiro decidir entre elevar o alvo, resfriar antes ou trocar de método.
     */
    /**
     * O alvo gera mais pressão do que a embalagem suporta (PKG-002-A).
     *
     * <p>409 e recusa, não alerta: alerta é lido no dia em que a linha está atrasada, e a consequência de
     * ignorá-lo é garrafa estourando. A resposta traz a pressão esperada, o limite e a temperatura em que
     * a conta foi feita — sem a temperatura, quem opera não sabe que estocar mais quente piora o número.
     */
    @ExceptionHandler(ContainerPressureExceededException.class)
    ProblemDetail handleContainerPressure(ContainerPressureExceededException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "container_pressure_exceeded",
                "O alvo de carbonatação gera " + ex.expectedPressureBar() + " bar a " + ex.referenceTempC()
                        + " °C, acima do limite de " + ex.maxPressureBar() + " bar desta embalagem. "
                        + "Reduza o alvo ou escolha uma embalagem que suporte a pressão.");
        problem.setProperty("expectedPressureBar", ex.expectedPressureBar());
        problem.setProperty("maxPressureBar", ex.maxPressureBar());
        problem.setProperty("referenceTempC", ex.referenceTempC());
        return problem;
    }

    @ExceptionHandler(OverCarbonationException.class)
    ProblemDetail handleOverCarbonation(OverCarbonationException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "over_carbonation",
                "A cerveja já tem o CO₂ alvo dissolvido; adicionar açúcar causaria sobrepressão.");
        problem.setProperty("carbonation", Map.<String, Object>of(
                "targetVolumes", ex.targetVolumes(),
                "residualVolumes", ex.residualVolumes()));
        return problem;
    }

    /**
     * O balanço de volume não fecha (PKG-003): as unidades declaradas contêm mais cerveja do que a
     * que saiu do tanque. Os três números vão no erro — adivinhar qual está errado seria inventar
     * produção.
     */
    @ExceptionHandler(VolumeBalanceException.class)
    ProblemDetail handleVolumeBalance(VolumeBalanceException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "volume_balance",
                "O balanço de volume não fecha: as unidades declaradas contêm mais cerveja do que saiu do tanque.");
        problem.setProperty("balance", Map.<String, Object>of(
                "inputVolumeLiters", ex.inputVolumeLiters(),
                "packagedVolumeLiters", ex.packagedVolumeLiters(),
                "rejectedVolumeLiters", ex.rejectedVolumeLiters(),
                "shortfallLiters", ex.shortfallLiters()));
        return problem;
    }

    /** A soma das execuções do lote não pode passar do que existiu no tanque (PKG-003). */
    @ExceptionHandler(BatchVolumeExceededException.class)
    ProblemDetail handleBatchVolume(BatchVolumeExceededException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "batch_volume_exceeded",
                "O envase tiraria do lote mais cerveja do que ele ainda tem.");
        problem.setProperty("batchVolume", Map.<String, Object>of(
                "batchVolumeLiters", ex.batchVolumeLiters(),
                "alreadyPackagedLiters", ex.alreadyPackagedLiters(),
                "remainingLiters", ex.remainingLiters(),
                "requestedLiters", ex.requestedLiters()));
        return problem;
    }

    /**
     * Rótulo incompleto (PKG-004): os campos faltantes vão separados por causa, porque a correção é
     * diferente — resolver a fonte do valor, ou acrescentar o campo ao layout.
     */
    @ExceptionHandler(LabelNotPrintableException.class)
    ProblemDetail handleLabelNotPrintable(LabelNotPrintableException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "label_not_printable",
                "O rótulo não pode ser impresso: há campo obrigatório faltando.");
        problem.setProperty("label", Map.<String, Object>of(
                "missingRequired", ex.missingRequired().stream().map(Enum::name).toList(),
                "requiredNotDrawn", ex.requiredNotDrawn().stream().map(Enum::name).toList()));
        return problem;
    }

    @ExceptionHandler(PackagingStockShortfallException.class)
    ProblemDetail handleShortfall(PackagingStockShortfallException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "insufficient_packaging_stock",
                "Embalagem insuficiente para o plano; nada foi reservado.");
        problem.setProperty("shortfall", Map.<String, Object>of(
                "containerId", ex.containerId().toString(),
                "requested", ex.requested(),
                "available", ex.available(),
                "unit", ex.unit()));
        return problem;
    }
}
