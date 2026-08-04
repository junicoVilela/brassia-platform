package br.com.brew.brassia.sensory.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resultado revelado no fechamento (SEN-001).
 *
 * @param samples       uma linha por amostra, agora com o lote à vista
 * @param consistency   comparação entre amostras do mesmo lote, quando a sessão usou duplicata —
 *                      é o que mede o painel, não a cerveja
 */
public record SessionResults(List<SampleResult> samples, List<BatchConsistency> consistency) {

    /**
     * @param spread diferença entre a maior e a menor nota global; painel disperso é sinal de
     *               que a sessão precisa de calibração antes de servir para decidir
     */
    public record SampleResult(UUID sampleId, String blindCode, UUID batchId, int evaluations,
            Map<SensoryAttribute, BigDecimal> averages, BigDecimal overallAverage, BigDecimal spread,
            List<String> descriptors) {}

    /**
     * Mesmo lote provado sob códigos cegos diferentes.
     *
     * @param difference diferença entre as médias globais das amostras do mesmo lote. Alta
     *                   diferença acusa <strong>viés do painel</strong>: a cerveja era a mesma.
     */
    public record BatchConsistency(UUID batchId, List<String> blindCodes, BigDecimal difference) {}

    static BigDecimal average(List<Integer> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        var sum = values.stream().mapToInt(Integer::intValue).sum();
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }
}
