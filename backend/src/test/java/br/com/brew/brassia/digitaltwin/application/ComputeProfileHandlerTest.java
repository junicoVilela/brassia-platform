package br.com.brew.brassia.digitaltwin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.digitaltwin.application.port.inbound.ProfileCommands;
import br.com.brew.brassia.digitaltwin.application.port.outbound.LearnedProfileRepository;
import br.com.brew.brassia.digitaltwin.application.service.ComputeProfileHandler;
import br.com.brew.brassia.digitaltwin.domain.Confidence;
import br.com.brew.brassia.digitaltwin.domain.LearnedProfile;
import br.com.brew.brassia.digitaltwin.domain.ProfileMetric;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.production.BatchOutcomeLookup;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O cálculo do perfil a partir de uma amostra (DTW-001).
 *
 * <p>O que estes testes fixam: <strong>o que entra na amostra decide o número</strong>, e o que fica de
 * fora precisa ficar de fora pelos motivos certos — nunca contado como zero.
 */
class ComputeProfileHandlerTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID OUTRA = UUID.randomUUID();
    private static final UUID RECEITA = UUID.randomUUID();
    private static final UUID OUTRA_RECEITA = UUID.randomUUID();
    private static final UUID OPERADOR = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-08-09T10:00:00Z");

    private FakeProfiles profiles;
    private FakeBatches batches;
    private FakeOutcomes outcomes;
    private RecordingAudit audit;
    private ComputeProfileHandler handler;

    @BeforeEach
    void setUp() {
        profiles = new FakeProfiles();
        batches = new FakeBatches();
        outcomes = new FakeOutcomes();
        audit = new RecordingAudit();
        handler = new ComputeProfileHandler(profiles, batches, outcomes, audit,
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    /** Um lote transferido: planejado 100 L, transferido o informado, perda informada. */
    private UUID lote(String transferido, String perda) {
        return lote(CERVEJARIA, RECEITA, transferido, perda);
    }

    private UUID lote(UUID brewery, UUID recipe, String transferido, String perda) {
        var id = UUID.randomUUID();
        batches.add(brewery, id, recipe);
        outcomes.add(brewery, id, new BigDecimal("100"),
                transferido == null ? null : new BigDecimal(transferido),
                perda == null ? null : new BigDecimal(perda));
        return id;
    }

    @Test
    @DisplayName("calcula rendimento e perda a partir dos lotes transferidos")
    void calcula() {
        var amostra = List.of(lote("92", "3.0"), lote("94", "2.5"), lote("90", "3.5"));

        var profile = handler.compute(new ProfileCommands.Request(OPERADOR, CERVEJARIA, RECEITA, amostra));

        assertThat(profile.estimateOf(ProfileMetric.VOLUME_YIELD_PERCENT).mean())
                .isEqualByComparingTo("92");
        assertThat(profile.estimateOf(ProfileMetric.TRANSFER_LOSS_LITERS).mean())
                .isEqualByComparingTo("3");
        assertThat(profile.observedBatchIds()).isEqualTo(amostra);
    }

    @Test
    @DisplayName("LOTE SEM TRANSFERÊNCIA É EXCLUÍDO, nunca contado como zero")
    void loteSemTransferenciaEExcluido() {
        // Um lote que ainda está fervendo não rendeu 0%: ele ainda não rendeu. Contá-lo como zero
        // arrastaria a média para baixo e — pior — encolheria a faixa, dando aparência de certeza a um
        // número envenenado.
        var transferidos = List.of(lote("92", "3.0"), lote("94", "2.5"));
        var fervendo = lote(null, null);

        var amostra = new ArrayList<>(transferidos);
        amostra.add(fervendo);
        var profile = handler.compute(new ProfileCommands.Request(OPERADOR, CERVEJARIA, RECEITA, amostra));

        assertThat(profile.estimateOf(ProfileMetric.VOLUME_YIELD_PERCENT).mean())
                .isEqualByComparingTo("93");
        assertThat(profile.estimateOf(ProfileMetric.VOLUME_YIELD_PERCENT).sampleSize()).isEqualTo(2);
        // E a exclusão é auditável: o lote fervendo não está na amostra gravada.
        assertThat(profile.observedBatchIds()).isEqualTo(transferidos);
    }

    @Test
    @DisplayName("lote de OUTRA RECEITA não entra: aprender sobre A com lotes de B não descreve nenhuma")
    void loteDeOutraReceitaNaoEntra() {
        var daReceita = lote("92", "3.0");
        var deOutra = lote(CERVEJARIA, OUTRA_RECEITA, "50", "20");

        var profile = handler.compute(new ProfileCommands.Request(OPERADOR, CERVEJARIA, RECEITA,
                List.of(daReceita, deOutra)));

        assertThat(profile.observedBatchIds()).containsExactly(daReceita);
        // Só uma observação: a estimativa fica INSUFFICIENT, que é honesto.
        assertThat(profile.estimateOf(ProfileMetric.VOLUME_YIELD_PERCENT).confidence())
                .isEqualTo(Confidence.INSUFFICIENT);
    }

    @Test
    @DisplayName("lote de OUTRA CERVEJARIA não resolve, e por isso não entra")
    void loteDeOutraCervejariaNaoEntra() {
        // O isolamento vale sem este módulo conhecer a tabela de produção: a consulta publicada
        // simplesmente não devolve o lote.
        var meu = lote("92", "3.0");
        var alheio = lote(OUTRA, RECEITA, "10", "50");

        var profile = handler.compute(new ProfileCommands.Request(OPERADOR, CERVEJARIA, RECEITA,
                List.of(meu, alheio)));

        assertThat(profile.observedBatchIds()).containsExactly(meu);
    }

    @Test
    @DisplayName("amostra em que nenhum lote serve é RECUSADA, não vira perfil vazio")
    void amostraVaziaERecusada() {
        // Um perfil sem observação daria a impressão de que a receita foi analisada.
        var fervendo = lote(null, null);

        assertThatThrownBy(() -> handler.compute(
                new ProfileCommands.Request(OPERADOR, CERVEJARIA, RECEITA, List.of(fervendo))))
                .isInstanceOf(ComputeProfileHandler.EmptySampleException.class);
    }

    @Test
    @DisplayName("lista de lotes vazia é recusada")
    void listaVaziaERecusada() {
        assertThatThrownBy(() -> handler.compute(
                new ProfileCommands.Request(OPERADOR, CERVEJARIA, RECEITA, List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a versão é derivada, não informada: nunca duas 'versão 3' do mesmo perfil")
    void versaoDerivada() {
        var amostra = List.of(lote("92", "3.0"), lote("94", "2.5"));

        var primeira = handler.compute(new ProfileCommands.Request(OPERADOR, CERVEJARIA, RECEITA, amostra));
        var segunda = handler.compute(new ProfileCommands.Request(OPERADOR, CERVEJARIA, RECEITA, amostra));

        assertThat(primeira.version()).isEqualTo(1);
        assertThat(segunda.version()).isEqualTo(2);
        // E a anterior continua lá: um perfil de maio guiou decisões em maio.
        assertThat(profiles.stored).hasSize(2);
    }

    @Test
    @DisplayName("a auditoria distingue lotes INFORMADOS de lotes USADOS")
    void auditoriaDistingueInformadoDeUsado() {
        // É a pergunta que alguém faz meses depois: "por que este perfil só olhou dois dos três lotes?".
        var amostra = List.of(lote("92", "3.0"), lote("94", "2.5"), lote(null, null));

        handler.compute(new ProfileCommands.Request(OPERADOR, CERVEJARIA, RECEITA, amostra));

        var evento = audit.events.getFirst();
        assertThat(evento.metadata()).containsEntry("batchesRequested", "3");
        assertThat(evento.metadata()).containsEntry("batchesUsed", "2");
    }

    @Test
    @DisplayName("o rendimento é derivado do que a produção informou, não recalculado")
    void rendimentoDerivadoDaProducao() {
        // 75 de 100 planejados = 75%. Nenhum número é reinventado aqui — recalcular criaria uma segunda
        // opinião sobre o mesmo fato.
        var amostra = List.of(lote("75", "5"), lote("75", "5"));

        var profile = handler.compute(new ProfileCommands.Request(OPERADOR, CERVEJARIA, RECEITA, amostra));

        assertThat(profile.estimateOf(ProfileMetric.VOLUME_YIELD_PERCENT).mean())
                .isEqualByComparingTo("75");
    }

    // --- dublês ---

    private static final class FakeProfiles implements LearnedProfileRepository {
        private final List<LearnedProfile> stored = new ArrayList<>();

        @Override public void insert(LearnedProfile profile) {
            stored.add(profile);
        }

        @Override public int highestVersionOf(UUID breweryId, UUID recipeId) {
            return stored.stream()
                    .filter(p -> p.breweryId().equals(breweryId) && p.recipeId().equals(recipeId))
                    .mapToInt(LearnedProfile::version).max().orElse(0);
        }

        @Override public Optional<LearnedProfile> latestOf(UUID breweryId, UUID recipeId) {
            return stored.stream()
                    .filter(p -> p.breweryId().equals(breweryId) && p.recipeId().equals(recipeId))
                    .reduce((a, b) -> b);
        }

        @Override public List<LearnedProfile> historyOf(UUID breweryId, UUID recipeId) {
            return stored;
        }
    }

    private static final class FakeBatches implements BatchLookup {
        private final Map<String, Snapshot> byKey = new HashMap<>();

        void add(UUID breweryId, UUID batchId, UUID recipeId) {
            byKey.put(breweryId + "|" + batchId, new Snapshot(batchId, UUID.randomUUID(), "LOTE",
                    new BigDecimal("100"), new BigDecimal("100"), "DONE", recipeId, 1, "Receita"));
        }

        @Override public Optional<Snapshot> find(UUID breweryId, UUID batchId) {
            return Optional.ofNullable(byKey.get(breweryId + "|" + batchId));
        }
    }

    private static final class FakeOutcomes implements BatchOutcomeLookup {
        private final Map<String, BatchOutcome> byKey = new HashMap<>();

        void add(UUID breweryId, UUID batchId, BigDecimal planned, BigDecimal transferred,
                BigDecimal losses) {
            byKey.put(breweryId + "|" + batchId, new BatchOutcome(planned, transferred, losses));
        }

        @Override public Optional<BatchOutcome> outcomeOf(UUID breweryId, UUID batchId) {
            return Optional.ofNullable(byKey.get(breweryId + "|" + batchId));
        }
    }

    private static final class RecordingAudit implements AuditTrail {
        private final List<AuditEvent> events = new ArrayList<>();

        @Override public void record(AuditEvent event) {
            events.add(event);
        }
    }
}
