package br.com.brew.brassia.sensory.adapter.inbound.web.dto;

import br.com.brew.brassia.sensory.domain.SensoryAttribute;
import br.com.brew.brassia.sensory.domain.SensorySample;
import br.com.brew.brassia.sensory.domain.SensorySession;
import br.com.brew.brassia.sensory.domain.SessionResults;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Respostas da análise sensorial (SEN-001). */
public final class SensoryViews {

    private SensoryViews() {
    }

    /**
     * Sessão como o provador a vê.
     *
     * <p>As amostras trazem o código cego e <strong>nunca o lote</strong> enquanto a sessão não
     * está encerrada. O lote existe no registro; ele só não sai por aqui — é o que separa uma prova
     * cega de uma conversa sobre a cerveja que todos já sabem qual é.
     */
    public record SessionView(UUID id, String code, String purpose, LocalDate scheduledFor, String status,
            String statusLabel, boolean resultsAvailable, int evaluationCount, List<SampleView> samples,
            Instant openedAt, Instant closedAt) {

        public static SessionView from(SensorySession s, int evaluationCount) {
            var revela = s.status().revealsResults();
            return new SessionView(s.id(), s.code(), s.purpose(), s.scheduledFor(), s.status().name(),
                    s.status().label(), revela, evaluationCount,
                    s.samples().stream().map(sample -> SampleView.from(sample, revela)).toList(),
                    s.openedAt(), s.closedAt());
        }
    }

    /** @param batchId nulo enquanto a sessão não fecha — a cegueira é da resposta, não só da tela */
    public record SampleView(UUID id, String blindCode, UUID batchId, String note) {

        public static SampleView from(SensorySample sample, boolean reveal) {
            return new SampleView(sample.id(), sample.blindCode().value(),
                    reveal ? sample.batchId() : null, sample.note());
        }
    }

    public record AttributeView(String code, String label) {

        public static List<AttributeView> all() {
            return java.util.Arrays.stream(SensoryAttribute.values())
                    .map(a -> new AttributeView(a.name(), a.label()))
                    .toList();
        }
    }

    public record ResultsView(List<SampleResultView> samples, List<ConsistencyView> consistency) {

        public static ResultsView from(SessionResults results) {
            return new ResultsView(
                    results.samples().stream().map(SampleResultView::from).toList(),
                    results.consistency().stream().map(ConsistencyView::from).toList());
        }
    }

    public record SampleResultView(UUID sampleId, String blindCode, UUID batchId, int evaluations,
            Map<String, BigDecimal> averages, BigDecimal overallAverage, BigDecimal spread,
            List<String> descriptors) {

        public static SampleResultView from(SessionResults.SampleResult r) {
            return new SampleResultView(r.sampleId(), r.blindCode(), r.batchId(), r.evaluations(),
                    r.averages().entrySet().stream()
                            .collect(java.util.stream.Collectors.toMap(e -> e.getKey().name(),
                                    Map.Entry::getValue)),
                    r.overallAverage(), r.spread(), r.descriptors());
        }
    }

    /** @param difference diferença entre médias do mesmo lote — mede o painel, não a cerveja */
    public record ConsistencyView(UUID batchId, List<String> blindCodes, BigDecimal difference) {

        public static ConsistencyView from(SessionResults.BatchConsistency c) {
            return new ConsistencyView(c.batchId(), c.blindCodes(), c.difference());
        }
    }
}
