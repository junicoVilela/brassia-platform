package br.com.brew.brassia.digitaltwin.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.digitaltwin.application.port.inbound.ProfileCommands;
import br.com.brew.brassia.digitaltwin.application.port.outbound.LearnedProfileRepository;
import br.com.brew.brassia.digitaltwin.domain.LearnedProfile;
import br.com.brew.brassia.digitaltwin.domain.ProfileMetric;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.production.BatchOutcomeLookup;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Calcula um perfil aprendido a partir de uma amostra de lotes (DTW-001).
 *
 * <p><strong>Nenhum número é recalculado aqui.</strong> Volume planejado, volume transferido e perda vêm
 * das consultas publicadas da produção — o módulo que responde por eles. Recalcular criaria uma segunda
 * opinião sobre o mesmo fato, e duas opiniões divergem no dia em que uma das duas ganha uma correção que a
 * outra não tem. O que este caso de uso faz é <em>resumir</em>.
 *
 * <p><strong>Lote sem transferência é excluído da amostra, não contado como zero.</strong> A distinção é a
 * história inteira: um lote que ainda está fervendo não rendeu 0%, ele ainda não rendeu. Contá-lo como zero
 * arrastaria a média para baixo e — pior — encolheria a faixa, dando aparência de certeza a um número
 * envenenado. Os lotes efetivamente usados ficam gravados no perfil, e é por isso que a exclusão é
 * auditável em vez de silenciosa.
 */
public final class ComputeProfileHandler implements ProfileCommands {

    private final LearnedProfileRepository profiles;
    private final BatchLookup batches;
    private final BatchOutcomeLookup outcomes;
    private final AuditTrail audit;
    private final Clock clock;

    public ComputeProfileHandler(LearnedProfileRepository profiles, BatchLookup batches,
            BatchOutcomeLookup outcomes, AuditTrail audit, Clock clock) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.batches = Objects.requireNonNull(batches, "batches");
        this.outcomes = Objects.requireNonNull(outcomes, "outcomes");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public LearnedProfile compute(Request request) {
        Objects.requireNonNull(request, "request");
        if (request.batchIds() == null || request.batchIds().isEmpty()) {
            throw new IllegalArgumentException("informe ao menos um lote para aprender");
        }

        var observations = new EnumMap<ProfileMetric, List<BigDecimal>>(ProfileMetric.class);
        observations.put(ProfileMetric.VOLUME_YIELD_PERCENT, new ArrayList<>());
        observations.put(ProfileMetric.TRANSFER_LOSS_LITERS, new ArrayList<>());
        var used = new ArrayList<UUID>();

        for (var batchId : request.batchIds()) {
            // Cada lote é resolvido dentro da cervejaria pelas consultas publicadas: um lote de outra
            // cervejaria simplesmente não resolve, e é assim que o isolamento vale sem este módulo
            // conhecer a tabela de produção.
            var batch = batches.find(request.breweryId(), batchId).orElse(null);
            if (batch == null || !request.recipeId().equals(batch.recipeId())) {
                // Lote inexistente, de outra cervejaria, ou de outra receita. Aprender sobre a receita A
                // com lotes da receita B produziria um número que não descreve nem uma nem outra.
                continue;
            }
            var outcome = outcomes.outcomeOf(request.breweryId(), batchId).orElse(null);
            if (outcome == null || !outcome.transferred()) {
                continue;
            }

            var planned = outcome.plannedVolumeLiters();
            var transferred = outcome.transferredVolumeLiters();
            if (planned != null && planned.signum() > 0 && transferred != null) {
                observations.get(ProfileMetric.VOLUME_YIELD_PERCENT).add(
                        transferred.multiply(new BigDecimal("100"))
                                .divide(planned, 4, RoundingMode.HALF_UP));
            }
            if (outcome.transferLossesLiters() != null) {
                observations.get(ProfileMetric.TRANSFER_LOSS_LITERS).add(outcome.transferLossesLiters());
            }
            used.add(batchId);
        }

        if (used.isEmpty()) {
            // Nenhum lote da amostra serviu. Recusar é melhor que gravar um perfil vazio: quem pediu
            // escolheu lotes que não descrevem esta receita, e um perfil sem observação nenhuma daria a
            // impressão de que a receita foi analisada.
            throw new EmptySampleException(request.recipeId());
        }

        var version = profiles.highestVersionOf(request.breweryId(), request.recipeId()) + 1;
        var profile = LearnedProfile.compute(request.breweryId(), request.recipeId(), version,
                observations, used, request.actorId(), clock.instant());
        profiles.insert(profile);

        audit(request, profile);
        return profile;
    }

    /**
     * Auditado porque calcular escolhe a amostra, e a amostra decide o número.
     *
     * <p>O metadata guarda quantos lotes foram <em>informados</em> e quantos foram <em>usados</em>. A
     * diferença entre os dois é a pergunta que alguém vai fazer meses depois — "por que este perfil só
     * olhou quatro dos dez lotes que pedi?".
     */
    private void audit(Request request, LearnedProfile profile) {
        var metadata = new LinkedHashMap<String, String>();
        metadata.put("recipeId", request.recipeId().toString());
        metadata.put("version", String.valueOf(profile.version()));
        metadata.put("batchesRequested", String.valueOf(request.batchIds().size()));
        metadata.put("batchesUsed", String.valueOf(profile.observedBatchIds().size()));
        metadata.put("usable", String.valueOf(profile.hasAnyUsableEstimate()));
        audit.record(AuditEvent.success(request.breweryId(), request.actorId(),
                "digitaltwin.profile.compute", "twin_profile", profile.id().toString(), metadata));
    }

    /** Nenhum lote da amostra serviu para aprender sobre esta receita. */
    public static final class EmptySampleException extends RuntimeException {

        private final UUID recipeId;

        EmptySampleException(UUID recipeId) {
            super("nenhum lote da amostra descreve esta receita");
            this.recipeId = recipeId;
        }

        public UUID recipeId() {
            return recipeId;
        }
    }
}
