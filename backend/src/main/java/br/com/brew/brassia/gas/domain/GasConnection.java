package br.com.brew.brassia.gas.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Conexão de gás (GAS-001): cilindro → regulador → (manifold) → ponto de uso.
 *
 * <p>A conexão nasce em {@link ConnectionStatus#PENDING_TEST} e <strong>não serve</strong> antes de
 * um teste de vazamento aprovado — montar a linha não é o mesmo que liberá-la.
 *
 * <p>O teto de pressão da rede é o menor limite entre os componentes e fica congelado na conexão:
 * trocar depois o cadastro do regulador não reescreve o que a linha montada suportava. Leitura
 * acima desse teto é sobrepressão: a medição é preservada e a conexão vai para
 * {@link ConnectionStatus#BLOCKED}, exigindo intervenção humana para voltar a servir.
 */
public final class GasConnection {

    private final UUID id;
    private final UUID breweryId;
    private final UUID cylinderId;
    private final UUID regulatorId;
    private final UUID manifoldId;
    private final UUID pointOfUseEquipmentId;
    private final BigDecimal workingPressureBar;
    private final BigDecimal networkMaxPressureBar;
    private ConnectionStatus status;
    private final Instant connectedAt;
    private final UUID connectedBy;
    private LeakTest leakTest;
    private Instant disconnectedAt;
    private String disconnectReason;
    private final long version;

    private GasConnection(UUID id, UUID breweryId, UUID cylinderId, UUID regulatorId, UUID manifoldId,
            UUID pointOfUseEquipmentId, BigDecimal workingPressureBar, BigDecimal networkMaxPressureBar,
            ConnectionStatus status, Instant connectedAt, UUID connectedBy, LeakTest leakTest,
            Instant disconnectedAt, String disconnectReason, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.cylinderId = Objects.requireNonNull(cylinderId, "cilindro é obrigatório");
        this.regulatorId = Objects.requireNonNull(regulatorId, "regulador é obrigatório");
        this.manifoldId = manifoldId;
        this.pointOfUseEquipmentId = Objects.requireNonNull(pointOfUseEquipmentId, "ponto de uso é obrigatório");
        this.workingPressureBar = requirePositive(workingPressureBar, "pressão de trabalho");
        this.networkMaxPressureBar = requirePositive(networkMaxPressureBar, "pressão máxima da rede");
        if (this.workingPressureBar.compareTo(this.networkMaxPressureBar) > 0) {
            throw new GasConnectionBlockedException(List.of(new GasConnectionBlockedException.Blocker(
                    "working_pressure_above_network",
                    "A pressão de trabalho pedida passa do limite da rede ("
                            + this.networkMaxPressureBar.toPlainString() + " bar).")));
        }
        this.status = Objects.requireNonNull(status, "status");
        this.connectedAt = Objects.requireNonNull(connectedAt, "instante da conexão é obrigatório");
        this.connectedBy = Objects.requireNonNull(connectedBy, "responsável é obrigatório");
        this.leakTest = leakTest;
        this.disconnectedAt = disconnectedAt;
        this.disconnectReason = disconnectReason;
        this.version = version;
    }

    /**
     * Monta a linha. O teto da rede é o menor limite entre regulador e manifold — a corrente é tão
     * forte quanto o elo mais fraco.
     */
    public static GasConnection connect(UUID breweryId, UUID cylinderId, GasNetworkComponent regulator,
            GasNetworkComponent manifold, UUID pointOfUseEquipmentId, BigDecimal workingPressureBar,
            Instant at, UUID actorId) {
        Objects.requireNonNull(regulator, "regulador é obrigatório");
        var networkMax = manifold == null
                ? regulator.maxPressureBar()
                : regulator.maxPressureBar().min(manifold.maxPressureBar());
        return new GasConnection(UUID.randomUUID(), breweryId, cylinderId, regulator.id(),
                manifold == null ? null : manifold.id(), pointOfUseEquipmentId, workingPressureBar, networkMax,
                ConnectionStatus.PENDING_TEST, at, actorId, null, null, null, 0);
    }

    public static GasConnection reconstitute(UUID id, UUID breweryId, UUID cylinderId, UUID regulatorId,
            UUID manifoldId, UUID pointOfUseEquipmentId, BigDecimal workingPressureBar,
            BigDecimal networkMaxPressureBar, ConnectionStatus status, Instant connectedAt, UUID connectedBy,
            LeakTest leakTest, Instant disconnectedAt, String disconnectReason, long version) {
        return new GasConnection(id, breweryId, cylinderId, regulatorId, manifoldId, pointOfUseEquipmentId,
                workingPressureBar, networkMaxPressureBar, status, connectedAt, connectedBy, leakTest,
                disconnectedAt, disconnectReason, version);
    }

    /** Registra o teste de vazamento. Aprovado libera a linha; reprovado a bloqueia. */
    public void recordLeakTest(LeakTest test) {
        Objects.requireNonNull(test, "teste é obrigatório");
        if (status == ConnectionStatus.DISCONNECTED) {
            throw new IllegalStateException("conexão desconectada não aceita teste");
        }
        this.leakTest = test;
        this.status = test.passed() ? ConnectionStatus.SERVING : ConnectionStatus.BLOCKED;
    }

    /**
     * Avalia uma leitura de pressão. A medição em si é preservada pelo chamador — aqui decide-se se
     * a linha continua servindo. Acima do teto da rede é sobrepressão e a conexão é bloqueada.
     */
    public boolean evaluatePressure(BigDecimal bar) {
        requirePositive(bar, "pressão medida");
        if (status != ConnectionStatus.SERVING) {
            throw new IllegalStateException("só conexão servindo registra pressão: " + status);
        }
        var overPressure = bar.compareTo(networkMaxPressureBar) > 0;
        if (overPressure) {
            this.status = ConnectionStatus.BLOCKED;
        }
        return overPressure;
    }

    /** Só linha liberada consome gás; consumo em linha pendente ou bloqueada é erro de registro. */
    public void requireServing() {
        if (status != ConnectionStatus.SERVING) {
            throw new IllegalStateException("conexão não está servindo: " + status);
        }
    }

    public void disconnect(String reason, Instant at) {
        if (status == ConnectionStatus.DISCONNECTED) {
            throw new IllegalStateException("conexão já desconectada");
        }
        this.disconnectReason = requireText(reason, "motivo da desconexão", 200);
        this.disconnectedAt = Objects.requireNonNull(at, "instante da desconexão é obrigatório");
        this.status = ConnectionStatus.DISCONNECTED;
    }

    /** A conexão ocupa o cilindro e o ponto de uso enquanto não for desconectada. */
    public boolean open() {
        return status.open();
    }

    private static BigDecimal requirePositive(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " é obrigatória");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(field + " deve ser positiva");
        }
        return value;
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        var trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(field + " excede " + max + " caracteres");
        }
        return trimmed;
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public UUID cylinderId() { return cylinderId; }
    public UUID regulatorId() { return regulatorId; }
    public UUID manifoldId() { return manifoldId; }
    public UUID pointOfUseEquipmentId() { return pointOfUseEquipmentId; }
    public BigDecimal workingPressureBar() { return workingPressureBar; }
    public BigDecimal networkMaxPressureBar() { return networkMaxPressureBar; }
    public ConnectionStatus status() { return status; }
    public Instant connectedAt() { return connectedAt; }
    public UUID connectedBy() { return connectedBy; }
    public LeakTest leakTest() { return leakTest; }
    public Instant disconnectedAt() { return disconnectedAt; }
    public String disconnectReason() { return disconnectReason; }
    public long version() { return version; }
}
