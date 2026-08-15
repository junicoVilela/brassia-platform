package br.com.brew.brassia.packaging.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A liberação de um lote acabado para venda (SAL-001-B).
 *
 * <p><strong>É ato assinado, e não dedução.</strong> A alternativa era derivar "liberado" de "não há não
 * conformidade nem desvio em aberto", e ela foi recusada por dois motivos: um lote <em>nunca medido</em>
 * passaria como liberado — {@code BatchQualityLookup.unmeasured()} é verdadeiro e nada reclama —, e a
 * auditoria que pergunta "quem liberou este lote?" receberia "o sistema deduziu". Em alimento, liberação
 * é decisão registrada, com nome e data.
 *
 * <p><strong>Mora em packaging porque é estado do lote, embora a alçada seja da qualidade.</strong> Se o
 * registro morasse em {@code quality}, a expedição — que já mora aqui — precisaria consultá-lo para
 * recusar lote não liberado, e isso fecharia ciclo entre os dois módulos (ADR-0016).
 *
 * <p><strong>Não se revoga.</strong> Lote liberado que depois se mostra problemático é caso de quarentena
 * ou recall, que já existem, alcançam por herança e deixam rastro do porquê. Apagar a liberação faria
 * sumir o fato de que alguém a assinou — que é o que a investigação precisa saber.
 */
public record LotRelease(UUID finishedLotId, UUID breweryId, UUID releasedBy, Instant releasedAt,
        String note) {

    private static final int MAX_NOTE = 500;

    public LotRelease {
        Objects.requireNonNull(finishedLotId, "lote acabado");
        Objects.requireNonNull(breweryId, "cervejaria");
        Objects.requireNonNull(releasedBy, "quem liberou");
        Objects.requireNonNull(releasedAt, "quando liberou");
        if (note != null) {
            note = note.strip();
            if (note.isEmpty()) {
                // Vazio vira ausente: guardar string em branco faria a tela mostrar uma observação que
                // não existe.
                note = null;
            } else if (note.length() > MAX_NOTE) {
                throw new IllegalArgumentException("a observação passa de " + MAX_NOTE + " caracteres");
            }
        }
    }

    public Optional<String> observation() {
        return Optional.ofNullable(note);
    }
}
