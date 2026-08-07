package br.com.brew.brassia.reporting.domain;

import br.com.brew.brassia.costing.BatchCostLookup.CostSummary;
import br.com.brew.brassia.packaging.PackagingOutcomeLookup.PackagingOutcome;
import br.com.brew.brassia.planning.OrderPlanLookup.PlannedMaterial;
import br.com.brew.brassia.quality.BatchQualityLookup.BatchQuality;
import br.com.brew.brassia.traceability.BatchLineageLookup.BatchLineage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * O dossiê de um lote (RPT-001): plano, execução, qualidade, custo e genealogia num documento só.
 *
 * <p><strong>É consolidação, não cálculo.</strong> Nada aqui soma nada que outro módulo já não
 * tenha somado; o relatório junta e diz de onde veio cada pedaço. Recalcular custo ou rendimento
 * aqui criaria uma segunda aritmética que um dia divergiria da primeira, e a divergência
 * apareceria justamente no documento que existe para ser levado a auditor.
 *
 * <p><strong>Seção que não pôde ser preenchida vira lacuna, não seção vazia.</strong> Um relatório
 * sem a seção de qualidade e um relatório com a seção de qualidade vazia dizem coisas opostas: o
 * primeiro é "não perguntei", o segundo é "não houve medição". Só o segundo é aceitável, e ele tem
 * de dizer isso com todas as letras.
 */
public record BatchReport(UUID batchId, String batchCode, String recipeName, int recipeVersion,
        String status, Instant generatedAt, Plan plan, Execution execution, BatchQuality quality,
        CostSummary cost, BatchLineage lineage, List<String> gaps) {

    public BatchReport {
        Objects.requireNonNull(batchId, "lote é obrigatório");
        Objects.requireNonNull(batchCode, "código do lote é obrigatório");
        Objects.requireNonNull(generatedAt, "instante de geração é obrigatório");
        gaps = List.copyOf(gaps);
    }

    /**
     * O relatório é sempre incompleto de alguma forma enquanto o lote vive — e dizer isso é o
     * ponto. Só o lote encerrado, custeado e envasado chega sem lacuna.
     */
    public boolean incomplete() {
        return !gaps.isEmpty();
    }

    /**
     * O que se pretendia.
     *
     * @param materials vazio quando não há plano confiável — a mesma regra da CST-002: receita
     *                  republicada depois da ordem não serve de plano
     */
    public record Plan(BigDecimal volumeLiters, List<PlannedMaterial> materials) {

        public Plan {
            materials = List.copyOf(materials);
        }
    }

    /**
     * O que aconteceu.
     *
     * @param transferredVolumeLiters vazio enquanto o lote não foi transferido — e vazio não é zero
     */
    public record Execution(BigDecimal transferredVolumeLiters, BigDecimal transferLossesLiters,
            List<PackagingOutcome> packaging) {

        public Execution {
            packaging = List.copyOf(packaging);
        }

        public boolean transferred() {
            return transferredVolumeLiters != null;
        }

        public boolean packaged() {
            return !packaging.isEmpty();
        }
    }
}
