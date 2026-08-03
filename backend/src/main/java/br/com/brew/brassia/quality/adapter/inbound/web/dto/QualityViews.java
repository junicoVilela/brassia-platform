package br.com.brew.brassia.quality.adapter.inbound.web.dto;

import br.com.brew.brassia.quality.domain.ControlPlan;
import br.com.brew.brassia.quality.domain.ControlPoint;
import br.com.brew.brassia.quality.domain.Deviation;
import br.com.brew.brassia.quality.domain.CapaAction;
import br.com.brew.brassia.quality.domain.Containment;
import br.com.brew.brassia.quality.domain.Investigation;
import br.com.brew.brassia.quality.domain.Measurement;
import br.com.brew.brassia.quality.domain.NonConformity;
import br.com.brew.brassia.quality.domain.NonConformityStatus;
import br.com.brew.brassia.quality.domain.Verification;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

    /**
     * Não conformidade e o seu tratamento (QLT-002).
     *
     * @param overduePhases fases com prazo vencido, derivadas na data da consulta — não há coluna
     *                      de "atrasado" que envelhece sozinha
     * @param closable      só depois de verificação eficaz; é o critério da história
     */
    public record NonConformityView(UUID id, String code, String title, String description, String source,
            String sourceLabel, UUID deviationId, String severity, String severityLabel, String status,
            String statusLabel, LocalDate containmentDueOn, LocalDate investigationDueOn,
            LocalDate verificationDueOn, List<String> overduePhases, boolean overdue, boolean closable,
            ContainmentView containment, InvestigationView investigation, List<ActionView> actions,
            List<VerificationView> verifications, Instant openedAt, Instant closedAt) {

        public static NonConformityView from(NonConformity nc, LocalDate on) {
            return new NonConformityView(nc.id(), nc.code(), nc.title(), nc.description(),
                    nc.source().name(), nc.source().label(), nc.deviationId().orElse(null),
                    nc.severity().name(), nc.severity().label(), nc.status().name(), nc.status().label(),
                    nc.containmentDueOn(), nc.investigationDueOn(), nc.verificationDueOn(),
                    nc.overduePhases(on), nc.overdue(on),
                    nc.status() == NonConformityStatus.VERIFIED,
                    nc.containment().map(ContainmentView::from).orElse(null),
                    nc.investigation().map(InvestigationView::from).orElse(null),
                    nc.actions().stream().map(a -> ActionView.from(a, on)).toList(),
                    nc.verifications().stream().map(VerificationView::from).toList(),
                    nc.openedAt(), nc.closedAt());
        }
    }

    public record ContainmentView(String description, Instant takenAt) {

        public static ContainmentView from(Containment c) {
            return new ContainmentView(c.description(), c.takenAt());
        }
    }

    public record InvestigationView(String rootCause, String method, Instant investigatedAt) {

        public static InvestigationView from(Investigation i) {
            return new InvestigationView(i.rootCause(), i.method(), i.investigatedAt());
        }
    }

    public record ActionView(UUID id, String kind, String kindLabel, String description, String owner,
            LocalDate dueOn, Instant completedAt, boolean completed, boolean overdue) {

        public static ActionView from(CapaAction a, LocalDate on) {
            return new ActionView(a.id(), a.kind().name(), a.kind().label(), a.description(), a.owner(),
                    a.dueOn(), a.completedAt(), a.completed(), a.overdue(on));
        }
    }

    public record VerificationView(boolean effective, String evidence, Instant verifiedAt) {

        public static VerificationView from(Verification v) {
            return new VerificationView(v.effective(), v.evidence(), v.verifiedAt());
        }
    }

    /** @param deviationId presente quando a medição saiu da faixa */
    public record MeasurementOutcome(UUID measurementId, boolean withinSpec, UUID deviationId,
            DeviationView deviation) {}
}
