package br.com.brew.brassia.distribution.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Uma carga: o que sai, em que ordem, para quem, e com quem (LOG-001).
 *
 * <p><strong>Planejar e liberar são atos de pessoas diferentes.</strong> É a separação de deveres da
 * história, e ela não é desconfiança do motorista: a conferência serve para encontrar o erro de quem
 * montou, e quem montou relê o próprio trabalho enxergando o que quis colocar. Uma conferência feita pela
 * mesma pessoa custa o mesmo tempo e não encontra nada.
 *
 * <p><strong>Depois de liberada, a carga congela.</strong> Acrescentar um keg numa carga já conferida
 * desfaz a conferência sem que ninguém perceba — e o papel que o motorista leva deixa de descrever o que
 * está no caminhão. Mudou? Volta para o planejamento, e alguém confere de novo.
 */
public final class Load {

    private final UUID id;
    private final UUID breweryId;
    private final String code;
    private final LocalDate scheduledFor;
    private final BigDecimal capacityLiters;
    private final UUID plannedBy;
    private UUID driverId;
    private String vehicle;
    private LoadStatus status;
    private UUID releasedBy;
    private Instant releasedAt;
    private final List<LoadStop> stops = new ArrayList<>();

    private Load(UUID id, UUID breweryId, String code, LocalDate scheduledFor,
            BigDecimal capacityLiters, UUID plannedBy, UUID driverId, String vehicle,
            LoadStatus status, UUID releasedBy, Instant releasedAt) {
        this.id = Objects.requireNonNull(id);
        this.breweryId = Objects.requireNonNull(breweryId);
        this.code = requireCode(code);
        this.scheduledFor = Objects.requireNonNull(scheduledFor, "data");
        this.capacityLiters = requirePositive(capacityLiters);
        this.plannedBy = Objects.requireNonNull(plannedBy, "quem planejou");
        this.driverId = driverId;
        this.vehicle = vehicle;
        this.status = Objects.requireNonNull(status);
        this.releasedBy = releasedBy;
        this.releasedAt = releasedAt;
    }

    public static Load plan(UUID id, UUID breweryId, String code, LocalDate scheduledFor,
            BigDecimal capacityLiters, UUID plannedBy) {
        return new Load(id, breweryId, code, scheduledFor, capacityLiters, plannedBy, null, null,
                LoadStatus.PLANNED, null, null);
    }

    public static Load reconstitute(UUID id, UUID breweryId, String code, LocalDate scheduledFor,
            BigDecimal capacityLiters, UUID plannedBy, UUID driverId, String vehicle,
            LoadStatus status, UUID releasedBy, Instant releasedAt) {
        return new Load(id, breweryId, code, scheduledFor, capacityLiters, plannedBy, driverId,
                vehicle, status, releasedBy, releasedAt);
    }

    // --- montagem ---

    public void addStop(LoadStop stop) {
        requireOpen();
        if (stops.stream().anyMatch(s -> s.sequence() == stop.sequence())) {
            // Duas paradas na mesma posição é ambiguidade que o motorista resolve inventando.
            throw new IllegalArgumentException("já existe parada na posição " + stop.sequence());
        }
        stops.add(stop);
    }

    public void removeStop(UUID stopId) {
        requireOpen();
        stops.removeIf(s -> s.id().equals(stopId));
    }

    /**
     * Põe um vasilhame numa parada.
     *
     * <p>O volume que ele carrega entra na conta da capacidade — que é do veículo, e não de cada parada:
     * o caminhão é um só, e o limite é o dele.
     */
    public void load(UUID stopId, UUID containerId, BigDecimal volumeLiters) {
        requireOpen();
        var stop = stop(stopId);
        if (containsContainer(containerId)) {
            // O mesmo keg em duas paradas é entrega prometida duas vezes, e uma delas vai faltar.
            throw new IllegalArgumentException("o vasilhame já está nesta carga");
        }
        var total = loadedLiters().add(volumeLiters);
        if (total.compareTo(capacityLiters) > 0) {
            throw new LoadCapacityExceededException(total.subtract(capacityLiters));
        }
        stop.add(containerId);
        volumes.put(containerId, volumeLiters);
    }

    public void unload(UUID containerId) {
        requireOpen();
        stops.forEach(s -> s.remove(containerId));
        volumes.remove(containerId);
    }

    public void assign(UUID driverId, String vehicle) {
        requireOpen();
        this.driverId = Objects.requireNonNull(driverId, "responsável");
        this.vehicle = vehicle;
    }

    // --- ciclo ---

    /**
     * Libera a carga para sair, por <strong>outra pessoa</strong>.
     *
     * <p>Sem responsável não sai: uma carga na rua sem nome é uma carga sem a quem perguntar quando o
     * cliente liga dizendo que não chegou.
     */
    public void release(UUID releasedBy, Instant at) {
        if (status != LoadStatus.PLANNED) {
            throw new IllegalLoadTransitionException(status, LoadStatus.RELEASED);
        }
        if (plannedBy.equals(releasedBy)) {
            throw new SeparationOfDutiesException();
        }
        if (stops.isEmpty() || stops.stream().allMatch(LoadStop::isEmpty)) {
            // Uma carga vazia liberada vira um caminhão que sai por engano, e a rota do dia some.
            throw new IllegalArgumentException("uma carga sem nada dentro não sai");
        }
        if (driverId == null) {
            throw new IllegalArgumentException("a carga precisa de um responsável antes de sair");
        }
        this.status = LoadStatus.RELEASED;
        this.releasedBy = Objects.requireNonNull(releasedBy);
        this.releasedAt = Objects.requireNonNull(at);
    }

    public void depart() {
        transition(LoadStatus.RELEASED, LoadStatus.IN_ROUTE);
    }

    public void close() {
        transition(LoadStatus.IN_ROUTE, LoadStatus.CLOSED);
    }

    /**
     * Volta para o planejamento — e a liberação <strong>cai junto</strong>.
     *
     * <p>Manter a conferência de pé depois de a carga mudar seria pior que não ter conferência: o papel
     * diria que alguém olhou aquilo, e ninguém olhou.
     */
    public void reopen() {
        if (status != LoadStatus.RELEASED) {
            throw new IllegalLoadTransitionException(status, LoadStatus.PLANNED);
        }
        this.status = LoadStatus.PLANNED;
        this.releasedBy = null;
        this.releasedAt = null;
    }

    public void cancel() {
        if (status == LoadStatus.CLOSED || status == LoadStatus.CANCELLED) {
            throw new IllegalLoadTransitionException(status, LoadStatus.CANCELLED);
        }
        this.status = LoadStatus.CANCELLED;
    }

    // --- leitura ---

    /** As paradas na ordem do roteiro, e não na ordem em que foram digitadas. */
    public List<LoadStop> route() {
        return stops.stream().sorted(Comparator.comparingInt(LoadStop::sequence)).toList();
    }

    public BigDecimal loadedLiters() {
        return volumes.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal remainingLiters() {
        return capacityLiters.subtract(loadedLiters());
    }

    public boolean containsContainer(UUID containerId) {
        return stops.stream().anyMatch(s -> s.containerIds().contains(containerId));
    }

    public List<UUID> allContainers() {
        return stops.stream().flatMap(s -> s.containerIds().stream()).toList();
    }

    /** As sequências usadas, para a tela sugerir a próxima sem colidir. */
    public int nextSequence() {
        return stops.stream().mapToInt(LoadStop::sequence).max().orElse(0) + 1;
    }

    public boolean isFrozen() {
        return status != LoadStatus.PLANNED;
    }

    private void transition(LoadStatus from, LoadStatus to) {
        if (status != from) {
            throw new IllegalLoadTransitionException(status, to);
        }
        this.status = to;
    }

    private void requireOpen() {
        if (isFrozen()) {
            // Acrescentar um keg numa carga já conferida desfaz a conferência sem ninguém perceber.
            throw IllegalLoadTransitionException.frozen(status);
        }
    }

    private LoadStop stop(UUID stopId) {
        return stops.stream().filter(s -> s.id().equals(stopId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("parada não encontrada"));
    }

    private final java.util.Map<UUID, BigDecimal> volumes = new java.util.LinkedHashMap<>();

    /**
     * Recompõe uma parada vinda do banco.
     *
     * <p>Não passa por {@link #requireOpen()} de propósito: uma carga já liberada precisa ser LIDA, e o
     * congelamento vale para quem quer mudá-la — não para quem a reconstrói. As demais invariantes
     * continuam valendo, e uma linha inconsistente no banco aparece aqui em vez de na rua.
     */
    public void restoreStop(LoadStop stop) {
        if (stops.stream().anyMatch(s -> s.sequence() == stop.sequence())) {
            throw new IllegalArgumentException("já existe parada na posição " + stop.sequence());
        }
        stops.add(stop);
    }

    /** Recompõe um item vindo do banco, com o volume que ficou gravado na linha. */
    public void restoreItem(UUID stopId, UUID containerId, BigDecimal volumeLiters) {
        stop(stopId).add(containerId);
        volumes.put(containerId, volumeLiters);
    }

    public BigDecimal volumeOf(UUID containerId) {
        return volumes.getOrDefault(containerId, BigDecimal.ZERO);
    }

    public UUID id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public String code() {
        return code;
    }

    public LocalDate scheduledFor() {
        return scheduledFor;
    }

    public BigDecimal capacityLiters() {
        return capacityLiters;
    }

    public UUID plannedBy() {
        return plannedBy;
    }

    public Optional<UUID> driverId() {
        return Optional.ofNullable(driverId);
    }

    public Optional<String> vehicle() {
        return Optional.ofNullable(vehicle);
    }

    public LoadStatus status() {
        return status;
    }

    public Optional<UUID> releasedBy() {
        return Optional.ofNullable(releasedBy);
    }

    public Optional<Instant> releasedAt() {
        return Optional.ofNullable(releasedAt);
    }

    /** Clientes distintos atendidos — o número que a tela mostra ao lado do "quantas paradas". */
    public int customerCount() {
        return new HashSet<>(stops.stream().map(LoadStop::customerId).toList()).size();
    }

    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("a carga precisa de um código");
        }
        return code.trim();
    }

    private static BigDecimal requirePositive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("a capacidade do veículo deve ser positiva");
        }
        return value;
    }
}
