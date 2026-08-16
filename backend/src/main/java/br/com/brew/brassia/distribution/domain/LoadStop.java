package br.com.brew.brassia.distribution.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma parada do roteiro: um cliente, uma janela, e o que desce ali.
 *
 * <p><strong>A sequência é da parada, e não da ordem em que ela foi digitada.</strong> Duas paradas na
 * mesma posição é ambiguidade que o motorista resolve inventando — e a rota que ele inventar não é a que
 * a janela combinada pressupõe.
 */
public final class LoadStop {

    private final UUID id;
    private final UUID customerId;
    private final String customerName;
    private int sequence;
    private DeliveryWindow window;
    private final List<UUID> containerIds = new ArrayList<>();

    private LoadStop(UUID id, UUID customerId, String customerName, int sequence,
            DeliveryWindow window) {
        this.id = Objects.requireNonNull(id);
        this.customerId = Objects.requireNonNull(customerId, "cliente");
        this.customerName = Objects.requireNonNull(customerName, "nome do cliente");
        this.sequence = requireSequence(sequence);
        this.window = window;
    }

    public static LoadStop create(UUID id, UUID customerId, String customerName, int sequence,
            DeliveryWindow window) {
        return new LoadStop(id, customerId, customerName, sequence, window);
    }

    public void add(UUID containerId) {
        if (containerIds.contains(containerId)) {
            // O mesmo vasilhame duas vezes na mesma parada é engano de leitura de etiqueta, e viraria
            // uma entrega a mais do que saiu do depósito.
            throw new IllegalArgumentException("o vasilhame já está nesta parada");
        }
        containerIds.add(containerId);
    }

    public void remove(UUID containerId) {
        containerIds.remove(containerId);
    }

    public void reorder(int sequence) {
        this.sequence = requireSequence(sequence);
    }

    public void reschedule(DeliveryWindow window) {
        this.window = window;
    }

    public UUID id() {
        return id;
    }

    public UUID customerId() {
        return customerId;
    }

    public String customerName() {
        return customerName;
    }

    public int sequence() {
        return sequence;
    }

    public java.util.Optional<DeliveryWindow> window() {
        return java.util.Optional.ofNullable(window);
    }

    public List<UUID> containerIds() {
        return List.copyOf(containerIds);
    }

    public boolean isEmpty() {
        return containerIds.isEmpty();
    }

    private static int requireSequence(int sequence) {
        if (sequence < 1) {
            throw new IllegalArgumentException("a sequência começa em 1");
        }
        return sequence;
    }
}
