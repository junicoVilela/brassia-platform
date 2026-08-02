package br.com.brew.brassia.gas.adapter.inbound.web.dto;

import br.com.brew.brassia.gas.application.port.inbound.GasQueries;
import br.com.brew.brassia.gas.application.port.outbound.GasConnectionRepository;
import br.com.brew.brassia.gas.domain.GasConnection;
import br.com.brew.brassia.gas.domain.GasCylinder;
import br.com.brew.brassia.gas.domain.GasNetworkComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Projeções de leitura da API de gases (GAS-001). */
public final class GasViews {

    private GasViews() {
    }

    /**
     * O cilindro sai da API já respondendo às perguntas da operação: está vencido? pode ser
     * alocado? Assim a tela não reimplementa a regra de aptidão.
     */
    public record CylinderView(UUID id, String code, String gasType, BigDecimal capacityKg, BigDecimal tareKg,
            BigDecimal contentKg, LocalDate requalificationDueOn, boolean expired, String status,
            boolean allocatable, String blockReason, String location) {

        public static CylinderView from(GasCylinder c) {
            var today = LocalDate.now(ZoneOffset.UTC);
            return new CylinderView(c.id(), c.code(), c.gasType().name(), c.capacityKg(), c.tareKg(),
                    c.contentKg(), c.requalificationDueOn(), c.expired(today), c.status().name(),
                    c.blockers(today).isEmpty(), c.blockReason(), c.location());
        }
    }

    public record ComponentView(UUID id, String kind, String code, String name, BigDecimal maxPressureBar,
            BigDecimal setPressureBar, boolean active) {

        public static ComponentView from(GasNetworkComponent c) {
            return new ComponentView(c.id(), c.kind().name(), c.code(), c.name(), c.maxPressureBar(),
                    c.setPressureBar(), c.active());
        }
    }

    public record LeakTestView(boolean passed, String method, BigDecimal pressureDropBar, String note,
            Instant testedAt) {}

    public record ConnectionView(UUID id, UUID cylinderId, UUID regulatorId, UUID manifoldId,
            UUID pointOfUseEquipmentId, BigDecimal workingPressureBar, BigDecimal networkMaxPressureBar,
            String status, Instant connectedAt, LeakTestView leakTest, Instant disconnectedAt,
            String disconnectReason) {

        public static ConnectionView from(GasConnection c) {
            var test = c.leakTest();
            return new ConnectionView(c.id(), c.cylinderId(), c.regulatorId(), c.manifoldId(),
                    c.pointOfUseEquipmentId(), c.workingPressureBar(), c.networkMaxPressureBar(),
                    c.status().name(), c.connectedAt(),
                    test == null ? null : new LeakTestView(test.passed(), test.method(), test.pressureDropBar(),
                            test.note(), test.testedAt()),
                    c.disconnectedAt(), c.disconnectReason());
        }
    }

    public record PressureReadingView(UUID id, BigDecimal bar, BigDecimal tempC, boolean overPressure, Instant at) {

        static PressureReadingView from(GasConnectionRepository.PressureReadingRow row) {
            return new PressureReadingView(row.id(), row.bar(), row.tempC(), row.overPressure(), row.at());
        }
    }

    public record ConsumptionView(UUID id, BigDecimal kg, String reason, Instant at) {

        static ConsumptionView from(GasConnectionRepository.ConsumptionRow row) {
            return new ConsumptionView(row.id(), row.kg(), row.reason(), row.at());
        }
    }

    public record ConnectionDetailView(ConnectionView connection, List<PressureReadingView> pressureReadings,
            List<ConsumptionView> consumption, BigDecimal consumedKg) {

        public static ConnectionDetailView from(GasQueries.ConnectionDetail detail) {
            var consumption = detail.consumption().stream().map(ConsumptionView::from).toList();
            var consumedKg = consumption.stream()
                    .map(ConsumptionView::kg)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new ConnectionDetailView(ConnectionView.from(detail.connection()),
                    detail.pressureReadings().stream().map(PressureReadingView::from).toList(),
                    consumption, consumedKg);
        }
    }
}
