package br.com.brew.brassia.reporting.application.service;

import br.com.brew.brassia.reporting.application.port.inbound.DashboardQueries;
import br.com.brew.brassia.shared.reporting.IndicatorSource;
import br.com.brew.brassia.shared.reporting.OperationalIndicator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Coleta os indicadores das fontes registradas (RPT-002).
 *
 * <p>Não sabe quais módulos existem, e é o ponto: um módulo novo aparece no painel implementando a
 * porta, sem que uma linha daqui mude. O painel também não escreve definição nenhuma — quem define
 * o que é "desvio em aberto" é a qualidade, e um painel que escrevesse isso estaria legislando
 * sobre domínio alheio.
 *
 * <p><strong>Fonte que falha derruba o painel, de propósito.</strong> A tentação é engolir a
 * exceção e mostrar o resto; o resultado seria um painel com dois blocos a menos, indistinguível de
 * um painel normal, e alguém tomaria decisão sobre uma fábrica que ele acha que está vendo inteira.
 * Erro visível é pior de olhar e melhor de confiar.
 */
public final class DashboardQueryHandler implements DashboardQueries {

    private static final Comparator<OperationalIndicator> BY_GROUP_THEN_CODE =
            Comparator.comparing((OperationalIndicator indicator) -> indicator.group().ordinal())
                    .thenComparing(OperationalIndicator::code);

    private final List<IndicatorSource> sources;

    public DashboardQueryHandler(List<IndicatorSource> sources) {
        this.sources = List.copyOf(Objects.requireNonNull(sources));
    }

    @Override
    public Dashboard dashboard(UUID breweryId, Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("o início do período é depois do fim");
        }
        var indicators = new ArrayList<OperationalIndicator>();
        for (IndicatorSource source : sources) {
            indicators.addAll(source.indicatorsIn(breweryId, from, to));
        }
        indicators.sort(BY_GROUP_THEN_CODE);
        return new Dashboard(from, to, sources.size(), List.copyOf(indicators));
    }
}
