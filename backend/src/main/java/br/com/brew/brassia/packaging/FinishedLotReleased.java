package br.com.brew.brassia.packaging;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Um lote acabado foi liberado pela qualidade (INT-008).
 *
 * <p>É o gatilho para o e-commerce publicar o produto: antes da liberação não há o que vender, e depois
 * dela há — com validade conhecida. Publicar antes faria a loja anunciar cerveja que a qualidade ainda
 * não aprovou, que é o erro que a SAL-001-B existe para impedir do lado de dentro.
 *
 * <p>A validade viaja junto porque é ela que decide até quando a oferta faz sentido lá fora.
 */
public record FinishedLotReleased(UUID breweryId, UUID finishedLotId, String code, String batchCode,
        int units, LocalDate bestBefore, Instant occurredAt) {}
