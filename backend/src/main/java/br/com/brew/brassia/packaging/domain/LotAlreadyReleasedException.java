package br.com.brew.brassia.packaging.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * O lote já tinha sido liberado (SAL-001-B).
 *
 * <p>Recusar em vez de sobrescrever: uma segunda liberação trocaria o responsável e a data, e a auditoria
 * deixaria de saber quem respondeu pelo lote. A garantia de verdade é a chave primária em
 * {@code finished_lot_id}; esta exceção existe para a resposta dizer <em>quem</em> liberou e <em>quando</em>,
 * em vez de um erro de banco.
 */
public class LotAlreadyReleasedException extends RuntimeException {

    private final UUID releasedBy;
    private final Instant releasedAt;

    public LotAlreadyReleasedException(UUID releasedBy, Instant releasedAt) {
        super("este lote já foi liberado em " + releasedAt);
        this.releasedBy = releasedBy;
        this.releasedAt = releasedAt;
    }

    public UUID releasedBy() {
        return releasedBy;
    }

    public Instant releasedAt() {
        return releasedAt;
    }
}
