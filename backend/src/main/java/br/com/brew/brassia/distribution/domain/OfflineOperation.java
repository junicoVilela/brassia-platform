package br.com.brew.brassia.distribution.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Uma operação registrada no aparelho e enviada quando houve sinal (MOB-001).
 *
 * <p><strong>O identificador é do cliente, e não do servidor.</strong> É o que torna o reenvio seguro: o
 * aparelho precisa poder nomear a operação <em>antes</em> de o servidor saber que ela existe — offline
 * não há como pedir um número. Sem isso, o entregador que aperta "sincronizar" duas vezes num sinal ruim
 * registra duas entregas para o mesmo cliente, e o estoque perde a conta.
 *
 * <p><strong>Duas horas, e as duas importam.</strong> {@code occurredAt} é do aparelho — é quando a
 * cerveja desceu. {@code receivedAt} é do servidor — é quando a informação chegou. Usar a hora do
 * servidor para o fato colocaria toda entrega feita offline no momento em que o caminhão voltou ao
 * depósito, e ninguém entregou nada no pátio às seis da tarde.
 *
 * <p><strong>Conflito não se resolve sozinho.</strong> Quando o servidor mudou embaixo — a parada já tem
 * prova registrada pelo escritório —, a operação do aparelho não sobrescreve nem some: ela fica marcada,
 * com o motivo, para alguém decidir. Último-a-escrever-ganha descartaria em silêncio o registro de quem
 * estava lá.
 */
public final class OfflineOperation {

    private final UUID clientOperationId;
    private final UUID deviceId;
    private final UUID loadId;
    private final UUID stopId;
    private final Instant occurredAt;
    private final Instant receivedAt;
    private final int sequence;
    private SyncStatus status;
    private UUID resultId;
    private String reason;

    private OfflineOperation(UUID clientOperationId, UUID deviceId, UUID loadId, UUID stopId,
            Instant occurredAt, Instant receivedAt, int sequence, SyncStatus status, UUID resultId,
            String reason) {
        this.clientOperationId = Objects.requireNonNull(clientOperationId, "id da operação");
        this.deviceId = Objects.requireNonNull(deviceId, "aparelho");
        this.loadId = Objects.requireNonNull(loadId, "carga");
        this.stopId = Objects.requireNonNull(stopId, "parada");
        this.occurredAt = Objects.requireNonNull(occurredAt, "quando aconteceu");
        this.receivedAt = Objects.requireNonNull(receivedAt, "quando chegou");
        if (sequence < 1) {
            // A ordem é a do aparelho: aplicar fora dela entregaria antes de despachar.
            throw new IllegalArgumentException("a sequência do aparelho começa em 1");
        }
        this.sequence = sequence;
        this.status = Objects.requireNonNull(status);
        this.resultId = resultId;
        this.reason = reason;
    }

    public static OfflineOperation received(UUID clientOperationId, UUID deviceId, UUID loadId,
            UUID stopId, Instant occurredAt, Instant receivedAt, int sequence) {
        return new OfflineOperation(clientOperationId, deviceId, loadId, stopId, occurredAt,
                receivedAt, sequence, SyncStatus.APPLIED, null, null);
    }

    public static OfflineOperation reconstitute(UUID clientOperationId, UUID deviceId, UUID loadId,
            UUID stopId, Instant occurredAt, Instant receivedAt, int sequence, SyncStatus status,
            UUID resultId, String reason) {
        return new OfflineOperation(clientOperationId, deviceId, loadId, stopId, occurredAt,
                receivedAt, sequence, status, resultId, reason);
    }

    public void applied(UUID resultId) {
        this.status = SyncStatus.APPLIED;
        this.resultId = Objects.requireNonNull(resultId);
    }

    /** O reenvio devolve o mesmo resultado da primeira vez — e não cria outro. */
    public void duplicateOf(UUID resultId) {
        this.status = SyncStatus.DUPLICATE;
        this.resultId = resultId;
    }

    public void conflicted(String reason) {
        requireReason(reason);
        this.status = SyncStatus.CONFLICTED;
        this.reason = reason.trim();
    }

    public void rejected(String reason) {
        requireReason(reason);
        this.status = SyncStatus.REJECTED;
        this.reason = reason.trim();
    }

    /**
     * O relógio do aparelho ficou à frente do servidor.
     *
     * <p>Não invalida a operação — o celular do entregador não se ajusta sozinho no subsolo do bar, e
     * recusar a entrega por causa disso perderia o registro do que aconteceu de verdade. Mas fica
     * marcado, porque uma entrega com hora no futuro confunde qualquer leitura de linha do tempo.
     */
    public boolean clockAhead() {
        return occurredAt.isAfter(receivedAt);
    }

    public boolean isApplied() {
        return status == SyncStatus.APPLIED;
    }

    /** Precisa de gente: nem aplicou, nem foi duplicata. */
    public boolean needsDecision() {
        return status == SyncStatus.CONFLICTED;
    }

    public UUID clientOperationId() {
        return clientOperationId;
    }

    public UUID deviceId() {
        return deviceId;
    }

    public UUID loadId() {
        return loadId;
    }

    public UUID stopId() {
        return stopId;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public Instant receivedAt() {
        return receivedAt;
    }

    public int sequence() {
        return sequence;
    }

    public SyncStatus status() {
        return status;
    }

    public Optional<UUID> resultId() {
        return Optional.ofNullable(resultId);
    }

    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            // Uma recusa sem motivo deixa o entregador com um item vermelho na tela e nada a fazer.
            throw new IllegalArgumentException("a recusa precisa de motivo");
        }
    }
}
