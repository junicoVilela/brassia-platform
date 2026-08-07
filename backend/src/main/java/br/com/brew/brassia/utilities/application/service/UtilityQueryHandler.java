package br.com.brew.brassia.utilities.application.service;

import br.com.brew.brassia.utilities.PackagedVolumeSource;
import br.com.brew.brassia.utilities.UtilityReadingSource;
import br.com.brew.brassia.utilities.UtilityReadingSource.Coverage;
import br.com.brew.brassia.utilities.UtilityReadingSource.Reading;
import br.com.brew.brassia.utilities.UtilityReadingSource.UtilityType;
import br.com.brew.brassia.utilities.application.port.inbound.UtilityQueries;
import br.com.brew.brassia.utilities.domain.UtilityIndicator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Junta as medições das fontes e divide pelo envasado (UTL-001).
 *
 * <p>Não sabe quais módulos medem: recebe a lista de fontes e pergunta a todas. Um medidor novo —
 * de água na brassagem, de energia na câmara fria — entra implementando a porta, e o indicador
 * passa a enxergá-lo sem que uma linha daqui mude.
 *
 * <p>Só aparece no relatório a utilidade que alguém mediu. Listar as quatro com zero faria a
 * cervejaria que nunca mediu energia parecer uma cervejaria que não gasta energia.
 */
public final class UtilityQueryHandler implements UtilityQueries {

    private final List<UtilityReadingSource> sources;
    private final PackagedVolumeSource packaged;

    public UtilityQueryHandler(List<UtilityReadingSource> sources, PackagedVolumeSource packaged) {
        this.sources = List.copyOf(Objects.requireNonNull(sources));
        this.packaged = Objects.requireNonNull(packaged);
    }

    @Override
    public Report report(UUID breweryId, Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("o início do período é depois do fim");
        }
        var readings = new EnumMap<UtilityType, List<Reading>>(UtilityType.class);
        var coverage = new EnumMap<UtilityType, List<Coverage>>(UtilityType.class);
        for (UtilityReadingSource source : sources) {
            var measuredTypes = new ArrayList<UtilityType>();
            for (Reading reading : source.readingsIn(breweryId, from, to)) {
                readings.computeIfAbsent(reading.type(), type -> new ArrayList<>()).add(reading);
                if (!measuredTypes.contains(reading.type())) {
                    measuredTypes.add(reading.type());
                }
            }
            // A cobertura é de quem a declarou, e vale só para o que aquela fonte mede: a cobertura
            // dos ciclos de limpeza não diz nada sobre o CO₂, que é medido na conexão de gás.
            var declared = source.coverageIn(breweryId, from, to);
            if (!declared.isEmpty()) {
                for (UtilityType type : measuredTypes) {
                    coverage.computeIfAbsent(type, ignored -> new ArrayList<>()).addAll(declared);
                }
            }
        }

        var liters = packaged.packagedLitersIn(breweryId, from, to);
        var indicators = new ArrayList<UtilityIndicator>();
        for (var entry : readings.entrySet()) {
            indicators.add(UtilityIndicator.of(entry.getKey(), entry.getValue(), liters,
                    List.copyOf(coverage.getOrDefault(entry.getKey(), List.of()))));
        }
        return new Report(from, to, liters == null ? BigDecimal.ZERO : liters, List.copyOf(indicators));
    }
}
