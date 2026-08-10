package br.com.brew.brassia.blend.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * União ou divisão de volume entre lotes, com genealogia (BLD-001).
 *
 * <p><strong>O balanço fecha na simulação, não na execução.</strong> Simular é justamente o momento em que
 * a conta pode não fechar sem custo — depois de mover cerveja entre tanques, descobrir que faltam 40 litros
 * não desfaz a mistura. Por isso {@link #simulate} recusa o desequilíbrio, e a execução apenas confirma o
 * que já estava fechado.
 *
 * <p><strong>Aprovar é separado de executar</strong> porque são decisões de pessoas diferentes em momentos
 * diferentes: uma autoriza misturar, a outra abre a válvula. Colapsar as duas faria a aprovação acontecer
 * no instante em que a cerveja já está se misturando, que é tarde para autorizar qualquer coisa.
 */
public final class BlendOperation {

    /**
     * Tolerância do balanço, em litros.
     *
     * <p>Não é folga para erro: é o limite da instrumentação. Medidor de tanque não resolve mililitro, e
     * exigir igualdade exata recusaria operações corretas por arredondamento — o que treinaria quem opera
     * a inflar a perda declarada até a conta passar, destruindo o valor do próprio campo de perda.
     */
    public static final BigDecimal TOLERANCE_LITERS = new BigDecimal("0.10");

    private final UUID id;
    private final UUID breweryId;
    private final BlendKind kind;
    private final List<VolumeMovement> inputs;
    private final List<VolumeMovement> outputs;
    private final BigDecimal declaredLossLiters;
    private final String reason;
    private final UUID simulatedBy;
    private final Instant simulatedAt;

    private BlendStatus status;
    private UUID approvedBy;
    private Instant approvedAt;
    private UUID executedBy;
    private Instant executedAt;

    private BlendOperation(UUID id, UUID breweryId, BlendKind kind, List<VolumeMovement> inputs,
            List<VolumeMovement> outputs, BigDecimal declaredLossLiters, String reason,
            BlendStatus status, UUID simulatedBy, Instant simulatedAt, UUID approvedBy,
            Instant approvedAt, UUID executedBy, Instant executedAt) {
        this.id = id;
        this.breweryId = breweryId;
        this.kind = kind;
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
        this.declaredLossLiters = declaredLossLiters;
        this.reason = reason;
        this.status = status;
        this.simulatedBy = simulatedBy;
        this.simulatedAt = simulatedAt;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.executedBy = executedBy;
        this.executedAt = executedAt;
    }

    /**
     * Simula a operação, recusando o que não fecha.
     *
     * @throws UnbalancedBlendException quando entrada, saída e perda declarada não batem
     */
    public static BlendOperation simulate(UUID id, UUID breweryId, BlendKind kind,
            List<VolumeMovement> inputs, List<VolumeMovement> outputs, BigDecimal declaredLossLiters,
            String reason, UUID actor, Instant at) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(breweryId, "breweryId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(at, "at");
        var loss = Objects.requireNonNull(declaredLossLiters, "declaredLossLiters");
        if (loss.signum() < 0) {
            // Perda negativa seria cerveja aparecendo do nada com nome de perda.
            throw new IllegalArgumentException("perda declarada não pode ser negativa: " + loss);
        }
        requireShape(kind, inputs, outputs);
        requireDistinctBatches(inputs, outputs);
        requireBalance(inputs, outputs, loss);

        var text = Objects.requireNonNull(reason, "reason").trim();
        if (text.isEmpty()) {
            // Blend sem motivo registrado é uma decisão sem rastro: meses depois ninguém sabe se foi
            // correção de desvio, ajuste de estilo ou aproveitamento de sobra.
            throw new IllegalArgumentException("o motivo da operação não pode ser vazio");
        }

        return new BlendOperation(id, breweryId, kind, inputs, outputs, loss, text,
                BlendStatus.SIMULATED, actor, at, null, null, null, null);
    }

    /** Reconstrói do banco sem revalidar: o que já foi gravado aconteceu. */
    public static BlendOperation reconstitute(UUID id, UUID breweryId, BlendKind kind,
            List<VolumeMovement> inputs, List<VolumeMovement> outputs, BigDecimal declaredLossLiters,
            String reason, BlendStatus status, UUID simulatedBy, Instant simulatedAt, UUID approvedBy,
            Instant approvedAt, UUID executedBy, Instant executedAt) {
        return new BlendOperation(id, breweryId, kind, inputs, outputs, declaredLossLiters, reason,
                status, simulatedBy, simulatedAt, approvedBy, approvedAt, executedBy, executedAt);
    }

    /**
     * Aprova.
     *
     * <p>Quem simulou pode aprovar: o sistema não sabe quem é quem numa cervejaria de três pessoas, e
     * impedir aqui só produziria contas compartilhadas. A separação que importa é a de <em>permissão</em>,
     * e ela está no controller.
     */
    public void approve(UUID actor, Instant at) {
        if (status != BlendStatus.SIMULATED) {
            throw new IllegalBlendTransitionException(status, BlendStatus.APPROVED);
        }
        approvedBy = Objects.requireNonNull(actor, "actor");
        approvedAt = Objects.requireNonNull(at, "at");
        status = BlendStatus.APPROVED;
    }

    /** Executa. Só o que foi aprovado se executa — a aprovação é o que autoriza mexer no tanque. */
    public void execute(UUID actor, Instant at) {
        if (status != BlendStatus.APPROVED) {
            throw new IllegalBlendTransitionException(status, BlendStatus.EXECUTED);
        }
        executedBy = Objects.requireNonNull(actor, "actor");
        executedAt = Objects.requireNonNull(at, "at");
        status = BlendStatus.EXECUTED;
    }

    /**
     * Descarta.
     *
     * <p>Executada não se descarta: a cerveja já está misturada, e apagar a operação deixaria dois lotes
     * com volume alterado e nenhuma explicação — exatamente o buraco de rastreabilidade que a história
     * existe para fechar.
     */
    public void discard() {
        if (status == BlendStatus.EXECUTED) {
            throw new IllegalBlendTransitionException(status, BlendStatus.DISCARDED);
        }
        status = BlendStatus.DISCARDED;
    }

    /** O total que entra. */
    public BigDecimal inputLiters() {
        return sum(inputs);
    }

    /** O total que sai, sem contar a perda declarada. */
    public BigDecimal outputLiters() {
        return sum(outputs);
    }

    /**
     * A genealogia vale a partir da execução.
     *
     * <p>Uma simulação não moveu cerveja nenhuma; contribuir aresta antes disso faria o recall alcançar
     * lotes que nunca se tocaram — e um recall que exagera é descartado por quem o recebe, o que o torna
     * tão inútil quanto um que falta.
     */
    public boolean contributesLineage() {
        return status == BlendStatus.EXECUTED;
    }

    private static void requireShape(BlendKind kind, List<VolumeMovement> inputs,
            List<VolumeMovement> outputs) {
        if (inputs.isEmpty() || outputs.isEmpty()) {
            throw new IllegalArgumentException("a operação precisa de ao menos uma entrada e uma saída");
        }
        // A forma é o que distingue união de divisão. Sem esta checagem, "MERGE" com uma entrada e três
        // saídas seria aceito e apareceria como união no histórico — descrevendo o oposto do que houve.
        if (kind == BlendKind.MERGE && inputs.size() < 2) {
            throw new IllegalArgumentException("união precisa de ao menos dois lotes de origem");
        }
        if (kind == BlendKind.SPLIT && (inputs.size() != 1 || outputs.size() < 2)) {
            throw new IllegalArgumentException("divisão parte de um lote e chega a ao menos dois");
        }
    }

    private static void requireDistinctBatches(List<VolumeMovement> inputs,
            List<VolumeMovement> outputs) {
        var repeated = inputs.stream().map(VolumeMovement::batchId).distinct().count() != inputs.size()
                || outputs.stream().map(VolumeMovement::batchId).distinct().count() != outputs.size();
        if (repeated) {
            throw new IllegalArgumentException("um lote não pode aparecer duas vezes do mesmo lado");
        }
        // Lote dos dois lados fecharia o balanço consigo mesmo e criaria uma aresta de genealogia de um
        // lote para ele próprio — um ciclo que trava qualquer travessia de recall.
        var overlap = inputs.stream().map(VolumeMovement::batchId)
                .anyMatch(id -> outputs.stream().anyMatch(o -> o.batchId().equals(id)));
        if (overlap) {
            throw new IllegalArgumentException("um lote não pode ser origem e destino da mesma operação");
        }
    }

    private static void requireBalance(List<VolumeMovement> inputs, List<VolumeMovement> outputs,
            BigDecimal loss) {
        var in = sum(inputs);
        var out = sum(outputs);
        var difference = in.subtract(out).subtract(loss);
        if (difference.abs().compareTo(TOLERANCE_LITERS) > 0) {
            throw new UnbalancedBlendException(in, out, loss, difference);
        }
    }

    private static BigDecimal sum(List<VolumeMovement> movements) {
        return movements.stream().map(VolumeMovement::liters)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public UUID id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public BlendKind kind() {
        return kind;
    }

    public List<VolumeMovement> inputs() {
        return inputs;
    }

    public List<VolumeMovement> outputs() {
        return outputs;
    }

    public BigDecimal declaredLossLiters() {
        return declaredLossLiters;
    }

    public String reason() {
        return reason;
    }

    public BlendStatus status() {
        return status;
    }

    public UUID simulatedBy() {
        return simulatedBy;
    }

    public Instant simulatedAt() {
        return simulatedAt;
    }

    public Optional<UUID> approvedBy() {
        return Optional.ofNullable(approvedBy);
    }

    public Optional<Instant> approvedAt() {
        return Optional.ofNullable(approvedAt);
    }

    public Optional<UUID> executedBy() {
        return Optional.ofNullable(executedBy);
    }

    public Optional<Instant> executedAt() {
        return Optional.ofNullable(executedAt);
    }

    /** Transição que o estado atual não permite. */
    public static final class IllegalBlendTransitionException extends RuntimeException {

        private final BlendStatus current;
        private final BlendStatus attempted;

        IllegalBlendTransitionException(BlendStatus current, BlendStatus attempted) {
            super("operação em " + current + " não pode ir para " + attempted);
            this.current = current;
            this.attempted = attempted;
        }

        public BlendStatus current() {
            return current;
        }

        public BlendStatus attempted() {
            return attempted;
        }
    }
}
