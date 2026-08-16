package br.com.brew.brassia.container.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Uma etiqueta colada num contêiner: QR, código de barras ou RFID.
 *
 * <p><strong>Ler um código identifica, e não autoriza.</strong> Este objeto responde "qual keg é esta" e
 * nada mais — não carrega permissão, cervejaria nem token. É o critério transversal da sprint escrito em
 * código: quem escaneia continua precisando de alçada para fazer qualquer coisa com o que encontrou, e um
 * código fotografado no bar não vira chave de nada.
 *
 * <p><strong>Etiqueta aposentada não volta a apontar para nada.</strong> Reaproveitar o valor faria um
 * registro antigo — uma entrega de seis meses atrás — passar a resolver para outro contêiner, e a
 * genealogia que a CON-002 vai construir sobre isso ficaria errada sem ninguém perceber.
 */
public final class ContainerIdentifier {

    private final UUID id;
    private final UUID containerId;
    private final String value;
    private final IdentifierTechnology technology;
    private final Instant assignedAt;
    private Instant retiredAt;

    private ContainerIdentifier(UUID id, UUID containerId, String value,
            IdentifierTechnology technology, Instant assignedAt, Instant retiredAt) {
        this.id = Objects.requireNonNull(id);
        this.containerId = Objects.requireNonNull(containerId);
        this.value = requireValue(value);
        this.technology = Objects.requireNonNull(technology);
        this.assignedAt = Objects.requireNonNull(assignedAt);
        this.retiredAt = retiredAt;
    }

    public static ContainerIdentifier assign(UUID id, UUID containerId, String value,
            IdentifierTechnology technology, Instant at) {
        return new ContainerIdentifier(id, containerId, value, technology, at, null);
    }

    public static ContainerIdentifier reconstitute(UUID id, UUID containerId, String value,
            IdentifierTechnology technology, Instant assignedAt, Instant retiredAt) {
        return new ContainerIdentifier(id, containerId, value, technology, assignedAt, retiredAt);
    }

    /**
     * A etiqueta saiu de circulação — descolou, foi substituída, o keg foi reetiquetado.
     *
     * <p>Aposentar não apaga: o registro de que aquele valor um dia apontou para este contêiner é o que
     * permite entender uma leitura antiga.
     */
    public void retire(Instant at) {
        if (retiredAt == null) {
            this.retiredAt = Objects.requireNonNull(at);
        }
    }

    /** Só a etiqueta ativa resolve. A aposentada é história, e não um apontador. */
    public boolean resolvesAt(Instant moment) {
        return retiredAt == null || moment.isBefore(retiredAt);
    }

    public boolean isActive() {
        return retiredAt == null;
    }

    public UUID id() {
        return id;
    }

    public UUID containerId() {
        return containerId;
    }

    public String value() {
        return value;
    }

    public IdentifierTechnology technology() {
        return technology;
    }

    public Instant assignedAt() {
        return assignedAt;
    }

    public Optional<Instant> retiredAt() {
        return Optional.ofNullable(retiredAt);
    }

    private static String requireValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("a etiqueta precisa de um valor");
        }
        return value.trim();
    }
}
