package br.com.brew.brassia.sanitation.adapter.inbound.web.dto;

import br.com.brew.brassia.sanitation.domain.CleaningCycle;
import br.com.brew.brassia.sanitation.domain.CycleStep;
import br.com.brew.brassia.sanitation.domain.Verification;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CycleView(
        UUID id, UUID procedureId, String procedureCode, int procedureVersion, UUID equipmentId, String status,
        String interruptReason, Instant startedAt, Instant endedAt, Instant decidedAt,
        VerificationView verification, List<StepView> steps) {

    public static CycleView from(CleaningCycle c) {
        return new CycleView(c.id(), c.procedureId(), c.procedureCode(), c.procedureVersion(), c.equipmentId(),
                c.status().name(), c.interruptReason(), c.startedAt(), c.endedAt(), c.decidedAt(),
                VerificationView.from(c.verification()),
                c.steps().stream().map(StepView::from).toList());
    }

    public record VerificationView(
            boolean rinseOk, boolean visualOk, BigDecimal atpRlu, BigDecimal atpThreshold, boolean atpOk,
            boolean microOk, boolean passed, Instant verifiedAt) {

        static VerificationView from(Verification v) {
            if (v == null) {
                return null;
            }
            return new VerificationView(v.rinseOk(), v.visualOk(), v.atpRlu(), v.atpThreshold(), v.atpOk(),
                    v.microOk(), v.passed(), v.verifiedAt());
        }
    }

    public record StepView(
            int sequence, String method, String product, BigDecimal concentrationMinPct,
            BigDecimal concentrationMaxPct, BigDecimal tempMinC, BigDecimal tempMaxC, Integer timeMinutes,
            String prohibition, boolean evidenceRequired, String status, BigDecimal measuredConcentrationPct,
            BigDecimal measuredTempC, Integer measuredTimeMinutes, String flowActual, String evidence,
            String outOfOrderReason, boolean overridden, String overrideReason, Instant executedAt) {

        static StepView from(CycleStep s) {
            return new StepView(s.sequence(), s.method(), s.product(), s.concentrationMinPct(),
                    s.concentrationMaxPct(), s.tempMinC(), s.tempMaxC(), s.timeMinutes(), s.prohibition(),
                    s.evidenceRequired(), s.status().name(), s.measuredConcentrationPct(), s.measuredTempC(),
                    s.measuredTimeMinutes(), s.flowActual(), s.evidence(), s.outOfOrderReason(), s.overridden(),
                    s.overrideReason(), s.executedAt());
        }
    }
}
