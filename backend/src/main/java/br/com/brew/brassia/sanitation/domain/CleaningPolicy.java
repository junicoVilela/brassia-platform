package br.com.brew.brassia.sanitation.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Política de limpeza da cervejaria (PRM-001): por quantas horas uma liberação de CIP cobre o
 * equipamento sem novo uso.
 *
 * <p>O prazo depende do POP e da casa, e é por isso que ele é <strong>parâmetro, não constante</strong>
 * — fecha o débito PKG-001-A, que existia justamente porque inventar o número criaria regra de
 * negócio sem fonte.
 *
 * <p><strong>A ausência do parâmetro preserva o comportamento de hoje:</strong> sem prazo
 * configurado a liberação não expira por tempo, exatamente como antes desta história. Ganhar o
 * campo não faz a plataforma passar a inventar número.
 */
public final class CleaningPolicy {

    private final UUID breweryId;
    private Integer validityHours;
    private final long version;

    private CleaningPolicy(UUID breweryId, Integer validityHours, long version) {
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.validityHours = requireHours(validityHours);
        this.version = version;
    }

    /** Política vazia: nenhuma validade por tempo, que é o comportamento anterior à PRM-001. */
    public static CleaningPolicy none(UUID breweryId) {
        return new CleaningPolicy(breweryId, null, 0);
    }

    public static CleaningPolicy reconstitute(UUID breweryId, Integer validityHours, long version) {
        return new CleaningPolicy(breweryId, validityHours, version);
    }

    /** @param validityHours {@code null} remove o prazo e volta a não expirar por tempo */
    public void setValidityHours(Integer validityHours) {
        this.validityHours = requireHours(validityHours);
    }

    /**
     * Se a liberação feita em {@code releasedAt} ainda cobre {@code at}.
     *
     * <p>Sem prazo configurado, sempre cobre — a decisão de não expirar é explícita, não um
     * esquecimento.
     */
    public boolean covers(Instant releasedAt, Instant at) {
        Objects.requireNonNull(releasedAt, "liberação");
        Objects.requireNonNull(at, "instante de referência");
        if (validityHours == null) {
            return true;
        }
        return !releasedAt.plus(Duration.ofHours(validityHours)).isBefore(at);
    }

    public UUID breweryId() {
        return breweryId;
    }

    public Optional<Integer> validityHours() {
        return Optional.ofNullable(validityHours);
    }

    public long version() {
        return version;
    }

    private static Integer requireHours(Integer hours) {
        if (hours == null) {
            return null;
        }
        if (hours <= 0) {
            throw new IllegalArgumentException("a validade da limpeza deve ser positiva");
        }
        if (hours > 24 * 365) {
            throw new IllegalArgumentException("validade acima de um ano não é prazo de CIP");
        }
        return hours;
    }
}
