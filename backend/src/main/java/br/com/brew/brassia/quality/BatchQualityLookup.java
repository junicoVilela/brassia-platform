package br.com.brew.brassia.quality;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * O que a qualidade sabe de um lote (RPT-001): medições, desvios e não conformidades.
 *
 * <p>Consulta publicada, e não porta invertida: a qualidade não depende de relatório nenhum, então
 * quem monta o relatório pergunta direto. A regra é a de sempre — quem tem o dado responde por ele.
 */
public interface BatchQualityLookup {

    BatchQuality ofBatch(UUID breweryId, UUID batchId);

    /**
     * @param measurements quantas medições o lote teve
     * @param withinSpec   quantas ficaram dentro da faixa. A diferença para o total é o que virou
     *                     desvio, e por isso os dois números viajam juntos: "3 desvios" sem o
     *                     denominador não diz se o lote foi bem ou mal
     */
    record BatchQuality(int measurements, int withinSpec, List<Measurement> outOfSpec,
            List<Deviation> deviations, List<NonConformity> nonConformities) {

        public BatchQuality {
            outOfSpec = List.copyOf(outOfSpec);
            deviations = List.copyOf(deviations);
            nonConformities = List.copyOf(nonConformities);
        }

        public static BatchQuality empty() {
            return new BatchQuality(0, 0, List.of(), List.of(), List.of());
        }

        /** Verdadeiro quando ninguém mediu nada — que não é o mesmo que o lote estar aprovado. */
        public boolean unmeasured() {
            return measurements == 0;
        }
    }

    record Measurement(String parameter, BigDecimal value, String unit, Instant measuredAt) {}

    record Deviation(String parameter, String severity, String status, BigDecimal limitValue,
            BigDecimal measuredValue, String unit, Instant openedAt) {}

    record NonConformity(String code, String title, String severity, String status) {}
}
