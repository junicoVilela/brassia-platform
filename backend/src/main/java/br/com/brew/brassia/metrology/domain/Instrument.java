package br.com.brew.brassia.metrology.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Instrumento de medição (MTR-001): identidade, faixa/resolução/precisão, situação cadastral e a
 * última calibração.
 *
 * <p>A <strong>aptidão é derivada</strong> ({@link #fitness(LocalDate)}), nunca informada: sai do
 * estado cadastral mais a última calibração e a data. Um campo "apto" editável poderia divergir do
 * certificado, e é justamente essa divergência que a metrologia existe para impedir.
 *
 * <p>O agregado guarda apenas a <em>última</em> calibração, que é a que decide. O histórico é
 * imutável e vive na tabela de calibrações — vencer não apaga certificado.
 */
public final class Instrument {

    private final UUID id;
    private final UUID breweryId;
    private final String code;
    private String name;
    private final InstrumentType type;
    private MeasurementRange range;
    private String location;
    private InstrumentState state;
    private String blockReason;
    /** Designado para uso em ponto crítico de controle. */
    private boolean criticalUse;
    private Calibration lastCalibration;
    private final long version;

    private Instrument(UUID id, UUID breweryId, String code, String name, InstrumentType type,
            MeasurementRange range, String location, InstrumentState state, String blockReason,
            boolean criticalUse, Calibration lastCalibration, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.code = requireText(code, "código", 40);
        this.name = requireText(name, "nome", 120);
        this.type = Objects.requireNonNull(type, "tipo é obrigatório");
        this.range = Objects.requireNonNull(range, "faixa é obrigatória");
        this.location = requireText(location, "localização", 120);
        this.state = Objects.requireNonNull(state, "situação");
        this.blockReason = blockReason;
        this.criticalUse = criticalUse;
        this.lastCalibration = lastCalibration;
        this.version = version;
    }

    public static Instrument register(UUID breweryId, String code, String name, InstrumentType type,
            MeasurementRange range, String location) {
        return new Instrument(UUID.randomUUID(), breweryId, code, name, type, range, location,
                InstrumentState.ACTIVE, null, false, null, 0);
    }

    public static Instrument reconstitute(UUID id, UUID breweryId, String code, String name,
            InstrumentType type, MeasurementRange range, String location, InstrumentState state,
            String blockReason, boolean criticalUse, Calibration lastCalibration, long version) {
        return new Instrument(id, breweryId, code, name, type, range, location, state, blockReason,
                criticalUse, lastCalibration, version);
    }

    /**
     * Aptidão derivada na data informada. A ordem importa: o que foi decidido por uma pessoa
     * (baixa, bloqueio) vem antes do que o tempo decidiu, porque instrumento bloqueado não vira
     * "vencido" ao passar do prazo — ele continua bloqueado, e é isso que a tela precisa dizer.
     */
    public Fitness fitness(LocalDate today) {
        Objects.requireNonNull(today, "data de referência");
        if (state == InstrumentState.RETIRED) {
            return Fitness.RETIRED;
        }
        if (state == InstrumentState.BLOCKED) {
            return Fitness.BLOCKED;
        }
        if (lastCalibration == null) {
            return Fitness.UNCALIBRATED;
        }
        if (!lastCalibration.result().approves()) {
            return Fitness.REJECTED;
        }
        return lastCalibration.expiredOn(today) ? Fitness.EXPIRED : Fitness.FIT;
    }

    /** Registra calibração e passa a decidir por ela — inclusive quando reprova. */
    public Calibration calibrate(CalibrationStandard standard, LocalDate performedOn, LocalDate dueOn,
            String performedBy, String certificateNumber, CalibrationResult result, BigDecimal maxDeviation,
            String restriction, String note) {
        requireNotRetired();
        Objects.requireNonNull(standard, "padrão é obrigatório");
        if (!standard.breweryId().equals(breweryId)) {
            throw new IllegalArgumentException("padrão de outra cervejaria");
        }
        var calibration = Calibration.record(breweryId, id, standard, performedOn, dueOn, performedBy,
                certificateNumber, result, maxDeviation, restriction, note);
        // Reprovar também é evidência: a última calibração passa a ser esta, e o instrumento cai
        // para REJECTED mesmo que a aprovação anterior ainda estivesse no prazo.
        this.lastCalibration = calibration;
        return calibration;
    }

    /**
     * Designa (ou remove a designação de) uso em ponto crítico.
     *
     * <p>Declarar crítico um instrumento que não está apto é a versão cadastral de liberar um lote
     * sem evidência: recusamos com a aptidão junto, para a resposta dizer o que falta. Remover a
     * designação é sempre permitido — é justamente o que se faz quando ele vence.
     */
    public void designateForCriticalUse(boolean critical, LocalDate today) {
        requireNotRetired();
        if (critical) {
            var fitness = fitness(today);
            if (!fitness.usable()) {
                throw new InstrumentNotFitException(code, fitness, calibrationDueOn().orElse(null),
                        "instrumento não apto não pode ser designado para ponto crítico");
            }
        }
        this.criticalUse = critical;
    }

    /**
     * Se serve para medir em ponto crítico agora. Um instrumento designado que venceu deixa de
     * servir sem que ninguém precise mexer no cadastro — é o tempo que decide, e é isso que a
     * porta publicada responde a quem for usá-lo.
     */
    public boolean fitForCriticalUse(LocalDate today) {
        return criticalUse && fitness(today).usable();
    }

    public void block(String reason) {
        requireNotRetired();
        if (state == InstrumentState.BLOCKED) {
            throw new IllegalStateException("instrumento já bloqueado");
        }
        this.blockReason = requireText(reason, "motivo do bloqueio", 200);
        this.state = InstrumentState.BLOCKED;
    }

    public void unblock() {
        if (state != InstrumentState.BLOCKED) {
            throw new IllegalStateException("instrumento não está bloqueado");
        }
        this.state = InstrumentState.ACTIVE;
        this.blockReason = null;
    }

    /** Baixa do parque: terminal, e derruba a designação de uso crítico junto. */
    public void retire(String reason) {
        requireNotRetired();
        this.blockReason = requireText(reason, "motivo da baixa", 200);
        this.state = InstrumentState.RETIRED;
        this.criticalUse = false;
    }

    /**
     * Corrige o cadastro. Mexer na faixa de um instrumento designado para ponto crítico exige
     * recalibrar antes: a calibração vale para a faixa que foi verificada, não para outra.
     */
    public void amend(String name, MeasurementRange range, String location, LocalDate today) {
        requireNotRetired();
        Objects.requireNonNull(range, "faixa é obrigatória");
        if (!range.equals(this.range) && criticalUse) {
            throw new InstrumentNotFitException(code, fitness(today), calibrationDueOn().orElse(null),
                    "mudar a faixa de instrumento de ponto crítico exige remover a designação e recalibrar");
        }
        this.name = requireText(name, "nome", 120);
        this.range = range;
        this.location = requireText(location, "localização", 120);
    }

    public Optional<LocalDate> calibrationDueOn() {
        return Optional.ofNullable(lastCalibration).map(Calibration::dueOn);
    }

    public Optional<Calibration> lastCalibration() {
        return Optional.ofNullable(lastCalibration);
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

    public String name() {
        return name;
    }

    public InstrumentType type() {
        return type;
    }

    public MeasurementRange range() {
        return range;
    }

    public String location() {
        return location;
    }

    public InstrumentState state() {
        return state;
    }

    public String blockReason() {
        return blockReason;
    }

    public boolean criticalUse() {
        return criticalUse;
    }

    public long version() {
        return version;
    }

    private void requireNotRetired() {
        if (state.terminal()) {
            throw new IllegalStateException("instrumento baixado não aceita alteração");
        }
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
}
