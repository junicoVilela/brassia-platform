package br.com.brew.brassia.packaging.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Lote de produto acabado (TRC-001-B): a cerveja que saiu da linha, identificada.
 *
 * <p>Existe porque sem ele a rastreabilidade terminava na execução do envase. Um recall precisa
 * apontar para alguma coisa — "as 780 latas do lote LOTE-100/1" — e até aqui não havia essa coisa:
 * havia um número de unidades dentro do registro de execução, que não é um objeto que se recolhe.
 *
 * <p><strong>Só o que foi bom entra.</strong> Unidades rejeitadas consumiram embalagem e não viraram
 * produto; contá-las no lote inflaria o que existe no mundo e faria o recall procurar latas que
 * ninguém pode devolver.
 *
 * <p><strong>Não guarda validade.</strong> A validade vem da evidência de oxigênio (FSL-001), por
 * plano, e pode ser sobreposta com justificativa. Copiá-la para cá criaria uma segunda verdade que
 * silenciosamente divergiria do override.
 */
public final class FinishedLot {

    private final UUID id;
    private final UUID breweryId;
    private final String code;
    private final UUID runId;
    private final UUID planId;
    private final UUID batchId;
    private final String batchCode;
    private final UUID containerId;
    private final BigDecimal containerVolumeMl;
    private final int units;
    private final BigDecimal volumeLiters;
    private final LocalDate packagedOn;

    private FinishedLot(UUID id, UUID breweryId, String code, UUID runId, UUID planId, UUID batchId,
            String batchCode, UUID containerId, BigDecimal containerVolumeMl, int units,
            BigDecimal volumeLiters, LocalDate packagedOn) {
        this.id = Objects.requireNonNull(id);
        this.breweryId = Objects.requireNonNull(breweryId, "cervejaria é obrigatória");
        this.code = Objects.requireNonNull(code, "código do lote é obrigatório");
        this.runId = Objects.requireNonNull(runId, "execução de origem é obrigatória");
        this.planId = Objects.requireNonNull(planId, "plano de origem é obrigatório");
        this.batchId = Objects.requireNonNull(batchId, "lote de produção é obrigatório");
        this.batchCode = Objects.requireNonNull(batchCode, "código do lote de produção é obrigatório");
        this.containerId = Objects.requireNonNull(containerId, "embalagem é obrigatória");
        this.containerVolumeMl = Objects.requireNonNull(containerVolumeMl);
        this.volumeLiters = Objects.requireNonNull(volumeLiters);
        this.packagedOn = Objects.requireNonNull(packagedOn);
        if (units <= 0) {
            throw new IllegalArgumentException("lote de produto acabado sem unidades boas não existe");
        }
        this.units = units;
    }

    /**
     * Nasce da execução do envase, no mesmo commit.
     *
     * @param sequence ordem deste envase dentro do lote de produção, a partir de 1 — um lote pode
     *                 ser dividido em vários envases, e cada um vira um lote de produto acabado com
     *                 identidade própria, porque foram latas diferentes em momentos diferentes
     */
    public static FinishedLot from(PackagingRun run, String batchCode, UUID containerId, int sequence,
            LocalDate packagedOn) {
        Objects.requireNonNull(run, "execução é obrigatória");
        return new FinishedLot(UUID.randomUUID(), run.breweryId(), codeOf(batchCode, sequence), run.id(),
                run.planId(), run.batchId(), batchCode, containerId, run.containerVolumeMl(),
                run.producedUnits(), run.packagedVolumeLiters(), packagedOn);
    }

    /**
     * O código é derivado do lote de produção mais a ordem do envase: {@code LOTE-100/1}.
     *
     * <p>Derivar em vez de pedir tem um motivo prático — o código vai impresso na lata e precisa
     * levar de volta ao lote de produção sem consulta nenhuma. Um código digitado à mão quebraria
     * essa leitura no primeiro erro de digitação.
     */
    static String codeOf(String batchCode, int sequence) {
        if (sequence < 1) {
            throw new IllegalArgumentException("ordem do envase começa em 1");
        }
        return batchCode + "/" + sequence;
    }

    public static FinishedLot rehydrate(UUID id, UUID breweryId, String code, UUID runId, UUID planId,
            UUID batchId, String batchCode, UUID containerId, BigDecimal containerVolumeMl, int units,
            BigDecimal volumeLiters, LocalDate packagedOn) {
        return new FinishedLot(id, breweryId, code, runId, planId, batchId, batchCode, containerId,
                containerVolumeMl, units, volumeLiters, packagedOn);
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

    public UUID runId() {
        return runId;
    }

    public UUID planId() {
        return planId;
    }

    public UUID batchId() {
        return batchId;
    }

    public String batchCode() {
        return batchCode;
    }

    public UUID containerId() {
        return containerId;
    }

    public BigDecimal containerVolumeMl() {
        return containerVolumeMl;
    }

    public int units() {
        return units;
    }

    public BigDecimal volumeLiters() {
        return volumeLiters;
    }

    public LocalDate packagedOn() {
        return packagedOn;
    }
}
