package br.com.brew.brassia.quality.adapter.inbound.web.dto;

import br.com.brew.brassia.quality.domain.ControlPlan;
import br.com.brew.brassia.quality.domain.ControlPoint;
import br.com.brew.brassia.quality.domain.Deviation;
import br.com.brew.brassia.quality.domain.Measurement;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Respostas da qualidade (QLT-001). */
public final class QualityViews {

    private QualityViews() {
    }

    public record PlanView(UUID id, String code, String name, UUID recipeId, String stage, String stageLabel,
            String status, int version, List<PointView> points) {

        public static PlanView from(ControlPlan p) {
            return new PlanView(p.id(), p.code(), p.name(), p.recipeId().orElse(null), p.stage().name(),
                    p.stage().label(), p.status().name(), p.version(),
                    p.points().stream().map(PointView::from).toList());
        }
    }

    /**
     * @param limits texto pronto da faixa ("≤ 50 ppb"), para a tela não reimplementar a formatação
     *               de limite unilateral e não ter como divergir dela
     */
    public record PointView(UUID id, String parameter, BigDecimal min, BigDecimal max, BigDecimal target,
            String unit, String limits, String frequencyKind, Integer everyHours, String frequency,
            String action, String severity, String severityLabel, boolean critical) {

        public static PointView from(ControlPoint p) {
            return new PointView(p.id(), p.parameter(), p.limits().min(), p.limits().max(),
                    p.limits().target(), p.limits().unit(), p.limits().describe(),
                    p.frequency().kind().name(), p.frequency().everyHours(), p.frequency().describe(),
                    p.action(), p.severity().name(), p.severity().label(), p.critical());
        }
    }

    public record MeasurementView(UUID id, UUID planId, int planVersion, UUID pointId, String parameter,
            UUID batchId, UUID instrumentId, String instrumentFitness, boolean instrumentQuestionable,
            BigDecimal value, String unit, boolean withinSpec, String note, Instant measuredAt) {

        public static MeasurementView from(Measurement m) {
            return new MeasurementView(m.id(), m.planId(), m.planVersion(), m.pointId(), m.parameter(),
                    m.batchId(), m.instrumentId(), m.instrumentFitness(), m.instrumentQuestionable(),
                    m.value(), m.unit(), m.withinSpec(), m.note(), m.measuredAt());
        }
    }

    public record DeviationView(UUID id, UUID measurementId, UUID planId, UUID pointId, String parameter,
            String severity, String severityLabel, String bound, BigDecimal limitValue,
            BigDecimal measuredValue, BigDecimal excess, String unit, String action, String status,
            String description, Instant openedAt) {

        public static DeviationView from(Deviation d) {
            return new DeviationView(d.id(), d.measurementId(), d.planId(), d.pointId(), d.parameter(),
                    d.severity().name(), d.severity().label(), d.bound().name(), d.limitValue(),
                    d.measuredValue(), d.excess(), d.unit(), d.action(), d.status().name(), d.describe(),
                    d.openedAt());
        }
    }

    /** @param deviationId presente quando a medição saiu da faixa */
    public record MeasurementOutcome(UUID measurementId, boolean withinSpec, UUID deviationId,
            DeviationView deviation) {}
}
