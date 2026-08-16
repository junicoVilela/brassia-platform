package br.com.brew.brassia.container.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Onde o vasilhame esteve, e desde quando (CON-002).
 *
 * <p><strong>Também é histórico, e pela mesma razão do conteúdo.</strong> "Onde está" é a última linha;
 * "por onde andou" é o que responde quantos dias ele ficou parado num cliente — a conta que a CON-003 vai
 * fazer para cobrar depósito e caçar atraso.
 *
 * <p><strong>Sem posição nunca significa "sumiu".</strong> Significa que ninguém registrou — e a
 * diferença importa: um keg sem histórico é falha de processo, e não perda de ativo.
 *
 * @param place  texto livre de propósito nesta fatia: o depósito, o nome do bar. O vínculo com o cliente
 *               de verdade chega com a entrega (LOG-002), que é quem sabe a quem entregou
 */
public record ContainerLocation(UUID id, UUID containerId, LocationKind kind, String place,
        Instant recordedAt) {

    public ContainerLocation {
        Objects.requireNonNull(id);
        Objects.requireNonNull(containerId);
        Objects.requireNonNull(kind, "tipo de posição");
        Objects.requireNonNull(recordedAt);
    }

    public static ContainerLocation at(UUID containerId, LocationKind kind, String place, Instant at) {
        return new ContainerLocation(UUID.randomUUID(), containerId, kind,
                place == null || place.isBlank() ? null : place.trim(), at);
    }
}
