package br.com.brew.brassia.sensory.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sessão sensorial (SEN-001): amostras cegas, ficha, resultado e comparação.
 *
 * <p>A sessão é quem guarda a cegueira. Enquanto {@code OPEN}, o lote de cada amostra existe no
 * registro mas não sai daqui; só o fechamento revela. É a diferença entre uma prova cega e uma
 * conversa sobre a cerveja que todo mundo já sabe qual é.
 */
public final class SensorySession {

    private final UUID id;
    private final UUID breweryId;
    private final String code;
    private String purpose;
    private LocalDate scheduledFor;
    private SessionStatus status;
    /**
     * Escala da ficha, congelada quando a sessão é criada (PRM-001). Mudar o parâmetro da
     * cervejaria depois não reinterpreta sessão nenhuma: uma nota 8 dada numa sessão de escala 10
     * não vira 8 de 50.
     */
    private final int maxScore;
    private final List<SensorySample> samples;
    private Instant openedAt;
    private Instant closedAt;
    private final long lockVersion;

    private SensorySession(UUID id, UUID breweryId, String code, String purpose, LocalDate scheduledFor,
            SessionStatus status, int maxScore, List<SensorySample> samples, Instant openedAt,
            Instant closedAt, long lockVersion) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.code = requireText(code, "código", 40);
        this.purpose = requireText(purpose, "propósito", 200);
        this.scheduledFor = Objects.requireNonNull(scheduledFor, "data da sessão é obrigatória");
        this.status = Objects.requireNonNull(status, "situação");
        this.maxScore = maxScore;
        this.samples = new ArrayList<>(Objects.requireNonNull(samples, "amostras"));
        this.openedAt = openedAt;
        this.closedAt = closedAt;
        this.lockVersion = lockVersion;
    }

    /** @param maxScore escala vigente na cervejaria no momento da criação; fica congelada aqui */
    public static SensorySession draft(UUID breweryId, String code, String purpose,
            LocalDate scheduledFor, int maxScore) {
        return new SensorySession(UUID.randomUUID(), breweryId, code, purpose, scheduledFor,
                SessionStatus.DRAFT, maxScore, List.of(), null, null, 0);
    }

    public static SensorySession reconstitute(UUID id, UUID breweryId, String code, String purpose,
            LocalDate scheduledFor, SessionStatus status, int maxScore, List<SensorySample> samples,
            Instant openedAt, Instant closedAt, long lockVersion) {
        return new SensorySession(id, breweryId, code, purpose, scheduledFor, status, maxScore, samples,
                openedAt, closedAt, lockVersion);
    }

    /**
     * Acrescenta amostra sorteando um código cego inédito na sessão.
     *
     * <p>O mesmo lote pode entrar mais de uma vez, de propósito: amostra duplicada sob códigos
     * diferentes é a técnica clássica para medir a consistência do painel. Se o mesmo lote recebe
     * notas muito distintas, o problema está em quem prova, não no que se prova.
     */
    public SensorySample addSample(UUID batchId, String note) {
        requireDraft();
        var used = samples.stream().map(s -> s.blindCode().value()).collect(Collectors.toSet());
        var sample = SensorySample.of(BlindCode.randomExcluding(used), batchId, note);
        samples.add(sample);
        return sample;
    }

    public void removeSample(UUID sampleId) {
        requireDraft();
        if (!samples.removeIf(s -> s.id().equals(sampleId))) {
            throw new IllegalArgumentException("amostra inexistente na sessão");
        }
    }

    public void amend(String purpose, LocalDate scheduledFor) {
        requireDraft();
        this.purpose = requireText(purpose, "propósito", 200);
        this.scheduledFor = Objects.requireNonNull(scheduledFor, "data da sessão é obrigatória");
    }

    /** Abrir libera o envio de fichas. Sessão sem amostra não tem o que provar. */
    public void open(Instant at) {
        requireDraft();
        if (samples.isEmpty()) {
            throw new IllegalStateException("sessão sem amostra não abre");
        }
        this.status = SessionStatus.OPEN;
        this.openedAt = Objects.requireNonNull(at, "instante da abertura");
    }

    public void close(Instant at) {
        if (status != SessionStatus.OPEN) {
            throw new IllegalStateException("só sessão em avaliação pode ser encerrada");
        }
        this.status = SessionStatus.CLOSED;
        this.closedAt = Objects.requireNonNull(at, "instante do encerramento");
    }

    /** Recusa ficha fora da janela de avaliação. */
    public void requireAcceptingEvaluations() {
        if (!status.acceptsEvaluation()) {
            throw new SessionNotOpenException(code, status.label());
        }
    }

    /**
     * Consolida o resultado. Só com a sessão encerrada — é o critério da história.
     *
     * @param evaluations todas as fichas da sessão
     */
    public SessionResults results(List<SensoryEvaluation> evaluations) {
        if (!status.revealsResults()) {
            throw new ResultsNotAvailableException(code, status.label());
        }
        Objects.requireNonNull(evaluations, "fichas");
        var bySample = evaluations.stream().collect(Collectors.groupingBy(SensoryEvaluation::sampleId));

        var results = new ArrayList<SessionResults.SampleResult>();
        for (var sample : samples) {
            var fichas = bySample.getOrDefault(sample.id(), List.of());
            var averages = new EnumMap<SensoryAttribute, BigDecimal>(SensoryAttribute.class);
            for (var attribute : SensoryAttribute.values()) {
                averages.put(attribute, SessionResults.average(
                        fichas.stream().map(f -> f.score(attribute)).toList()));
            }
            var overall = averages.get(SensoryAttribute.OVERALL);
            var globais = fichas.stream().map(f -> f.score(SensoryAttribute.OVERALL)).sorted().toList();
            var spread = globais.isEmpty() ? BigDecimal.ZERO
                    : BigDecimal.valueOf(globais.get(globais.size() - 1) - globais.get(0));
            var descritores = fichas.stream()
                    .flatMap(f -> f.descriptors().stream())
                    .distinct()
                    .sorted()
                    .toList();
            results.add(new SessionResults.SampleResult(sample.id(), sample.blindCode().value(),
                    sample.batchId(), fichas.size(), averages, overall, spread, descritores));
        }

        return new SessionResults(results, consistency(results));
    }

    /**
     * Compara amostras do mesmo lote. É a medida de viés: a cerveja era a mesma, então a diferença
     * entre as médias é do painel.
     */
    private static List<SessionResults.BatchConsistency> consistency(
            List<SessionResults.SampleResult> results) {
        var byBatch = results.stream().collect(Collectors.groupingBy(SessionResults.SampleResult::batchId,
                LinkedHashMap::new, Collectors.toList()));
        var comparisons = new ArrayList<SessionResults.BatchConsistency>();
        byBatch.forEach((batchId, doLote) -> {
            if (doLote.size() < 2) {
                return;
            }
            var ordenadas = doLote.stream()
                    .sorted(Comparator.comparing(SessionResults.SampleResult::overallAverage))
                    .toList();
            var diferenca = ordenadas.get(ordenadas.size() - 1).overallAverage()
                    .subtract(ordenadas.get(0).overallAverage());
            comparisons.add(new SessionResults.BatchConsistency(batchId,
                    doLote.stream().map(SessionResults.SampleResult::blindCode).sorted().toList(),
                    diferenca));
        });
        return comparisons;
    }

    public Optional<SensorySample> sample(UUID sampleId) {
        return samples.stream().filter(s -> s.id().equals(sampleId)).findFirst();
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

    public String purpose() {
        return purpose;
    }

    public LocalDate scheduledFor() {
        return scheduledFor;
    }

    public SessionStatus status() {
        return status;
    }

    public int maxScore() {
        return maxScore;
    }

    public List<SensorySample> samples() {
        return List.copyOf(samples);
    }

    public Instant openedAt() {
        return openedAt;
    }

    public Instant closedAt() {
        return closedAt;
    }

    public long lockVersion() {
        return lockVersion;
    }

    private void requireDraft() {
        if (status != SessionStatus.DRAFT) {
            throw new IllegalStateException("sessão aberta ou encerrada não muda de amostras");
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
