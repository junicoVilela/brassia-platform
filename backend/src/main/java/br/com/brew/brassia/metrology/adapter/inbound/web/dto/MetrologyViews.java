package br.com.brew.brassia.metrology.adapter.inbound.web.dto;

import br.com.brew.brassia.metrology.domain.Calibration;
import br.com.brew.brassia.metrology.domain.CalibrationStandard;
import br.com.brew.brassia.metrology.domain.Instrument;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Respostas da metrologia (MTR-001). */
public final class MetrologyViews {

    private MetrologyViews() {
    }

    /**
     * A aptidão viaja calculada na data da consulta e vem acompanhada de {@code fitForCriticalUse},
     * para a tela não ter de reimplementar a regra — e não ter como divergir dela.
     */
    public record InstrumentView(UUID id, String code, String name, String type, String typeLabel,
            BigDecimal rangeMin, BigDecimal rangeMax, BigDecimal resolution, BigDecimal accuracy, String unit,
            String location, String state, String blockReason, boolean criticalUse, String fitness,
            boolean fitForCriticalUse, LocalDate calibrationDueOn, CalibrationView lastCalibration) {

        public static InstrumentView from(Instrument i, LocalDate on) {
            return new InstrumentView(i.id(), i.code(), i.name(), i.type().name(), i.type().label(),
                    i.range().min(), i.range().max(), i.range().resolution(), i.range().accuracy(),
                    i.range().unit(), i.location(), i.state().name(), i.blockReason(), i.criticalUse(),
                    i.fitness(on).name(), i.fitForCriticalUse(on), i.calibrationDueOn().orElse(null),
                    i.lastCalibration().map(CalibrationView::from).orElse(null));
        }
    }

    public record CalibrationView(UUID id, UUID standardId, String standardCode, LocalDate performedOn,
            LocalDate dueOn, String performedBy, String certificateNumber, String result, String resultLabel,
            BigDecimal maxDeviation, String restriction, String note) {

        public static CalibrationView from(Calibration c) {
            return new CalibrationView(c.id(), c.standardId(), c.standardCode(), c.performedOn(), c.dueOn(),
                    c.performedBy(), c.certificateNumber(), c.result().name(), c.result().label(),
                    c.maxDeviation(), c.restriction(), c.note());
        }
    }

    public record StandardView(UUID id, String code, String description, String certificateNumber,
            String issuer, String traceability, LocalDate validUntil, boolean expired) {

        public static StandardView from(CalibrationStandard s, LocalDate on) {
            return new StandardView(s.id(), s.code(), s.description(), s.certificateNumber(), s.issuer(),
                    s.traceability(), s.validUntil(), s.expiredOn(on));
        }
    }
}
