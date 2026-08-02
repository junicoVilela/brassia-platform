package br.com.brew.brassia.gas.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cilindro de gás (GAS-001): identidade, carga, requalificação e situação.
 *
 * <p>O conteúdo é rastreado por <strong>massa</strong>, não por pressão: em cilindro de CO₂ com
 * fase líquida a pressão fica praticamente constante enquanto houver líquido, então estimar o que
 * resta a partir do manômetro daria um número errado com cara de certo.
 *
 * <p>Requalificação vencida e bloqueio são impedimentos de segurança: o cilindro simplesmente não
 * é alocado, e o desbloqueio é ato humano com motivo registrado.
 */
public final class GasCylinder {

    private final UUID id;
    private final UUID breweryId;
    private final String code;
    private final GasType gasType;
    private final BigDecimal capacityKg;
    private final BigDecimal tareKg;
    private BigDecimal contentKg;
    private LocalDate requalificationDueOn;
    private CylinderStatus status;
    private String blockReason;
    private String location;
    private final long version;

    private GasCylinder(UUID id, UUID breweryId, String code, GasType gasType, BigDecimal capacityKg,
            BigDecimal tareKg, BigDecimal contentKg, LocalDate requalificationDueOn, CylinderStatus status,
            String blockReason, String location, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.code = requireText(code, "código", 40);
        this.gasType = Objects.requireNonNull(gasType, "tipo de gás é obrigatório");
        this.capacityKg = requirePositive(capacityKg, "capacidade");
        this.tareKg = requirePositive(tareKg, "tara");
        this.contentKg = requireContent(contentKg, this.capacityKg);
        this.requalificationDueOn = Objects.requireNonNull(requalificationDueOn, "requalificação é obrigatória");
        this.status = Objects.requireNonNull(status, "status");
        this.blockReason = blockReason;
        this.location = requireText(location, "localização", 120);
        this.version = version;
    }

    public static GasCylinder register(UUID breweryId, String code, GasType gasType, BigDecimal capacityKg,
            BigDecimal tareKg, BigDecimal contentKg, LocalDate requalificationDueOn, String location) {
        var status = contentKg.signum() == 0 ? CylinderStatus.EMPTY : CylinderStatus.AVAILABLE;
        return new GasCylinder(UUID.randomUUID(), breweryId, code, gasType, capacityKg, tareKg, contentKg,
                requalificationDueOn, status, null, location, 0);
    }

    public static GasCylinder reconstitute(UUID id, UUID breweryId, String code, GasType gasType,
            BigDecimal capacityKg, BigDecimal tareKg, BigDecimal contentKg, LocalDate requalificationDueOn,
            CylinderStatus status, String blockReason, String location, long version) {
        return new GasCylinder(id, breweryId, code, gasType, capacityKg, tareKg, contentKg, requalificationDueOn,
                status, blockReason, location, version);
    }

    /** Requalificação vencida em {@code today}: o cilindro não pode ser posto em serviço. */
    public boolean expired(LocalDate today) {
        return requalificationDueOn.isBefore(Objects.requireNonNull(today, "data de referência"));
    }

    /** Impedimentos de uso do cilindro em si, na data de referência. */
    public List<GasConnectionBlockedException.Blocker> blockers(LocalDate today) {
        var blockers = new ArrayList<GasConnectionBlockedException.Blocker>();
        if (status == CylinderStatus.BLOCKED) {
            blockers.add(new GasConnectionBlockedException.Blocker("cylinder_blocked",
                    "O cilindro está bloqueado: " + blockReason));
        }
        if (status == CylinderStatus.CONNECTED) {
            blockers.add(new GasConnectionBlockedException.Blocker("cylinder_in_use",
                    "O cilindro já está conectado em outro ponto."));
        }
        if (status == CylinderStatus.EMPTY) {
            blockers.add(new GasConnectionBlockedException.Blocker("cylinder_empty",
                    "O cilindro está vazio."));
        }
        if (expired(today)) {
            blockers.add(new GasConnectionBlockedException.Blocker("cylinder_expired",
                    "A requalificação do cilindro venceu em " + requalificationDueOn + "."));
        }
        return blockers;
    }

    /** Põe o cilindro em serviço. Vencido ou impedido nunca chega aqui — o comando reúne os motivos. */
    public void connect(LocalDate today) {
        var blockers = blockers(today);
        if (!blockers.isEmpty()) {
            throw new GasConnectionBlockedException(blockers);
        }
        this.status = CylinderStatus.CONNECTED;
    }

    /** Tira o cilindro de serviço; vazio permanece vazio até ser trocado. */
    public void release() {
        if (status != CylinderStatus.CONNECTED) {
            return;
        }
        this.status = contentKg.signum() == 0 ? CylinderStatus.EMPTY : CylinderStatus.AVAILABLE;
    }

    /** Consome massa de gás. Consumir mais do que há é erro de registro, não saldo negativo. */
    public void consume(BigDecimal kg) {
        requirePositive(kg, "consumo");
        if (status != CylinderStatus.CONNECTED) {
            throw new IllegalStateException("só cilindro conectado registra consumo");
        }
        if (kg.compareTo(contentKg) > 0) {
            throw new IllegalArgumentException("consumo maior que o conteúdo do cilindro");
        }
        this.contentKg = contentKg.subtract(kg);
    }

    /** Bloqueio é decisão humana e exige motivo; cilindro conectado precisa ser desconectado antes. */
    public void block(String reason) {
        if (status == CylinderStatus.CONNECTED) {
            throw new IllegalStateException("desconecte o cilindro antes de bloquear");
        }
        this.blockReason = requireText(reason, "motivo do bloqueio", 200);
        this.status = CylinderStatus.BLOCKED;
    }

    /** Libera o cilindro; requalificação vencida continua impedindo o uso, o desbloqueio não a apaga. */
    public void unblock() {
        if (status != CylinderStatus.BLOCKED) {
            throw new IllegalStateException("cilindro não está bloqueado");
        }
        this.blockReason = null;
        this.status = contentKg.signum() == 0 ? CylinderStatus.EMPTY : CylinderStatus.AVAILABLE;
    }

    /** Registra nova requalificação (data futura); não altera bloqueio nem conteúdo. */
    public void requalify(LocalDate dueOn, LocalDate today) {
        Objects.requireNonNull(dueOn, "vencimento da requalificação é obrigatório");
        if (!dueOn.isAfter(Objects.requireNonNull(today, "data de referência"))) {
            throw new IllegalArgumentException("a nova requalificação precisa vencer no futuro");
        }
        this.requalificationDueOn = dueOn;
    }

    /** Recarrega o cilindro: a massa volta ao valor aferido, dentro da capacidade. */
    public void refill(BigDecimal contentKg) {
        if (status == CylinderStatus.CONNECTED) {
            throw new IllegalStateException("desconecte o cilindro antes de recarregar");
        }
        if (status == CylinderStatus.BLOCKED) {
            throw new IllegalStateException("cilindro bloqueado não é recarregado");
        }
        this.contentKg = requireContent(contentKg, capacityKg);
        this.status = this.contentKg.signum() == 0 ? CylinderStatus.EMPTY : CylinderStatus.AVAILABLE;
    }

    public void relocate(String location) {
        this.location = requireText(location, "localização", 120);
    }

    private static BigDecimal requireContent(BigDecimal value, BigDecimal capacityKg) {
        Objects.requireNonNull(value, "conteúdo é obrigatório");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("conteúdo não pode ser negativo");
        }
        if (value.compareTo(capacityKg) > 0) {
            throw new IllegalArgumentException("conteúdo excede a capacidade do cilindro");
        }
        return value;
    }

    private static BigDecimal requirePositive(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " é obrigatório");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(field + " deve ser positivo");
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
    public String code() { return code; }
    public GasType gasType() { return gasType; }
    public BigDecimal capacityKg() { return capacityKg; }
    public BigDecimal tareKg() { return tareKg; }
    public BigDecimal contentKg() { return contentKg; }
    public LocalDate requalificationDueOn() { return requalificationDueOn; }
    public CylinderStatus status() { return status; }
    public String blockReason() { return blockReason; }
    public String location() { return location; }
    public long version() { return version; }
}
