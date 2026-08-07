package br.com.brew.brassia.shared.reporting;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Fonte de indicadores do painel (RPT-002), implementada por cada módulo que tem número a dar.
 *
 * <p><strong>A porta mora no compartilhado, e não no relatório</strong>, e é a única forma de a
 * federação funcionar nos dois sentidos: o relatório já depende de produção, envase e custo, então
 * esses módulos não podem depender dele de volta. Com a forma aqui, ninguém depende de ninguém —
 * cada módulo implementa, o painel coleta, e o {@code ModularityTest} não tem ciclo a apontar.
 *
 * <p>É a mesma federação do {@code LineageSource} e do {@code UtilityReadingSource}: o painel não
 * sabe quais módulos existem. Um módulo novo passa a aparecer no painel implementando esta porta, e
 * um módulo que some deixa de contribuir sem quebrar a tela.
 */
public interface IndicatorSource {

    /**
     * Os indicadores deste módulo para o período.
     *
     * <p>Indicador de posição ignora o intervalo e responde pela foto de {@code to} — estoque
     * vencendo é sobre agora, não sobre o mês passado.
     */
    List<OperationalIndicator> indicatorsIn(UUID breweryId, Instant from, Instant to);
}
