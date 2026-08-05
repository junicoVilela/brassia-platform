package br.com.brew.brassia.traceability.application.service;

import br.com.brew.brassia.traceability.DestinationSource;
import br.com.brew.brassia.traceability.LineageSource;
import br.com.brew.brassia.traceability.application.port.inbound.DrillQueries;
import br.com.brew.brassia.traceability.application.port.outbound.DrillRepository;
import br.com.brew.brassia.traceability.domain.RecallDrill;
import br.com.brew.brassia.traceability.domain.UnknownDrillException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * O relatório do simulado (FDS-004): percentual localizado, tempo e lacunas.
 *
 * <p>Enquanto o simulado corre, os números vêm do grafo — são o alvo que a equipe está tentando
 * localizar, e mudam se alguém expedir no meio do exercício, o que é a verdade. Depois de
 * encerrado, vêm congelados: aquele foi o resultado daquele dia.
 *
 * <p>As <strong>ações corretivas sugeridas</strong> são as lacunas viradas do avesso. Um lote sem
 * expedição vira "registre para onde ele foi"; um destino sem contato vira "cadastre com quem
 * falar". É o que transforma o simulado em exercício útil em vez de nota de rodapé: o relatório não
 * diz só que a cobertura foi de 70%, diz o que fazer para ela ser maior da próxima vez.
 */
public final class DrillQueryHandler implements DrillQueries {

    private final DrillRepository drills;
    private final List<LineageSource> sources;
    private final List<DestinationSource> destinations;

    public DrillQueryHandler(DrillRepository drills, List<LineageSource> sources,
            List<DestinationSource> destinations) {
        this.drills = Objects.requireNonNull(drills);
        this.sources = List.copyOf(Objects.requireNonNull(sources));
        this.destinations = List.copyOf(Objects.requireNonNull(destinations));
    }

    @Override
    public List<RecallDrill> list(UUID breweryId) {
        return drills.findAll(breweryId);
    }

    @Override
    public Report report(UUID breweryId, UUID drillId, int depth) {
        var drill = drills.findById(breweryId, drillId)
                .orElseThrow(() -> new UnknownDrillException(drillId));
        var measurement = DrillHandlers.DrillMeasurement.of(breweryId, drill.origin(), depth, sources,
                destinations);

        var found = measurement.destinations().stream()
                .map(destination -> new Destination(destination.reference(), destination.label(),
                        destination.contact(), destination.units()))
                .toList();
        var elapsed = drill.elapsed(Instant.now()).toSeconds();

        // Encerrado responde com o que foi medido; correndo, com o que o grafo diz agora.
        var unitsInScope = drill.running() ? measurement.unitsInScope() : drill.unitsInScope();
        var reached = drill.running() ? found.size() : drill.destinationsReached();

        return new Report(drill, unitsInScope, reached, found, measurement.gaps(),
                findings(measurement, found), elapsed);
    }

    private static List<String> findings(DrillHandlers.DrillMeasurement measurement,
            List<Destination> destinations) {
        var findings = new ArrayList<String>();
        if (!measurement.gaps().isEmpty()) {
            findings.add(measurement.gaps().size()
                    + " lote(s) de produto acabado sem expedição registrada: registre para onde eles "
                    + "foram, ou o próximo recall não vai alcançá-los.");
        }
        var semContato = destinations.stream().filter(destination -> destination.contact() == null).count();
        if (semContato > 0) {
            findings.add(semContato + " destino(s) sem contato cadastrado: o recall sabe para onde o "
                    + "produto foi e não sabe com quem falar.");
        }
        if (destinations.isEmpty() && measurement.gaps().isEmpty()) {
            findings.add("Nada saiu deste lote: o simulado não tem o que localizar. Escolha um lote "
                    + "com expedição para exercitar a cadeia inteira.");
        }
        return List.copyOf(findings);
    }
}
