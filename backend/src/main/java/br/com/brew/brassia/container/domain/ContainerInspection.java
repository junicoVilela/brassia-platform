package br.com.brew.brassia.container.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A última inspeção do vasilhame, com a data até quando ela vale.
 *
 * <p><strong>A validade é registrada, e não calculada.</strong> A periodicidade de inspeção de vaso de
 * pressão vem de norma e do tipo do contêiner, e inventar aqui um intervalo padrão — "cinco anos" — faria
 * o sistema afirmar conformidade que ninguém verificou. Quem inspeciona informa até quando vale
 * (DUV-CON-001).
 */
public record ContainerInspection(Instant performedAt, Instant validUntil, UUID inspectedBy,
        String note) {

    public ContainerInspection {
        Objects.requireNonNull(performedAt, "data da inspeção");
        Objects.requireNonNull(validUntil, "validade da inspeção");
        Objects.requireNonNull(inspectedBy, "quem inspecionou");
        if (!validUntil.isAfter(performedAt)) {
            // Uma inspeção que já nasce vencida é engano de digitação, e o operador só descobriria
            // quando a enchedeira recusasse o keg.
            throw new IllegalArgumentException("a validade da inspeção precisa ser posterior à inspeção");
        }
    }

    public boolean validAt(Instant moment) {
        return moment.isBefore(validUntil);
    }
}
