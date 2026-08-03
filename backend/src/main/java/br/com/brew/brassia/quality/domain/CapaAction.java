package br.com.brew.brassia.quality.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Ação corretiva ou preventiva, com dono e prazo. */
public final class CapaAction {

    private final UUID id;
    private final CapaActionKind kind;
    private final String description;
    private final String owner;
    private final LocalDate dueOn;
    private Instant completedAt;

    private CapaAction(UUID id, CapaActionKind kind, String description, String owner, LocalDate dueOn,
            Instant completedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Objects.requireNonNull(kind, "tipo da ação é obrigatório");
        this.description = Texts.require(description, "descrição da ação", 1000);
        this.owner = Texts.require(owner, "responsável pela ação", 120);
        this.dueOn = Objects.requireNonNull(dueOn, "prazo da ação é obrigatório");
        this.completedAt = completedAt;
    }

    public static CapaAction plan(CapaActionKind kind, String description, String owner, LocalDate dueOn) {
        return new CapaAction(UUID.randomUUID(), kind, description, owner, dueOn, null);
    }

    public static CapaAction reconstitute(UUID id, CapaActionKind kind, String description, String owner,
            LocalDate dueOn, Instant completedAt) {
        return new CapaAction(id, kind, description, owner, dueOn, completedAt);
    }

    public void complete(Instant at) {
        if (completedAt != null) {
            throw new IllegalStateException("ação já concluída");
        }
        this.completedAt = Objects.requireNonNull(at, "instante da conclusão");
    }

    public boolean completed() {
        return completedAt != null;
    }

    /** Atrasada é derivado do prazo e da data — não existe coluna que envelhece sozinha. */
    public boolean overdue(LocalDate today) {
        return !completed() && dueOn.isBefore(Objects.requireNonNull(today, "data de referência"));
    }

    public UUID id() {
        return id;
    }

    public CapaActionKind kind() {
        return kind;
    }

    public String description() {
        return description;
    }

    public String owner() {
        return owner;
    }

    public LocalDate dueOn() {
        return dueOn;
    }

    public Instant completedAt() {
        return completedAt;
    }
}
