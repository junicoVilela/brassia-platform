package br.com.brew.brassia.water.application.service;

import br.com.brew.brassia.water.application.port.inbound.SimulateWaterBlendUseCase;
import br.com.brew.brassia.water.application.port.outbound.WaterProfileRepository;
import br.com.brew.brassia.water.application.port.outbound.WaterReportRepository;
import br.com.brew.brassia.water.application.port.outbound.WaterSourceRepository;
import br.com.brew.brassia.water.domain.IonProfile;
import br.com.brew.brassia.water.domain.WaterBlend;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Simula a mistura de fontes por balanço de massa e, opcionalmente, compara com
 * um perfil-alvo. É uma consulta: não persiste nada e o resultado ecoa as
 * entradas e o método usados.
 */
public final class SimulateWaterBlendHandler implements SimulateWaterBlendUseCase {
    private final WaterSourceRepository sources;
    private final WaterReportRepository reports;
    private final WaterProfileRepository profiles;

    public SimulateWaterBlendHandler(WaterSourceRepository sources, WaterReportRepository reports,
            WaterProfileRepository profiles) {
        this.sources = Objects.requireNonNull(sources);
        this.reports = Objects.requireNonNull(reports);
        this.profiles = Objects.requireNonNull(profiles);
    }

    @Override
    public Result handle(Command command) {
        if (command.inputs() == null || command.inputs().isEmpty()) {
            throw new IllegalArgumentException("informe ao menos uma fonte");
        }

        var components = new ArrayList<WaterBlend.Component>();
        var applied = new ArrayList<AppliedInput>();
        for (var input : command.inputs()) {
            var source = sources.findById(command.breweryId(), input.sourceId())
                    .orElseThrow(() -> new IllegalArgumentException("fonte inexistente"));
            var report = reports.findLatestBySource(command.breweryId(), input.sourceId())
                    .orElseThrow(() -> new IllegalArgumentException("fonte sem laudo: " + source.code().value()));
            components.add(new WaterBlend.Component(report.ions(), input.volumeLiters()));
            applied.add(new AppliedInput(input.sourceId(), source.code().value(), input.volumeLiters()));
        }

        var blend = WaterBlend.simulate(components);
        var ions = blend.ions();

        Target target = null;
        if (command.targetProfileId() != null) {
            var profile = profiles.findById(command.breweryId(), command.targetProfileId())
                    .orElseThrow(() -> new IllegalArgumentException("perfil-alvo inexistente"));
            target = new Target(profile.id().value(), profile.code().value(), deviation(ions, profile.targets()));
        }

        return new Result(WaterBlend.METHOD, blend.totalVolumeLiters(), ions.calcium(), ions.magnesium(),
                ions.sodium(), ions.sulfate(), ions.chloride(), ions.bicarbonate(), applied, target);
    }

    private static Deviation deviation(IonProfile blend, IonProfile target) {
        return new Deviation(
                blend.calcium().subtract(target.calcium()),
                blend.magnesium().subtract(target.magnesium()),
                blend.sodium().subtract(target.sodium()),
                blend.sulfate().subtract(target.sulfate()),
                blend.chloride().subtract(target.chloride()),
                blend.bicarbonate().subtract(target.bicarbonate()));
    }
}
