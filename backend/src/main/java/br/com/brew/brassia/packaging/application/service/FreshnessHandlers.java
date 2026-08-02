package br.com.brew.brassia.packaging.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.packaging.application.port.inbound.FreshnessCommands;
import br.com.brew.brassia.packaging.application.port.outbound.FreshnessRepository;
import br.com.brew.brassia.packaging.application.port.outbound.PackagingRunRepository;
import br.com.brew.brassia.packaging.domain.FreshnessRecord;
import br.com.brew.brassia.packaging.domain.OxygenMeasurement;
import br.com.brew.brassia.packaging.domain.ShelfLifePolicy;
import br.com.brew.brassia.packaging.domain.ShelfLifeRecommendation;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Oxigênio e vida útil do envase (FSL-001).
 *
 * <p>A validade é <strong>recomendada com evidência</strong>: o TPO medido é confrontado com as
 * faixas que a própria cervejaria configurou, e a recomendação sai explicada — qual faixa pegou,
 * quanto do oxigênio veio do espaço livre, se a purga foi conferida e se a vedação passou.
 *
 * <p>O override não apaga a recomendação: os dois ficam lado a lado, e é isso que permite saber,
 * meses depois, se a data impressa veio da evidência ou de uma decisão humana — e por quê.
 */
public final class FreshnessHandlers {

    private FreshnessHandlers() {
    }

    public static final class Record implements FreshnessCommands.Record {

        private final FreshnessRepository freshness;
        private final PackagingRunRepository runs;
        private final AuditTrail audit;

        public Record(FreshnessRepository freshness, PackagingRunRepository runs, AuditTrail audit) {
            this.freshness = Objects.requireNonNull(freshness);
            this.runs = Objects.requireNonNull(runs);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public Result handle(Command command) {
            // Oxigênio se mede na embalagem cheia: sem envase executado não há o que medir.
            var run = runs.findByPlan(command.breweryId(), command.planId())
                    .orElseThrow(() -> new IllegalStateException(
                            "registre o envase antes de medir o oxigênio da embalagem"));
            var packagedOn = LocalDate.ofInstant(run.executedAt(), ZoneOffset.UTC);

            var measurement = new OxygenMeasurement(command.dissolvedOxygenPpb(),
                    command.totalPackageOxygenPpb(), command.purgeMethod(), command.purgeVerified(),
                    command.sealCheckMethod(), command.sealCheckPassed());
            var recommendation = freshness.findPolicy(command.breweryId())
                    .map(policy -> ShelfLifeRecommendation.evaluate(measurement, policy, packagedOn))
                    .orElse(null);

            var record = FreshnessRecord.record(command.planId(), command.breweryId(), packagedOn, measurement,
                    recommendation, command.actorId(), Instant.now());
            freshness.save(record);

            var metadata = new LinkedHashMap<String, String>();
            metadata.put("dissolvedOxygenPpb", measurement.dissolvedOxygenPpb().toPlainString());
            metadata.put("totalPackageOxygenPpb", measurement.totalPackageOxygenPpb().toPlainString());
            metadata.put("purgeVerified", String.valueOf(measurement.purgeVerified()));
            metadata.put("sealCheckPassed", String.valueOf(measurement.sealCheckPassed()));
            metadata.put("recommendedBestBefore",
                    recommendation == null ? "sem política" : recommendation.bestBefore().toString());
            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "packaging.freshness.record",
                    "packaging.plan", command.planId().toString(), Map.copyOf(metadata)));

            return new Result(record, recommendation);
        }
    }

    public static final class OverrideShelfLife implements FreshnessCommands.OverrideShelfLife {

        private final FreshnessRepository freshness;
        private final AuditTrail audit;

        public OverrideShelfLife(FreshnessRepository freshness, AuditTrail audit) {
            this.freshness = Objects.requireNonNull(freshness);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var record = freshness.findForUpdate(command.breweryId(), command.planId())
                    .orElseThrow(() -> new IllegalArgumentException("plano sem controle de frescor registrado"));
            var version = record.version();
            record.override(command.shelfLifeDays(), command.reason(), command.actorId(), Instant.now());
            if (!freshness.updateOverride(record, version)) {
                throw new IllegalStateException("registro alterado por outra operação; tente novamente");
            }

            // O override é a decisão que mais precisa de rastro: quem, quando, por quê e sobre o quê.
            var metadata = new LinkedHashMap<String, String>();
            metadata.put("reason", record.overrideReason());
            metadata.put("overrideShelfLifeDays", String.valueOf(record.overrideShelfLifeDays()));
            metadata.put("overrideBestBefore", record.overrideBestBefore().toString());
            metadata.put("recommendedShelfLifeDays", record.recommendedShelfLifeDays() == null
                    ? "sem recomendação" : String.valueOf(record.recommendedShelfLifeDays()));
            metadata.put("extendsBeyondRecommendation", String.valueOf(record.extendsBeyondRecommendation()));
            audit.record(AuditEvent.success(command.breweryId(), command.actorId(),
                    "packaging.freshness.override", "packaging.plan", command.planId().toString(),
                    Map.copyOf(metadata)));
        }
    }

    public static final class Get implements FreshnessCommands.Get {

        private final FreshnessRepository freshness;

        public Get(FreshnessRepository freshness) {
            this.freshness = Objects.requireNonNull(freshness);
        }

        @Override
        public Optional<FreshnessRecord> handle(UUID breweryId, UUID planId) {
            return freshness.findByPlan(breweryId, planId);
        }
    }

    public static final class Policy implements FreshnessCommands.Policy {

        private final FreshnessRepository freshness;
        private final AuditTrail audit;

        public Policy(FreshnessRepository freshness, AuditTrail audit) {
            this.freshness = Objects.requireNonNull(freshness);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public Optional<ShelfLifePolicy> get(UUID breweryId) {
            return freshness.findPolicy(breweryId);
        }

        @Override
        public void save(UUID actorId, UUID breweryId, ShelfLifePolicy policy) {
            freshness.savePolicy(breweryId, policy);
            audit.record(AuditEvent.success(breweryId, actorId, "packaging.shelf-life-policy.save",
                    "packaging.policy", breweryId.toString(),
                    Map.of("tiers", String.valueOf(policy.tiers().size()),
                            "fallbackDays", String.valueOf(policy.fallbackDays()))));
        }
    }
}
