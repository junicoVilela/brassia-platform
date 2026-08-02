package br.com.brew.brassia.packaging.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.calculator.CalculatorEngine;
import br.com.brew.brassia.packaging.application.port.inbound.CarbonationCommands;
import br.com.brew.brassia.packaging.application.port.inbound.CarbonationCommands.Recommendation;
import br.com.brew.brassia.packaging.application.port.outbound.CarbonationRepository;
import br.com.brew.brassia.packaging.application.port.outbound.PackagingPlanRepository;
import br.com.brew.brassia.packaging.domain.Carbonation;
import br.com.brew.brassia.packaging.domain.CarbonationMethod;
import br.com.brew.brassia.packaging.domain.PackagingPlan;
import br.com.brew.brassia.packaging.domain.PrimingSugar;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Carbonatação do plano de envase (PKG-002).
 *
 * <p>As fórmulas ficam no hub de calculadoras (`calculator`), com método e versão próprios — aqui
 * só se compõe a decisão: quanto CO₂ já está dissolvido na temperatura de referência, quanto falta
 * para o alvo e o que fazer para chegar lá. Nada é gravado sem confirmação humana.
 */
public final class CarbonationHandlers {

    private static final String RESIDUAL = "co2-residual";
    private static final String PRIMING = "priming-sugar";
    private static final String FORCED = "forced-carbonation-pressure";

    private CarbonationHandlers() {
    }

    public static final class Preview implements CarbonationCommands.Preview {

        private final PackagingPlanRepository plans;
        private final CalculatorEngine calculator;

        public Preview(PackagingPlanRepository plans, CalculatorEngine calculator) {
            this.plans = Objects.requireNonNull(plans);
            this.calculator = Objects.requireNonNull(calculator);
        }

        @Override
        public Recommendation handle(Query query) {
            var plan = plan(plans, query.breweryId(), query.planId());
            return recommend(calculator, plan, CarbonationMethod.of(query.method()), query.targetVolumes(),
                    query.referenceTempC(), query.primingSugar());
        }
    }

    public static final class Record implements CarbonationCommands.Record {

        private final PackagingPlanRepository plans;
        private final CarbonationRepository carbonations;
        private final CalculatorEngine calculator;
        private final AuditTrail audit;

        public Record(PackagingPlanRepository plans, CarbonationRepository carbonations,
                CalculatorEngine calculator, AuditTrail audit) {
            this.plans = Objects.requireNonNull(plans);
            this.carbonations = Objects.requireNonNull(carbonations);
            this.calculator = Objects.requireNonNull(calculator);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            // A confirmação nunca é implícita: nenhum número calculado vira decisão sozinho.
            if (!command.confirmed()) {
                throw new IllegalArgumentException("a carbonatação exige confirmação explícita");
            }
            var plan = plan(plans, command.breweryId(), command.planId());
            if (!plan.active()) {
                throw new IllegalStateException("plano cancelado não aceita carbonatação");
            }

            var method = CarbonationMethod.of(command.method());
            var recommendation = recommend(calculator, plan, method, command.targetVolumes(),
                    command.referenceTempC(), command.primingSugar());
            var at = Instant.now();
            var carbonation = method == CarbonationMethod.PRIMING
                    ? Carbonation.priming(plan.id(), plan.breweryId(), recommendation.targetVolumes(),
                            recommendation.referenceTempC(), recommendation.residualVolumes(),
                            PrimingSugar.of(recommendation.primingSugar()), recommendation.primingSugarGrams(),
                            recommendation.calculationMethod(), recommendation.calculatorVersion(),
                            recommendation.alerts(), command.actorId(), at)
                    : Carbonation.forced(plan.id(), plan.breweryId(), recommendation.targetVolumes(),
                            recommendation.referenceTempC(), recommendation.residualVolumes(),
                            recommendation.pressureBar(), recommendation.calculationMethod(),
                            recommendation.calculatorVersion(), recommendation.alerts(), command.actorId(), at);
            carbonations.save(carbonation);

            var metadata = new LinkedHashMap<String, String>();
            metadata.put("code", plan.code());
            metadata.put("method", method.name());
            metadata.put("targetVolumes", carbonation.targetVolumes().toPlainString());
            metadata.put("residualVolumes", carbonation.residualVolumes().toPlainString());
            metadata.put("calculatorVersion", carbonation.calculatorVersion());
            if (method == CarbonationMethod.PRIMING) {
                metadata.put("primingSugarGrams", carbonation.primingSugarGrams().toPlainString());
            } else {
                metadata.put("pressureBar", carbonation.pressureBar().toPlainString());
            }
            audit.record(AuditEvent.success(plan.breweryId(), command.actorId(), "packaging.carbonation.confirm",
                    "packaging.plan", plan.id().toString(), Map.copyOf(metadata)));
        }
    }

    public static final class Get implements CarbonationCommands.Get {

        private final CarbonationRepository carbonations;

        public Get(CarbonationRepository carbonations) {
            this.carbonations = Objects.requireNonNull(carbonations);
        }

        @Override
        public Optional<Carbonation> handle(UUID breweryId, UUID planId) {
            return carbonations.findByPlan(breweryId, planId);
        }
    }

    private static PackagingPlan plan(PackagingPlanRepository plans, UUID breweryId, UUID planId) {
        return plans.findById(breweryId, planId)
                .orElseThrow(() -> new IllegalArgumentException("plano de envase inexistente"));
    }

    /**
     * Compõe a recomendação: o CO₂ residual sai da temperatura de referência e alimenta o cálculo
     * do método escolhido. O volume de cerveja vem do próprio plano — não é digitado de novo.
     */
    private static Recommendation recommend(CalculatorEngine calculator, PackagingPlan plan,
            CarbonationMethod method, BigDecimal targetVolumes, BigDecimal referenceTempC, String sugarName) {
        Objects.requireNonNull(targetVolumes, "volumes alvo é obrigatório");
        Objects.requireNonNull(referenceTempC, "temperatura de referência é obrigatória");

        var residual = calculator.compute(RESIDUAL, Map.of("tempC", referenceTempC));
        var assumptions = new ArrayList<>(residual.assumptions());
        var alerts = new ArrayList<>(residual.alerts());
        var missing = targetVolumes.subtract(residual.value()).max(BigDecimal.ZERO);

        if (method == CarbonationMethod.PRIMING) {
            var sugar = PrimingSugar.of(sugarName);
            var priming = calculator.compute(PRIMING, Map.of(
                    "targetVolumes", targetVolumes,
                    "residualVolumes", residual.value(),
                    "beerVolumeLiters", plan.plannedVolumeLiters(),
                    "sugarYield", sugar.yieldGramsCo2PerGram()));
            assumptions.addAll(priming.assumptions());
            alerts.addAll(priming.alerts());
            if (sugar.approximate()) {
                alerts.add("O rendimento do extrato seco de malte é estimado e varia por fabricante e lote; "
                        + "confira a carbonatação antes de liberar o lote.");
            }
            return new Recommendation(method.name(), targetVolumes, referenceTempC, residual.value(), missing,
                    plan.plannedVolumeLiters(), sugar.name(), priming.value(), null, priming.method(),
                    priming.version(), List.copyOf(assumptions), List.copyOf(alerts));
        }

        var forced = calculator.compute(FORCED, Map.of("targetVolumes", targetVolumes, "tempC", referenceTempC));
        assumptions.addAll(forced.assumptions());
        alerts.addAll(forced.alerts());
        return new Recommendation(method.name(), targetVolumes, referenceTempC, residual.value(), missing,
                plan.plannedVolumeLiters(), null, null, forced.value(), forced.method(), forced.version(),
                List.copyOf(assumptions), List.copyOf(alerts));
    }
}
