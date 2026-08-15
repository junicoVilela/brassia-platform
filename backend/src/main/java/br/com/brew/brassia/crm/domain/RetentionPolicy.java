package br.com.brew.brassia.crm.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Por quanto tempo a cervejaria guarda o dado pessoal de um contato depois do último relacionamento
 * (CRM-001).
 *
 * <p><strong>O número é da casa, não do código</strong> — mesmo espírito da {@code CapaPolicy} e das
 * demais políticas da PRM-001. Retenção é decisão jurídica de cada cervejaria, e um padrão embutido
 * seria o código respondendo por ela uma pergunta que ele não tem como responder.
 *
 * <p><strong>Sem política, nada expira.</strong> Não anonimizar por falta de decisão é reversível: um dia
 * alguém define o prazo e a varredura alcança o atrasado. Anonimizar cedo demais não é — o dado não
 * volta, e junto com ele vai embora o contato que a cervejaria talvez precisasse para uma convocação de
 * recall.
 */
public final class RetentionPolicy {

    private final UUID breweryId;
    private final Integer daysAfterLastInteraction;

    private RetentionPolicy(UUID breweryId, Integer daysAfterLastInteraction) {
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.daysAfterLastInteraction = daysAfterLastInteraction;
    }

    public static RetentionPolicy none(UUID breweryId) {
        return new RetentionPolicy(breweryId, null);
    }

    public static RetentionPolicy of(UUID breweryId, int daysAfterLastInteraction) {
        if (daysAfterLastInteraction <= 0) {
            throw new IllegalArgumentException("o prazo de retenção deve ser positivo");
        }
        return new RetentionPolicy(breweryId, daysAfterLastInteraction);
    }

    /**
     * A data em que o contato passa a ser anonimizável, contada do último relacionamento.
     *
     * <p>Vazio quando não há política: sem prazo definido não existe data, e devolver "hoje" ou "nunca"
     * seria inventar a decisão que falta.
     */
    public Optional<LocalDate> anonymizeOn(LocalDate lastInteraction) {
        Objects.requireNonNull(lastInteraction, "último relacionamento");
        return Optional.ofNullable(daysAfterLastInteraction).map(lastInteraction::plusDays);
    }

    /**
     * Se o contato já passou do prazo em {@code today}.
     *
     * <p>O dia exato do vencimento ainda <strong>não</strong> vence: o prazo é "guardar por N dias", e
     * cortar no próprio dia entregaria N−1. A diferença é de um dia e aparece em auditoria.
     */
    public boolean dueFor(LocalDate lastInteraction, LocalDate today) {
        Objects.requireNonNull(today, "hoje");
        return anonymizeOn(lastInteraction).map(today::isAfter).orElse(false);
    }

    public UUID breweryId() {
        return breweryId;
    }

    public Optional<Integer> daysAfterLastInteraction() {
        return Optional.ofNullable(daysAfterLastInteraction);
    }
}
