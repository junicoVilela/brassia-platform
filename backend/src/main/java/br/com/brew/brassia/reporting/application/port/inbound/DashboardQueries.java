package br.com.brew.brassia.reporting.application.port.inbound;

import br.com.brew.brassia.shared.reporting.OperationalIndicator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Leitura do painel operacional (RPT-002). */
public interface DashboardQueries {

    Dashboard dashboard(UUID breweryId, Instant from, Instant to);

    /**
     * @param indicators todos os indicadores das fontes registradas, na ordem dos grupos
     * @param sources    quantos módulos contribuíram com indicadores. Vai no contrato porque um
     *                   painel com dois blocos a menos parece um painel normal, e o número de
     *                   fontes é o que permite notar que o painel encolheu
     */
    record Dashboard(Instant from, Instant to, int sources, List<OperationalIndicator> indicators) {

        public Dashboard {
            indicators = List.copyOf(indicators);
        }
    }
}
