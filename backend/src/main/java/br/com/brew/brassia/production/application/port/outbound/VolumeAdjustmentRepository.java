package br.com.brew.brassia.production.application.port.outbound;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Ajustes de volume envasável (DEC-BLD-003).
 *
 * <p>O saldo não é guardado: ele é a soma dos ajustes sobre o volume que a transferência determinou.
 * Guardá-lo criaria um segundo número que diverge do primeiro no dia em que alguém corrigir a
 * transferência — e aí não haveria como saber qual dos dois descreve o tanque.
 */
public interface VolumeAdjustmentRepository {

    /**
     * Grava o ajuste.
     *
     * @return {@code false} quando este lote já foi ajustado por esta operação — quem decide é a
     *         restrição única, e não uma leitura anterior que duas requisições simultâneas atravessariam
     */
    boolean insert(UUID breweryId, UUID batchId, BigDecimal deltaLiters, String source, UUID sourceRef,
            UUID actorId, Instant occurredAt);

    /** Soma dos ajustes do lote; zero quando não houve nenhum. */
    BigDecimal totalFor(UUID breweryId, UUID batchId);
}
